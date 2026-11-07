package app.bizu.conversa;

import app.bizu.conversa.ContatoIgnoradoRepository.Motivo;
import app.bizu.conversa.MensagemRepository.Direcao;
import app.bizu.conversa.MensagemRepository.Origem;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrada de mensagens no domínio: decide quem a IA atende e quando o corretor assume. */
@Service
public class ConversaService {

    /** Eco: mensagem que o próprio sistema enviou e voltou do WhatsApp como "do corretor". */
    public enum Resultado { ENFILEIRADA, REGISTRADA, IGNORADA, DUPLICADA, ECO, ASSUMIDA }

    private static final Duration JANELA_ECO = Duration.ofSeconds(60);

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

    /** Mensagem de texto recebida de um cliente. */
    @Transactional
    public Resultado receberDoCliente(String telefone, String texto, String waMessageId, Instant agora) {
        if (contatos.existe(telefone)) {
            return Resultado.IGNORADA;
        }

        Optional<Conversa> atual = conversas.buscarAtivaParaAtualizar(telefone);
        Conversa conversa;
        if (atual.isPresent() && !atual.get().expirada(agora, props.reinicioApos())) {
            conversa = atual.get();
        } else {
            atual.ifPresent(velha -> conversas.atualizar(velha.desativada()));
            conversa = Conversa.nova(telefone, agora);
            conversas.inserir(conversa);
        }

        UUID mensagemId = UUID.randomUUID();
        if (!mensagens.inserir(mensagemId, conversa.id(), Direcao.ENTRADA, Origem.CLIENTE, texto, waMessageId, agora)) {
            return Resultado.DUPLICADA;
        }
        conversas.atualizar(conversa.comAtividade(agora));

        if (conversa.estado() == Estado.BOT) {
            fila.enfileirar(conversa.id(), mensagemId, agora.plus(props.janelaAgrupamento()));
            return Resultado.ENFILEIRADA;
        }
        return Resultado.REGISTRADA;
    }

    /**
     * Mensagem enviada do celular do corretor. Se foi o eco de uma resposta da IA, é descartada; senão o
     * corretor assumiu: a conversa vai a HUMANO e o número nunca mais é atendido pela IA.
     */
    @Transactional
    public Resultado receberDoCorretor(String telefone, String texto, String waMessageId, Instant agora) {
        // Trava a conversa: se a IA está enviando agora, espera o commit para o ID da resposta já estar gravado.
        Optional<Conversa> ativa = conversas.buscarAtivaParaAtualizar(telefone);

        if (waMessageId != null && mensagens.existePorWaId(waMessageId)) {
            return Resultado.ECO;
        }
        if (ativa.isPresent()
                && mensagens.existeSaidaIaRecente(ativa.get().id(), texto, agora.minus(JANELA_ECO))) {
            return Resultado.ECO;
        }

        contatos.marcar(telefone, Motivo.ESCRITA_MANUAL, agora);
        if (ativa.isPresent()) {
            Conversa conversa = ativa.get();
            mensagens.inserir(UUID.randomUUID(), conversa.id(), Direcao.SAIDA, Origem.CORRETOR, texto,
                    waMessageId, agora);
            conversas.atualizar(conversa.assumida(agora));
        }
        return Resultado.ASSUMIDA;
    }

    /** Marca um número como contato salvo do corretor (a IA nunca o atende). */
    @Transactional
    public void marcarContatoSalvo(String telefone, Instant agora) {
        contatos.marcar(telefone, Motivo.AGENDA, agora);
    }
}
