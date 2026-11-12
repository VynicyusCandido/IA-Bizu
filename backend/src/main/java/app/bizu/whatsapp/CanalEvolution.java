package app.bizu.whatsapp;

import app.bizu.conversa.Canal;
import app.bizu.conversa.Conexao;
import app.bizu.conversa.HistoricoDeConversas;
import app.bizu.conversa.HistoricoDoCorretor;
import app.bizu.corretor.Corretor;
import app.bizu.corretor.CorretorService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Fala com a Evolution API (conexão por QR). Cada corretor tem a própria instância, criada na primeira vez que
 * ele abre a conexão. Formato a confirmar contra uma instância real.
 */
@Component
class CanalEvolution implements Canal, Conexao, HistoricoDoCorretor, HistoricoDeConversas {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(Chave key) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chave(String id) {
    }

    private static final int TAMANHO_PAGINA = 100;
    private static final int MAXIMO_PAGINAS = 20;

    private final RestClient http;
    private final EvolutionProperties props;
    private final CorretorService corretores;
    private final String segredoDoWebhook;

    CanalEvolution(EvolutionProperties props, CorretorService corretores,
                   @Value("${bizu.webhook-segredo:}") String segredoDoWebhook) {
        this.props = props;
        this.corretores = corretores;
        this.segredoDoWebhook = segredoDoWebhook;
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("apikey", props.apiKey())
                .build();
    }

    @Override
    public String enviar(String corretorId, String telefone, String texto) {
        String instancia = instanciaDe(corretorId)
                .orElseThrow(() -> new IllegalStateException("O corretor " + corretorId + " ainda não conectou o WhatsApp"));
        Resposta resposta = http.post()
                .uri("/message/sendText/{instancia}", instancia)
                .body(Map.of("number", telefone, "text", texto))
                .retrieve()
                .body(Resposta.class);
        if (resposta == null || resposta.key() == null || resposta.key().id() == null) {
            throw new IllegalStateException("A Evolution não devolveu o ID da mensagem enviada");
        }
        return resposta.key().id();
    }

    /** Só consulta. Formato da resposta a confirmar contra a instância real (versão da Evolution em uso). */
    @Override
    public boolean conectado(String corretorId) {
        return instanciaDe(corretorId).map(this::estaConectada).orElse(false);
    }

    /** Cria a instância na primeira vez e pede o QR se ainda não conectou. Formato a confirmar contra a real. */
    @Override
    public Situacao conectar(String corretorId) {
        String instancia = garantirInstancia(corretorId);
        if (estaConectada(instancia)) {
            return new Situacao(true, null, null);
        }
        JsonNode conexao = http.get().uri("/instance/connect/{instancia}", instancia)
                .retrieve().body(JsonNode.class);
        return new Situacao(false,
                conexao == null ? null : conexao.path("base64").asString(null),
                conexao == null ? null : conexao.path("pairingCode").asString(null));
    }

    /** Só texto enviado pelo corretor em conversa individual. Formato a confirmar contra a instância real. */
    @Override
    public List<MensagemEnviada> mensagensEnviadas(String corretorId, int maximo) {
        List<MensagemEnviada> enviadas = new ArrayList<>();
        Optional<String> instancia = instanciaDe(corretorId);
        if (instancia.isEmpty()) {
            return enviadas;
        }
        JsonNode resposta = http.post()
                .uri("/chat/findMessages/{instancia}", instancia.get())
                .body(Map.of("where", Map.of("key", Map.of("fromMe", true)), "page", 1, "offset", maximo))
                .retrieve().body(JsonNode.class);
        if (resposta == null) {
            return enviadas;
        }
        JsonNode registros = resposta.path("messages").path("records");
        for (JsonNode registro : registros.isArray() ? registros : resposta) {
            var mensagem = new EvolutionWebhook("messages.upsert", null, registro);
            if (mensagem.fromMe() && mensagem.telefone().isPresent() && enviadas.size() < maximo) {
                mensagem.texto().ifPresent(t -> enviadas.add(new MensagemEnviada(mensagem.idMensagem(), t)));
            }
        }
        return enviadas;
    }

    /**
     * Conversas individuais com mensagem desde o instante dado, lidas do que a Evolution guardou. Para cada conversa
     * recente pagina as mensagens (da mais nova para a mais velha) até passar da data. Formato conferido na Evolution
     * 2.3.7. shortcut: no máximo 2000 mensagens por conversa, o suficiente para uma semana de atendimento.
     */
    @Override
    public List<ConversaImportada> desde(String corretorId, Instant desde) {
        List<ConversaImportada> conversas = new ArrayList<>();
        Optional<String> instancia = instanciaDe(corretorId);
        if (instancia.isEmpty()) {
            return conversas;
        }
        JsonNode chats = http.post().uri("/chat/findChats/{instancia}", instancia.get())
                .body(Map.of()).retrieve().body(JsonNode.class);
        if (chats == null || !chats.isArray()) {
            return conversas;
        }
        long desdeSegundos = desde.getEpochSecond();
        for (JsonNode chat : chats) {
            String jid = chat.path("remoteJid").asString("");
            boolean individual = jid.endsWith("@s.whatsapp.net") || jid.endsWith("@lid");
            if (individual && chat.path("lastMessage").path("messageTimestamp").asLong(0) >= desdeSegundos) {
                conversaImportada(instancia.get(), chat, jid, desdeSegundos).ifPresent(conversas::add);
            }
        }
        return conversas;
    }

