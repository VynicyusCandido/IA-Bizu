package app.bizu.conversa;

import app.bizu.conversa.Agente.Contexto;
import app.bizu.conversa.Agente.RespostaAgente;
import app.bizu.conversa.MensagemRepository.Direcao;
import app.bizu.conversa.MensagemRepository.Origem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consome a fila: uma conversa por vez, com a linha da conversa travada até o envio ser gravado. */
@Service
public class ProcessadorDeFila {

    private static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(30);

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

    ProcessadorDeFila(ConversaRepository conversas, MensagemRepository mensagens, FilaRepository fila,
                      LeadRepository leads, PromptRepository prompts, Agente agente, Canal canal,
                      ConversaProperties props) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.fila = fila;
        this.leads = leads;
        this.prompts = prompts;
        this.agente = agente;
        this.canal = canal;
        this.props = props;
    }

    /** Processa uma conversa pronta. Devolve false quando não há nada na fila. */
    @Transactional
    public boolean processarProxima(Instant agora) {
        Optional<UUID> pronta = conversas.reivindicarPronta(agora);
        if (pronta.isEmpty()) {
            return false;
        }
        UUID conversaId = pronta.get();
        try {
            responder(conversaId, agora);
        } catch (RuntimeException e) {
            throw new FalhaNoProcessamento(conversaId, e);
        }
        return true;
    }

    private void responder(UUID conversaId, Instant agora) {
        Conversa conversa = conversas.buscar(conversaId).orElseThrow();
        List<Long> filaIds = fila.idsDaConversa(conversaId);

        // Corretor assumiu ou limite atingido entre o enfileiramento e agora: nada a responder.
        if (!conversa.podeResponder(props.limiteRespostas())) {
            fila.remover(filaIds);
            return;
        }

        Contexto contexto = new Contexto(conversa, mensagens.historico(conversaId),
                prompts.conteudo("BASE"), prompts.conteudo("ESTILO"));
        RespostaAgente resposta = agente.responder(contexto);

        if (resposta.qualificacao() != null && !resposta.qualificacao().vazia()) {
            leads.salvar(conversaId, resposta.qualificacao(), agora);
        }
        fila.remover(filaIds);

        if (resposta.passarAoCorretor()) {
            conversas.atualizar(conversa.assumida(agora));
            return;
        }
        if (resposta.texto() == null || resposta.texto().isBlank()) {
            return;
        }

        String waId = canal.enviar(conversa.telefone(), resposta.texto());
        mensagens.inserir(UUID.randomUUID(), conversaId, Direcao.SAIDA, Origem.IA, resposta.texto(), waId, agora);
        conversas.atualizar(conversa.aposRespostaIa(props.limiteRespostas(), agora));
    }

    /** Adia a conversa que falhou; depois do máximo de tentativas a fila dela é descartada. */
    @Transactional
    public void registrarFalha(UUID conversaId, Instant agora) {
        fila.registrarFalha(conversaId, agora.plus(ESPERA_APOS_FALHA), props.maximoTentativas());
    }
}
