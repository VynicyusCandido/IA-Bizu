package app.bizu.conversa;

import app.bizu.conversa.ContatoIgnoradoRepository.Motivo;
import app.bizu.conversa.HistoricoDeConversas.ConversaImportada;
import app.bizu.conversa.HistoricoDeConversas.MensagemImportada;
import app.bizu.conversa.MensagemRepository.Direcao;
import app.bizu.conversa.MensagemRepository.Origem;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrada de mensagens no domínio: decide quem a IA atende e quando o corretor assume. */
@Service
public class ConversaService {

    /** Eco: mensagem que o próprio sistema enviou e voltou do WhatsApp como "do corretor". */
    public enum Resultado { ENFILEIRADA, REGISTRADA, DUPLICADA, ECO, ASSUMIDA }

    /**
     * Linha do painel: a conversa, o que a IA descobriu do cliente e a prévia da última mensagem
     * (até 120 caracteres; {@code ultimaOrigem} é CLIENTE, IA ou CORRETOR). {@code nome} é o nome do contato, se
     * o WhatsApp informou; {@code fotoUrl}, a foto dele (https), se houver.
     */
    public record ConversaResumo(UUID id, String telefone, Estado estado, int respostasIa, Instant ultimaAtividadeEm,
                                 String finalidade, String regiao, String faixaValor, String prazo,
                                 String imovelCitado, String ultimaMensagem, String ultimaOrigem, String nome,
                                 String fotoUrl) {
    }

    /** Uma mensagem do histórico mostrado no painel; {@code origem} é CLIENTE, IA ou CORRETOR. */
    public record MensagemDaConversa(String origem, String texto, Instant criadaEm) {
    }

    /** Contato da agenda do corretor; {@code telefone} segue a mesma regra do identificador da conversa. */
    public record ContatoSalvo(String telefone, String nome) {
    }

    public record ResumoDaImportacao(int conversas, int mensagens) {
    }

    private static final Duration JANELA_ECO = Duration.ofSeconds(60);
    private static final String SUFIXO_LID = "@lid";

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final FilaRepository fila;
    private final ContatoIgnoradoRepository contatos;
    private final ConversaProperties props;

