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
     * o WhatsApp informou; {@code fotoUrl}, a foto dele (https), se houver; {@code naoLidas}, quantas mensagens do cliente o corretor ainda não leu no painel.
     */
    public record ConversaResumo(UUID id, String telefone, Estado estado, int respostasIa, Instant ultimaAtividadeEm,
                                 String finalidade, String regiao, String faixaValor, String prazo,
                                 String imovelCitado, String ultimaMensagem, String ultimaOrigem, String nome,
                                 String fotoUrl, int naoLidas) {
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
    private final Canal canal;
    private final AgendaDoCorretor agenda;
    private final FotoDoContato fotos;

    ConversaService(ConversaRepository conversas, MensagemRepository mensagens, FilaRepository fila,
                    ContatoIgnoradoRepository contatos, ConversaProperties props, Canal canal,
                    AgendaDoCorretor agenda, FotoDoContato fotos) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.fila = fila;
        this.contatos = contatos;
        this.props = props;
        this.canal = canal;
        this.agenda = agenda;
        this.fotos = fotos;
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
            conversa = Conversa.nova(corretorId, telefone, estadoInicial(corretorId, telefone, agora), agora);
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

        boolean semIa = salvoNaAgenda(corretorId, telefone, agora);
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

    /**
     * Busca a foto de uma conversa que acabou de nascer (só tem a primeira mensagem) e ainda não tem foto. Chamada
     * depois do commit e fora de qualquer transação, porque o WhatsApp pode demorar; nunca lança exceção, pois a
     * foto é só enfeite. Conversas importadas ganham a foto na importação.
     */
    public void completarFoto(String corretorId, String telefone) {
        try {
            conversas.semFotoRecemCriada(corretorId, telefone).ifPresent(id ->
                    fotos.de(corretorId, telefone).ifPresent(url -> conversas.atualizarFoto(id, url)));
        } catch (RuntimeException e) {
            // sem foto por enquanto: a próxima importação tenta de novo
        }
    }

    @Transactional(readOnly = true)
    public List<ConversaResumo> listar(String corretorId) {
        return conversas.listarResumos(corretorId);
    }

    /**
     * O corretor viu a conversa até {@code ate} (a data da última mensagem que o painel mostrou). Nunca volta atrás e
     * nunca passa de agora. Falso se a conversa não existe ou não é dele.
     */
    @Transactional
    public boolean marcarLida(String corretorId, UUID id, Instant ate, Instant agora) {
        return conversas.marcarLida(corretorId, id, ate.isAfter(agora) ? agora : ate);
    }

    /** Histórico da conversa; vazio se ela não existe. */
    @Transactional(readOnly = true)
    public Optional<List<MensagemDaConversa>> mensagens(String corretorId, UUID id) {
        return conversas.buscar(corretorId, id).map(c -> mensagens.listar(id));
    }

    /**
     * O corretor responde pelo painel: envia pelo WhatsApp dele e grava. Com a IA na conversa, ele assume (a IA para);
     * contato salvo ou sem telefone continua SEM_IA. Vazio se a conversa não existe, não é dele ou não está ativa.
     * Falha do WhatsApp lança {@link EnvioRecusadoException} e não grava nada. shortcut: o envio acontece dentro da
     * transação, com a conversa travada, para o eco do webhook esperar o ID gravado; custa uma conexão do banco
     * até o timeout da Evolution (30 s) no pior caso.
     */
    @Transactional
    public Optional<Conversa> responder(String corretorId, UUID id, String texto, Instant agora) {
        return enviarDoPainel(corretorId, id, texto, c -> canal.enviar(corretorId, c.telefone(), texto), agora);
    }

    /**
     * Igual a {@link #responder}, mas com um anexo (imagem, vídeo ou documento) e legenda opcional. No histórico a
     * mensagem aparece como marcador, no mesmo formato da importação ("[imagem] legenda", "[documento] nome.pdf").
     */
    @Transactional
    public Optional<Conversa> responderComAnexo(String corretorId, UUID id, Canal.Midia midia, Instant agora) {
        String legenda = midia.legenda() == null ? "" : midia.legenda().strip();
        String marcador = switch (midia.tipo()) {
            case "image" -> "[imagem]";
            case "video" -> "[vídeo]";
            default -> "[documento]";
        };
        String detalhe = !legenda.isEmpty() ? legenda : "document".equals(midia.tipo()) ? midia.nome() : "";
        String texto = detalhe.isEmpty() ? marcador : marcador + " " + detalhe;
        return enviarDoPainel(corretorId, id, texto, c -> canal.enviarMidia(corretorId, c.telefone(), midia), agora);
    }

    private Optional<Conversa> enviarDoPainel(String corretorId, UUID id, String texto,
                                              java.util.function.Function<Conversa, String> envio, Instant agora) {
        return conversas.buscarParaAtualizar(corretorId, id).filter(Conversa::ativa).map(conversa -> {
            String waId;
            try {
                waId = envio.apply(conversa);
            } catch (RuntimeException e) {
                throw new EnvioRecusadoException(e);
            }
            mensagens.inserir(UUID.randomUUID(), conversa.id(), Direcao.SAIDA, Origem.CORRETOR, texto, waId, agora);
            boolean semIa = conversa.estado() == Estado.SEM_IA;
            if (!semIa) {
                contatos.marcar(corretorId, conversa.telefone(), Motivo.ESCRITA_MANUAL, null, agora);
            }
            Conversa atualizada = semIa ? conversa.comAtividade(agora) : conversa.assumida(agora);
            conversas.atualizar(atualizada);
            return atualizada;
        });
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

    /**
     * O corretor liga a IA na conversa: age em conversa assumida ou sem IA (a escolha dele vale até sobre contato
     * salvo); se o limite de respostas já foi atingido, encerra.
     */
    @Transactional
    public Optional<Conversa> devolver(String corretorId, UUID id, Instant agora) {
        return conversas.buscarParaAtualizar(corretorId, id).filter(Conversa::ativa)
                .map(conversa -> {
                    if (conversa.estado() != Estado.HUMANO && conversa.estado() != Estado.SEM_IA) {
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
            // Resposta que a própria IA enviou também volta do WhatsApp como "minha": só vale o que o corretor escreveu.
            boolean corretorFalou = ordenadas.stream()
                    .anyMatch(m -> m.doCorretor() && !mensagens.existeSaidaIaPorWaId(m.id()));
            Instant ultima = ordenadas.get(ordenadas.size() - 1).em();

            if (chat.salvo()) {
                contatos.marcar(corretorId, id, Motivo.AGENDA, chat.nome(), agora);
            } else if (corretorFalou) {
                contatos.marcar(corretorId, id, Motivo.ESCRITA_MANUAL, null, agora);
            }
            Optional<Motivo> motivo = contatos.motivo(corretorId, id);
            Estado estado = chat.salvo() || motivo.filter(m -> m == Motivo.AGENDA).isPresent()
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
            if (existente.isEmpty()) {
                // Histórico importado já é passado: não nasce como "não lido".
                conversas.marcarLida(corretorId, conversa.id(), ultima);
            }
            totalConversas++;
        }
        return new ResumoDaImportacao(totalConversas, totalMensagens);
    }

    private static boolean naoReinicia(Conversa conversa) {
        return conversa.estado() == Estado.HUMANO || conversa.estado() == Estado.SEM_IA;
    }

    /** Estado de uma conversa que nasce agora com mensagem de cliente. */
    private Estado estadoInicial(String corretorId, String telefone, Instant agora) {
        Optional<Motivo> motivo = contatos.motivo(corretorId, telefone);
        if (motivo.isPresent()) {
            return motivo.get() == Motivo.AGENDA ? Estado.SEM_IA : Estado.HUMANO;
        }
        return salvoNaAgenda(corretorId, telefone, agora) ? Estado.SEM_IA : Estado.BOT;
    }

    /**
     * Contato salvo na agenda (a IA nunca atende). Contato só com código (@lid) não chega pelo evento de contatos a
     * tempo, então pergunta ao WhatsApp e guarda a resposta. Sem conseguir perguntar, trata como salvo: errar para
     * este lado só deixa de responder, nunca responde a quem não devia.
     */
    private boolean salvoNaAgenda(String corretorId, String telefone, Instant agora) {
        if (contatos.motivo(corretorId, telefone).filter(m -> m == Motivo.AGENDA).isPresent()) {
            return true;
        }
        if (!telefone.endsWith(SUFIXO_LID)) {
            return false;
        }
        try {
            if (!agenda.salvo(corretorId, telefone)) {
                return false;
            }
        } catch (RuntimeException e) {
            return true;
        }
        contatos.marcar(corretorId, telefone, Motivo.AGENDA, null, agora);
        return true;
    }
}
