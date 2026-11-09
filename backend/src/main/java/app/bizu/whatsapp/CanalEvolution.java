package app.bizu.whatsapp;

import app.bizu.conversa.Canal;
import app.bizu.conversa.Conexao;
import app.bizu.conversa.HistoricoDoCorretor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Envia mensagens pela Evolution API (conexão por QR). Formato a confirmar contra a instância real. */
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

    CanalEvolution(EvolutionProperties props) {
        this.props = props;
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("apikey", props.apiKey())
                .build();
    }

    @Override
    public String enviar(String telefone, String texto) {
        Resposta resposta = http.post()
                .uri("/message/sendText/{instancia}", props.instancia())
                .body(Map.of("number", telefone, "text", texto))
                .retrieve()
                .body(Resposta.class);
        if (resposta == null || resposta.key() == null || resposta.key().id() == null) {
            throw new IllegalStateException("A Evolution não devolveu o ID da mensagem enviada");
        }
        return resposta.key().id();
    }

    /** Formato das respostas a confirmar contra a instância real (versão da Evolution em uso). */
    @Override
    public Situacao situacao() {
        JsonNode estado = http.get().uri("/instance/connectionState/{instancia}", props.instancia())
                .retrieve().body(JsonNode.class);
        if (estado != null && "open".equals(estado.path("instance").path("state").asString(""))) {
            return new Situacao(true, null, null);
        }
        JsonNode conexao = http.get().uri("/instance/connect/{instancia}", props.instancia())
                .retrieve().body(JsonNode.class);
        return new Situacao(false,
                conexao == null ? null : conexao.path("base64").asString(null),
                conexao == null ? null : conexao.path("pairingCode").asString(null));
    }

    /** Só texto enviado pelo corretor em conversa individual. Formato a confirmar contra a instância real. */
    @Override
    public List<MensagemEnviada> mensagensEnviadas(int maximo) {
        JsonNode resposta = http.post()
                .uri("/chat/findMessages/{instancia}", props.instancia())
                .body(Map.of("where", Map.of("key", Map.of("fromMe", true)), "page", 1, "offset", maximo))
                .retrieve().body(JsonNode.class);
        List<MensagemEnviada> enviadas = new ArrayList<>();
        if (resposta == null) {
            return enviadas;
        }
        JsonNode registros = resposta.path("messages").path("records");
        for (JsonNode registro : registros.isArray() ? registros : resposta) {
            var mensagem = new EvolutionWebhook("messages.upsert", registro);
            if (mensagem.fromMe() && mensagem.telefone().isPresent() && enviadas.size() < maximo) {
                mensagem.texto().ifPresent(t -> enviadas.add(new MensagemEnviada(mensagem.idMensagem(), t)));
            }
        }
        return enviadas;
    }
}
