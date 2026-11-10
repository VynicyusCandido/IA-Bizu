package app.bizu.whatsapp;

import app.bizu.conversa.Canal;
import app.bizu.conversa.Conexao;
import app.bizu.conversa.HistoricoDoCorretor;
import app.bizu.corretor.Corretor;
import app.bizu.corretor.CorretorService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
class CanalEvolution implements Canal, Conexao, HistoricoDoCorretor {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(Chave key) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chave(String id) {
    }

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
