package app.bizu.painel;

import app.bizu.corretor.Corretor;
import app.bizu.corretor.CorretorService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API sem sessão de servidor: tudo em /api exige o JWT emitido por este backend após o login com Google.
 * O webhook da Evolution, o login, a chave pública e a saúde ficam abertos aqui; o webhook se protege com o
 * segredo no header.
 */
@Configuration
class SegurancaConfig {

    static final String EMISSOR = "bizu";

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtDecoder decodificador) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/webhooks/**", "/api/auth/**", "/error",
                                "/.well-known/jwks.json", "/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decodificador)));
        return http.build();
    }

    /** Verifica só com a chave pública (RS256): um token assinado de outro jeito, como HS256, é recusado. */
    @Bean
    JwtDecoder decodificadorDaSessao(ChavesDaSessao chaves, CorretorService corretores) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(chaves.publica()).build();
        // Confere a conta a cada requisição: corretor desligado perde o acesso na hora, sem esperar o token vencer.
        OAuth2TokenValidator<Jwt> contaAtiva = jwt -> corretores.buscar(jwt.getSubject()).filter(Corretor::ativo).isPresent()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Conta inativa", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(EMISSOR), contaAtiva));
        return decoder;
    }

    @Bean
    JwtEncoder codificadorDaSessao(ChavesDaSessao chaves) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(chaves.chave())));
    }

    /** O nome do bean importa: é por ele que o Spring Security acha esta configuração de CORS. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(PainelProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(props.origemPermitida()));
        config.setAllowedMethods(List.of("GET", "PUT", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", config);
        return fonte;
    }
}
