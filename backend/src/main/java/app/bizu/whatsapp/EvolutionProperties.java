package app.bizu.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code instancia} é só o prefixo: a de cada corretor se chama prefixo-authId. */
@ConfigurationProperties("bizu.evolution")
record EvolutionProperties(String baseUrl, String instancia, String apiKey, String webhookUrl) {
}