    private Optional<ConversaImportada> conversaImportada(String instancia, JsonNode chat, String jid, long desdeSegundos) {
        List<MensagemImportada> mensagens = new ArrayList<>();
        String telefone = jid.endsWith("@s.whatsapp.net") ? jid.substring(0, jid.indexOf('@')) : null;
        // O nome do contato é o pushName de uma mensagem que ELE mandou: o do chat vem vazio, e o pushName de
        // mensagem do corretor é o nome do próprio corretor.
        String nome = null;
        for (int pagina = 1; pagina <= MAXIMO_PAGINAS; pagina++) {
            JsonNode resposta = http.post().uri("/chat/findMessages/{instancia}", instancia)
                    .body(Map.of("where", Map.of("key", Map.of("remoteJid", jid)), "page", pagina, "offset", TAMANHO_PAGINA))
                    .retrieve().body(JsonNode.class);
            JsonNode registros = resposta == null ? null : resposta.path("messages").path("records");
            if (registros == null || !registros.isArray() || registros.size() == 0) {
                break;
            }
            boolean passouDaData = false;
            for (JsonNode registro : registros) {
                if (telefone == null) {
                    String alternativo = registro.path("key").path("remoteJidAlt").asString("");
                    if (alternativo.endsWith("@s.whatsapp.net")) {
                        telefone = alternativo.substring(0, alternativo.indexOf('@'));
                    }
                }
                if (nome == null && !registro.path("key").path("fromMe").asBoolean(false)) {
                    String pushName = registro.path("pushName").asString("");
                    nome = pushName.isBlank() ? null : pushName.strip();
                }
                long segundos = registro.path("messageTimestamp").asLong(0);
                if (segundos < desdeSegundos) {
                    passouDaData = true; // da mais nova para a mais velha: o resto da conversa é anterior
                    continue;
                }
                String id = registro.path("key").path("id").asString("");
                String texto = textoDe(registro);
                if (!id.isBlank() && texto != null) {
                    mensagens.add(new MensagemImportada(id, registro.path("key").path("fromMe").asBoolean(false),
                            texto, Instant.ofEpochSecond(segundos)));
                }
            }
            if (passouDaData) {
                break;
            }
        }
        if (mensagens.isEmpty()) {
            return Optional.empty();
        }
        String foto = chat.path("profilePicUrl").asString("");
        return Optional.of(new ConversaImportada(telefone != null ? telefone : jid, nome,
                foto.isBlank() ? null : foto, chat.path("isSaved").asBoolean(false), mensagens));
    }

    /** Texto para mostrar no painel; mídia vira um marcador ("[áudio]"). Tipos de bastidor (reação etc.) são ignorados. */
    private static String textoDe(JsonNode registro) {
        JsonNode m = registro.path("message");
        return switch (registro.path("messageType").asString("")) {
            case "conversation" -> naoVazio(m.path("conversation"));
            case "extendedTextMessage" -> naoVazio(m.path("extendedTextMessage").path("text"));
            case "imageMessage" -> comLegenda("[imagem]", m.path("imageMessage").path("caption"));
            case "videoMessage" -> comLegenda("[vídeo]", m.path("videoMessage").path("caption"));
            case "audioMessage" -> "[áudio]";
            case "documentMessage" -> comLegenda("[documento]", m.path("documentMessage").path("fileName"));
            case "stickerMessage" -> "[figurinha]";
            case "contactMessage" -> "[contato]";
            case "locationMessage" -> "[localização]";
            default -> null;
        };
    }

    private static String naoVazio(JsonNode no) {
        String texto = no.asString("");
        return texto.isBlank() ? null : texto;
    }

    private static String comLegenda(String marcador, JsonNode legenda) {
        String texto = legenda.asString("");
        return texto.isBlank() ? marcador : marcador + " " + texto.strip();
    }

    private boolean estaConectada(String instancia) {
        JsonNode estado = http.get().uri("/instance/connectionState/{instancia}", instancia)
                .retrieve().body(JsonNode.class);
        return estado != null && "open".equals(estado.path("instance").path("state").asString(""));
    }

    private Optional<String> instanciaDe(String corretorId) {
        return corretores.buscar(corretorId).map(Corretor::instancia);
    }

    /** Instância do corretor; na primeira vez cria na Evolution (já com o webhook apontando para cá). */
    private String garantirInstancia(String corretorId) {
        Optional<String> existente = instanciaDe(corretorId);
        if (existente.isPresent()) {
            return existente.get();
        }
        if (segredoDoWebhook.isBlank()) {
            // Sem o segredo a instância nasceria enviando o header vazio e o Bizu recusaria todo webhook em silêncio.
            throw new IllegalStateException("Defina WEBHOOK_SEGREDO antes de conectar um WhatsApp");
        }
        String nome = props.instancia() + "-" + corretorId;
        try {
            http.post().uri("/instance/create")
                    .body(Map.of(
                            "instanceName", nome,
                            "integration", "WHATSAPP-BAILEYS",
                            "qrcode", true,
                            "webhook", Map.of(
                                    "url", props.webhookUrl(),
                                    "byEvents", false,
                                    "base64", false,
                                    "headers", Map.of("apikey", segredoDoWebhook),
                                    "events", List.of("MESSAGES_UPSERT", "CONTACTS_UPSERT"))))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // 403/409: a instância já existe (outra chamada criou ao mesmo tempo); basta guardar o nome.
            if (e.getStatusCode() != HttpStatus.FORBIDDEN && e.getStatusCode() != HttpStatus.CONFLICT) {
                throw e;
            }
        }
        corretores.definirInstancia(corretorId, nome);
        return nome;
    }
}
