package app.bizu.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;

/** Parte do payload do webhook da Evolution (evento messages.upsert) que o sistema usa. */
@JsonIgnoreProperties(ignoreUnknown = true)
record EvolutionWebhook(String event, Dados data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Dados(Chave key, Mensagem message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chave(String remoteJid, boolean fromMe, String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Mensagem(String conversation, TextoEstendido extendedTextMessage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextoEstendido(String text) {
    }

    boolean ehNovaMensagem() {
        return event != null && event.replace('_', '.').equalsIgnoreCase("messages.upsert") && data != null;
    }

    /** Telefone só para conversa individual; grupos, status e outros destinos são descartados. */
    Optional<String> telefone() {
        if (data == null || data.key() == null || data.key().remoteJid() == null) {
            return Optional.empty();
        }
        String jid = data.key().remoteJid();
        if (!jid.endsWith("@s.whatsapp.net")) {
            return Optional.empty();
        }
        return Optional.of(jid.substring(0, jid.indexOf('@')));
    }

    Optional<String> texto() {
        if (data == null || data.message() == null) {
            return Optional.empty();
        }
        Mensagem m = data.message();
        String texto = m.conversation();
        if ((texto == null || texto.isBlank()) && m.extendedTextMessage() != null) {
            texto = m.extendedTextMessage().text();
        }
        return Optional.ofNullable(texto).filter(t -> !t.isBlank());
    }
}