    ConversaService(ConversaRepository conversas, MensagemRepository mensagens, FilaRepository fila,
                    ContatoIgnoradoRepository contatos, ConversaProperties props) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.fila = fila;
        this.contatos = contatos;
        this.props = props;
    }

    public Resultado receberDoCliente(String corretorId, String telefone, String texto, String waMessageId,
                                      Instant agora) {
        return receberDoCliente(corretorId, telefone, null, texto, waMessageId, agora);
    }

    /**
     * Mensagem de texto recebida de um cliente. Toda conversa individual é gravada e aparece no painel; só as de
     * número novo ficam com a IA (BOT). Contato salvo e contato sem telefone conhecido ficam SEM_IA.
     */
    @Transactional
    public Resultado receberDoCliente(String corretorId, String telefone, String nome, String texto,
                                      String waMessageId, Instant agora) {
        Optional<Conversa> atual = conversas.buscarAtivaParaAtualizar(corretorId, telefone);
        Conversa conversa;
        // HUMANO e SEM_IA nunca reiniciam sozinhas: a IA só volta a uma conversa se o corretor devolver.
        if (atual.isPresent() && (naoReinicia(atual.get()) || !atual.get().expirada(agora, props.reinicioApos()))) {
            conversa = atual.get();
        } else {
            atual.ifPresent(velha -> conversas.atualizar(velha.desativada()));
            conversa = Conversa.nova(corretorId, telefone, estadoInicial(corretorId, telefone), agora);
            conversas.inserir(conversa);
        }

        UUID mensagemId = UUID.randomUUID();
        if (!mensagens.inserir(mensagemId, conversa.id(), Direcao.ENTRADA, Origem.CLIENTE, texto, waMessageId, agora)) {
            return Resultado.DUPLICADA;
        }
        conversas.atualizar(conversa.comAtividade(agora));
        conversas.atualizarNome(conversa.id(), nome);

        if (conversa.estado() == Estado.BOT) {
            fila.enfileirar(conversa.id(), mensagemId, agora.plus(props.janelaAgrupamento()));
            return Resultado.ENFILEIRADA;
        }
        return Resultado.REGISTRADA;
    }

    /**
     * Mensagem enviada do celular do corretor. Se foi o eco de uma resposta da IA, é descartada; senão a conversa
     * é gravada (e criada, se ainda não existia). Com número novo o corretor assumiu: a conversa vai a HUMANO e o
     * número nunca mais é atendido pela IA. Contato salvo e contato sem telefone continuam SEM_IA.
     */
    @Transactional
    public Resultado receberDoCorretor(String corretorId, String telefone, String texto, String waMessageId,
                                       Instant agora) {
        // Trava a conversa: se a IA está enviando agora, espera o commit para o ID da resposta já estar gravado.
        Optional<Conversa> ativa = conversas.buscarAtivaParaAtualizar(corretorId, telefone);

        if (waMessageId != null && mensagens.existePorWaId(waMessageId)) {
            return Resultado.ECO;
        }
        if (ativa.isPresent()
                && mensagens.existeSaidaIaRecente(ativa.get().id(), texto, agora.minus(JANELA_ECO))) {
            return Resultado.ECO;
        }

        boolean semIa = telefone.endsWith(SUFIXO_LID) || contatos.motivo(corretorId, telefone).filter(m -> m == Motivo.AGENDA).isPresent();
        if (!semIa) {
            contatos.marcar(corretorId, telefone, Motivo.ESCRITA_MANUAL, null, agora);
        }
        Conversa conversa;
        if (ativa.isPresent()) {
            conversa = ativa.get();
        } else {
            conversa = Conversa.nova(corretorId, telefone, semIa ? Estado.SEM_IA : Estado.HUMANO, agora);
            conversas.inserir(conversa);
        }
        mensagens.inserir(UUID.randomUUID(), conversa.id(), Direcao.SAIDA, Origem.CORRETOR, texto, waMessageId, agora);
        conversas.atualizar(conversa.estado() == Estado.SEM_IA ? conversa.comAtividade(agora) : conversa.assumida(agora));
        return Resultado.ASSUMIDA;
    }

    @Transactional(readOnly = true)
    public List<ConversaResumo> listar(String corretorId) {
        return conversas.listarResumos(corretorId);
    }

    /** Histórico da conversa; vazio se ela não existe. */
    @Transactional(readOnly = true)
    public Optional<List<MensagemDaConversa>> mensagens(String corretorId, UUID id) {
        return conversas.buscar(corretorId, id).map(c -> mensagens.listar(id));
    }

    /** O corretor assume pelo painel: a IA para de responder. Vazio se a conversa não existe, não é dele, não está ativa ou nunca teve IA. */
    @Transactional
    public Optional<Conversa> assumir(String corretorId, UUID id, Instant agora) {
        return conversas.buscarParaAtualizar(corretorId, id).filter(Conversa::ativa)
                .filter(c -> c.estado() != Estado.SEM_IA).map(conversa -> {
                    Conversa assumida = conversa.assumida(agora);
                    conversas.atualizar(assumida);
                    return assumida;
                });
    }

    /** O corretor devolve à IA. Só age em conversa assumida; se o limite de respostas já foi atingido, encerra. */
    @Transactional
    public Optional<Conversa> devolver(String corretorId, UUID id, Instant agora) {
        return conversas.buscarParaAtualizar(corretorId, id).filter(Conversa::ativa)
                .filter(c -> c.estado() != Estado.SEM_IA).map(conversa -> {
                    if (conversa.estado() != Estado.HUMANO) {
                        return conversa;
                    }
                    Conversa devolvida = conversa.devolvida(props.limiteRespostas(), agora);
                    conversas.atualizar(devolvida);
                    return devolvida;
                });
    }

    /**
     * Marca contatos como salvos na agenda do corretor (a IA nunca os atende), todos numa só transação. Conversa
     * deles que estava com a IA, por a mensagem ter chegado antes da agenda, passa a SEM_IA.
     */
    @Transactional
    public void marcarContatosSalvos(String corretorId, List<ContatoSalvo> salvos, Instant agora) {
        salvos.forEach(c -> contatos.marcar(corretorId, c.telefone(), Motivo.AGENDA, c.nome(), agora));
        conversas.pararIa(corretorId, salvos.stream().map(ContatoSalvo::telefone).toList());
    }

    /**
     * Grava conversas que já existiam no WhatsApp, com a data original de cada mensagem. Não mexe na fila: a IA
     * nunca responde retroativamente. Quem o corretor já atendeu vira HUMANO, contato salvo ou sem telefone vira
     * SEM_IA, e o resto fica com a IA para a próxima mensagem. Pode rodar de novo: o id do WhatsApp evita repetir.
     */
    @Transactional
    public ResumoDaImportacao importar(String corretorId, List<ConversaImportada> importadas, Instant agora) {
        int totalConversas = 0;
        int totalMensagens = 0;
        for (ConversaImportada chat : importadas) {
            List<MensagemImportada> ordenadas = chat.mensagens().stream()
                    .sorted(Comparator.comparing(MensagemImportada::em)).toList();
            if (ordenadas.isEmpty()) {
                continue;
            }
            String id = chat.identificador();
            boolean semTelefone = id.endsWith(SUFIXO_LID);
            boolean corretorFalou = ordenadas.stream().anyMatch(MensagemImportada::doCorretor);
            Instant ultima = ordenadas.get(ordenadas.size() - 1).em();

            if (!semTelefone && chat.salvo()) {
                contatos.marcar(corretorId, id, Motivo.AGENDA, chat.nome(), agora);
            } else if (!semTelefone && corretorFalou) {
                contatos.marcar(corretorId, id, Motivo.ESCRITA_MANUAL, null, agora);
            }
            Optional<Motivo> motivo = contatos.motivo(corretorId, id);
            Estado estado = semTelefone || chat.salvo() || motivo.filter(m -> m == Motivo.AGENDA).isPresent()
                    ? Estado.SEM_IA
                    : (corretorFalou || motivo.isPresent() ? Estado.HUMANO : Estado.BOT);

            Optional<Conversa> existente = conversas.buscarAtivaParaAtualizar(corretorId, id);
            Conversa conversa;
            if (existente.isPresent()) {
                conversa = existente.get();
                if (ultima.isAfter(conversa.ultimaAtividadeEm())) {
                    conversas.atualizar(conversa.comAtividade(ultima));
                }
            } else {
                conversa = Conversa.doHistorico(corretorId, id, estado, ordenadas.get(0).em(), ultima);
                conversas.inserir(conversa);
            }
            if (estado == Estado.SEM_IA) {
                conversas.pararIa(corretorId, List.of(id));
            }
            for (MensagemImportada m : ordenadas) {
                boolean nova = mensagens.inserir(UUID.randomUUID(), conversa.id(),
                        m.doCorretor() ? Direcao.SAIDA : Direcao.ENTRADA,
                        m.doCorretor() ? Origem.CORRETOR : Origem.CLIENTE, m.texto(), m.id(), m.em());
                if (nova) {
                    totalMensagens++;
                }
            }
            conversas.atualizarNome(conversa.id(), chat.nome());
            conversas.atualizarFoto(conversa.id(), chat.fotoUrl());
            totalConversas++;
        }
        return new ResumoDaImportacao(totalConversas, totalMensagens);
    }

    private static boolean naoReinicia(Conversa conversa) {
        return conversa.estado() == Estado.HUMANO || conversa.estado() == Estado.SEM_IA;
    }

    /** Estado de uma conversa que nasce agora com mensagem de cliente. */
    private Estado estadoInicial(String corretorId, String telefone) {
        Optional<Motivo> motivo = contatos.motivo(corretorId, telefone);
        if (motivo.isPresent()) {
            return motivo.get() == Motivo.AGENDA ? Estado.SEM_IA : Estado.HUMANO;
        }
        return telefone.endsWith(SUFIXO_LID) ? Estado.SEM_IA : Estado.BOT;
    }
}
