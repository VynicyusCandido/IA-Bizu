package app.bizu.conversa;

import app.bizu.conversa.Agente.Contexto;
import app.bizu.conversa.Agente.RespostaAgente;
import app.bizu.conversa.MensagemRepository.Direcao;
import app.bizu.conversa.MensagemRepository.Origem;
import app.bizu.corretor.CorretorService;
import app.bizu.corretor.MotivoDaPausa;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Consome a fila: uma conversa por vez, com a linha da conversa travada até o envio ser gravado. */
@Service
public class ProcessadorDeFila {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeFila.class);
    private static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(30);
    /** Mais que o pior caso da IA (4 rodadas de 30 s). Pressupõe um processo só consumindo a fila. */
    private static final Duration RESERVA = Duration.ofMinutes(3);

    /** Falha ao responder uma conversa; carrega o ID para o worker adiar só ela. */
    public static class FalhaNoProcessamento extends RuntimeException {
        private final UUID conversaId;

        FalhaNoProcessamento(UUID conversaId, Throwable causa) {
            super("Falha ao responder a conversa " + conversaId, causa);
            this.conversaId = conversaId;
        }

        public UUID conversaId() {
            return conversaId;
        }
    }

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final FilaRepository fila;
    private final LeadRepository leads;
    private final PromptRepository prompts;
    private final Agente agente;
    private final Canal canal;
    private final ConversaProperties props;
    private final TransactionTemplate transacao;
    private final CorretorService corretores;

    ProcessadorDeFila(ConversaRepository conversas, MensagemRepository mensagens, FilaRepository fila,
                      LeadRepository leads, PromptRepository prompts, Agente agente, Canal canal,
                      ConversaProperties props, TransactionTemplate transacao, CorretorService corretores) {
        this.transacao = transacao;
        this.corretores = corretores;
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.fila = fila;
        this.leads = leads;
        this.prompts = prompts;
        this.agente = agente;
        this.canal = canal;
        this.props = props;
    }

    /** O que o passo 1 separou para a IA; {@code contexto} é nulo quando não há nada a responder. */
    private record Pedido(UUID conversaId, String corretorId, List<Long> filaIds, Contexto contexto) {
    }

    /**
     * Processa uma conversa pronta. Devolve false quando não há nada na fila. Três passos, para a conversa não
     * ficar travada no banco enquanto a IA pensa (pode levar minutos se a API estiver lenta):
     * 1) reserva a fila e lê o contexto; 2) chama a IA sem transação; 3) trava a conversa de novo, confere que
     * o corretor não assumiu nesse meio-tempo, envia e grava.
     */
    public boolean processarProxima(Instant agora) {
        Optional<Pedido> reservado = transacao.execute(s -> reservar(agora));
        if (reservado.isEmpty()) {
            return false;
        }
        Pedido pedido = reservado.get();
        try {
            if (pedido.contexto() != null) {
                RespostaAgente resposta = agente.responder(pedido.contexto());
                transacao.executeWithoutResult(s -> concluir(pedido, resposta, agora));
            }
        } catch (RuntimeException e) {
            throw new FalhaNoProcessamento(pedido.conversaId(), e);
        }
        return true;
    }

    private Optional<Pedido> reservar(Instant agora) {
        Optional<UUID> pronta = conversas.reivindicarPronta(agora);
        if (pronta.isEmpty()) {
            return Optional.empty();
        }
        UUID conversaId = pronta.get();
        Conversa conversa = conversas.buscar(conversaId).orElseThrow();
        List<Long> filaIds = fila.idsDaConversa(conversaId);

        // Corretor assumiu ou limite atingido entre o enfileiramento e agora: nada a responder.
        if (!conversa.podeResponder(props.limiteRespostas())) {
            fila.remover(filaIds);
            return Optional.of(new Pedido(conversaId, conversa.corretorId(), filaIds, null));
        }

        // Conta desligada, assinatura vencida além da carência ou teto do mês atingido: a IA fica parada. A conversa
        // não muda de estado, as mensagens do cliente continuam gravadas e a IA volta a responder quando o cliente
        // escrever de novo depois de resolvido. Com vários trabalhadores o teto pode passar por poucas respostas
        // (as que já estavam em andamento).
        Optional<MotivoDaPausa> pausa = corretores.motivoDaPausa(conversa.corretorId(), agora);
        if (pausa.isPresent()) {
            log.info("IA pausada para o corretor {} ({}): a conversa {} fica sem resposta automática",
                    conversa.corretorId(), pausa.get(), conversaId);
            fila.remover(filaIds);
            return Optional.of(new Pedido(conversaId, conversa.corretorId(), filaIds, null));
        }

        // Reserva: some da fila de "prontas" enquanto a IA responde. Se o processo cair, volta sozinha.
        fila.adiar(conversaId, agora.plus(RESERVA));
        Contexto contexto = new Contexto(conversa, mensagens.historico(conversaId),
                prompts.conteudo(conversa.corretorId(), "BASE"), prompts.conteudo(conversa.corretorId(), "ESTILO"));
        return Optional.of(new Pedido(conversaId, conversa.corretorId(), filaIds, contexto));
    }

    private void concluir(Pedido pedido, RespostaAgente resposta, Instant agora) {
        Conversa conversa = conversas.buscarParaAtualizar(pedido.corretorId(), pedido.conversaId()).orElseThrow();

        if (resposta.qualificacao() != null && !resposta.qualificacao().vazia()) {
            leads.salvar(pedido.conversaId(), resposta.qualificacao(), agora);
        }
        fila.remover(pedido.filaIds());

        // O corretor pode ter assumido (ou o limite batido) enquanto a IA pensava: a resposta é descartada.
        if (!conversa.podeResponder(props.limiteRespostas())) {
            return;
        }
        if (resposta.passarAoCorretor()) {
            conversas.atualizar(conversa.assumida(agora));
            return;
        }
        if (resposta.texto() == null || resposta.texto().isBlank()) {
            return;
        }

        String waId = canal.enviar(conversa.corretorId(), conversa.telefone(), resposta.texto());
        mensagens.inserir(UUID.randomUUID(), conversa.id(), Direcao.SAIDA, Origem.IA, resposta.texto(), waId, agora);
        conversas.atualizar(conversa.aposRespostaIa(props.limiteRespostas(), agora));
        corretores.contarRespostaDaIa(conversa.corretorId(), agora);
    }

    /** Adia a conversa que falhou; depois do máximo de tentativas a fila dela é descartada. */
    @Transactional
    public void registrarFalha(UUID conversaId, Instant agora) {
        int descartadas = fila.registrarFalha(conversaId, agora.plus(ESPERA_APOS_FALHA), props.maximoTentativas());
        if (descartadas > 0) {
            log.error("Fila da conversa {} descartada depois de {} falhas: {} mensagem(ns) do cliente ficaram sem resposta",
                    conversaId, props.maximoTentativas(), descartadas);
        }
    }
}
