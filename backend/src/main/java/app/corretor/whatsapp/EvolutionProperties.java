package app.corretor.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("corretor.evolution")
record EvolutionProperties(String baseUrl, String instancia, String apiKey) {
}
