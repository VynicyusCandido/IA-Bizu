package app.bizu.conversa;

import java.time.Instant;
import java.util.List;

/** Porta para as conversas que já existem no WhatsApp do corretor (o que a Evolution tiver guardado). */
public interface HistoricoDeConversas {

    /**
     * Conversas individuais com mensagens desde o instante dado. {@code identificador} é o telefone (só dígitos) ou,
     * quando o WhatsApp só informa um código do contato, esse código com "@lid".
     */
    List<ConversaImportada> desde(String corretorId, Instant desde);

    /** {@code salvo}: o contato está na agenda do corretor. */
    record ConversaImportada(String identificador, String nome, String fotoUrl, boolean salvo,
                             List<MensagemImportada> mensagens) {
    }

    /** {@code id} é o identificador da mensagem no WhatsApp: é ele que impede importar a mesma mensagem duas vezes. */
    record MensagemImportada(String id, boolean doCorretor, String texto, Instant em) {
    }
}
