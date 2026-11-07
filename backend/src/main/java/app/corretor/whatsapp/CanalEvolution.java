package app.corretor.whatsapp;

import app.corretor.conversa.Canal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Envia mensagens pela Evolution API (conexão por QR). Formato a confirmar contra a instância real. */
@Component
class CanalEvolution implements Canal {

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
}
