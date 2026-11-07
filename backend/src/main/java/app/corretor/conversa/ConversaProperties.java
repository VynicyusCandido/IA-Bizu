package app.corretor.conversa;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("corretor.conversa")
public record ConversaProperties(
        int limiteRespostas,
        Duration reinicioApos,
        Duration janelaAgrupamento,
        Duration sondagemFila,
        int maximoTentativas) {
}
