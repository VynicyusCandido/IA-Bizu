package app.bizu.agente;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bizu.agente")
record AgenteProperties(String tipo, String modelo, long maxTokens) {
}
