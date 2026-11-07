package app.bizu.painel;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do painel. O segredo do JWT e o e-mail do corretor vêm só de variáveis de ambiente.
 * Em branco, o login recusa todo mundo e a aplicação não sobe sem segredo válido.
 */
@ConfigurationProperties("bizu.painel")
record PainelProperties(
        String googleClientId,
        String emailCorretor,
        String jwtSegredo,
        Duration jwtValidade,
        String origemPermitida) {
}
