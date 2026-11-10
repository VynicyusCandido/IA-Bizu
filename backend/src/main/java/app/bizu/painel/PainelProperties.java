package app.bizu.painel;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do painel. A sessão é assinada com um par de chaves RSA: {@code jwtChaveArquivo} aponta para a
 * chave privada (PEM PKCS8). Sem arquivo, a aplicação só sobe com {@code jwtChaveEfemera} (dev e testes).
 * Quem pode entrar é decidido pelo cadastro de corretores no banco, não por configuração.
 */
@ConfigurationProperties("bizu.painel")
record PainelProperties(
        String googleClientId,
        String jwtChaveArquivo,
        boolean jwtChaveEfemera,
        Duration jwtValidade,
        String origemPermitida) {
}
