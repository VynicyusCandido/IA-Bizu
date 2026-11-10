package app.bizu.whatsapp;

import app.bizu.conversa.ConversaService.ContatoSalvo;
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
    private static final String LID = "@lid";

    boolean ehNovaMensagem() {
        return evento("messages.upsert") && data != null && data.isObject();
    }

    /** Contatos salvos na agenda do corretor, entregues quando o WhatsApp sincroniza. */
    boolean ehContatos() {
        return evento("contacts.upsert");
    }

    /**
     * Contatos de conversa individual do evento, com o nome que o WhatsApp informa. Grupos e formatos
     * desconhecidos são ignorados.
     */
    List<ContatoSalvo> contatos() {
        List<ContatoSalvo> contatos = new ArrayList<>();
        if (!ehContatos() || data == null) {
            return contatos;
        }
        Iterable<JsonNode> itens = data.isArray() ? data : List.of(data);
        for (JsonNode contato : itens) {
            String jid = texto(contato.path("remoteJid"));
            if (jid == null) {
                jid = texto(contato.path("id"));
            }
            identificadorDe(jid, texto(contato.path("remoteJidAlt")))
                    .ifPresent(id -> contatos.add(new ContatoSalvo(id, texto(contato.path("pushName")))));
        }
        return contatos;
    }

    /** O JID de destino da mensagem, como veio (serve para registrar o que foi descartado). */
    Optional<String> jid() {
        return Optional.ofNullable(data == null ? null : texto(data.path("key").path("remoteJid")));
    }

    /**
     * Quem é o contato, só para conversa individual: o telefone (dígitos) ou, quando o WhatsApp só informa um código
     * (@lid) e não traz o telefone, esse código com o "@lid". Grupos, status e outros destinos são descartados.
     */
    Optional<String> telefone() {
        String alternativo = data == null ? null : texto(data.path("key").path("remoteJidAlt"));
        return identificadorDe(jid().orElse(null), alternativo);
    }

    /** Nome que o próprio contato usa no WhatsApp (pushName), em mensagem que ele mandou. */
    Optional<String> nomeDoContato() {
        if (data == null || fromMe()) {
            return Optional.empty();
        }
        return Optional.ofNullable(texto(data.path("pushName"))).filter(n -> !n.isBlank());
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

    /** O WhatsApp novo identifica muitos contatos por @lid e põe o telefone verdadeiro em outro campo (jid alternativo). */
    private static Optional<String> identificadorDe(String jid, String jidAlternativo) {
        Optional<String> telefone = telefoneDe(jid).or(() -> telefoneDe(jidAlternativo));
        if (telefone.isPresent()) {
            return telefone;
        }
        return jid != null && jid.endsWith(LID) ? Optional.of(jid) : Optional.empty();
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
