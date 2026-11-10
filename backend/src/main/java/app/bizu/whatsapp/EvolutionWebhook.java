package app.bizu.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Parte do payload do webhook da Evolution que o sistema usa. {@code data} fica como árvore JSON porque
 * muda de forma conforme o evento: objeto em messages.upsert, lista em contacts.upsert.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EvolutionWebhook(String event, String instance, JsonNode data) {

    private static final String INDIVIDUAL = "@s.whatsapp.net";

    boolean ehNovaMensagem() {
        return evento("messages.upsert") && data != null && data.isObject();
    }

    /** Contatos salvos na agenda do corretor, entregues quando o WhatsApp sincroniza. */
    boolean ehContatos() {
        return evento("contacts.upsert");
    }

    /** Telefones de conversa individual dos contatos do evento; grupos e formatos desconhecidos são ignorados. */
    List<String> telefonesDeContatos() {
        List<String> telefones = new ArrayList<>();
        if (!ehContatos() || data == null) {
            return telefones;
        }
        Iterable<JsonNode> contatos = data.isArray() ? data : List.of(data);
        for (JsonNode contato : contatos) {
            String jid = texto(contato.path("remoteJid"));
            if (jid == null) {
                jid = texto(contato.path("id"));
            }
            telefoneDe(jid).ifPresent(telefones::add);
        }
        return telefones;
    }

    /** O JID de destino da mensagem, como veio (serve para registrar o que foi descartado). */
    Optional<String> jid() {
        return Optional.ofNullable(data == null ? null : texto(data.path("key").path("remoteJid")));
    }

    /** Telefone só para conversa individual; grupos, status e outros destinos são descartados. */
    Optional<String> telefone() {
        return jid().flatMap(EvolutionWebhook::telefoneDe);
    }

    Optional<String> texto() {
        if (data == null) {
            return Optional.empty();
        }
        JsonNode mensagem = data.path("message");
        String texto = texto(mensagem.path("conversation"));
        if (texto == null || texto.isBlank()) {
            texto = texto(mensagem.path("extendedTextMessage").path("text"));
        }
        return Optional.ofNullable(texto).filter(t -> !t.isBlank());
    }

    boolean fromMe() {
        return data != null && data.path("key").path("fromMe").asBoolean(false);
    }

    String idMensagem() {
        return data == null ? null : texto(data.path("key").path("id"));
    }

    private boolean evento(String nome) {
        return event != null && event.replace('_', '.').equalsIgnoreCase(nome);
    }

    private static Optional<String> telefoneDe(String jid) {
        if (jid == null || !jid.endsWith(INDIVIDUAL)) {
            return Optional.empty();
        }
        return Optional.of(jid.substring(0, jid.indexOf('@')));
    }

    private static String texto(JsonNode no) {
        return no.isTextual() ? no.asText() : null;
    }
}
