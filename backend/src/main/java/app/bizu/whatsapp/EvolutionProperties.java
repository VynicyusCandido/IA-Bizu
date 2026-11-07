package app.bizu.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bizu.evolution")
record EvolutionProperties(String baseUrl, String instancia, String apiKey) {
}
