package app.bizu.conversa;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bizu.conversa")
public record ConversaProperties(
        int limiteRespostas,
        Duration reinicioApos,
        Duration janelaAgrupamento,
        Duration sondagemFila,
        int maximoTentativas,
        int trabalhadores) {
}
