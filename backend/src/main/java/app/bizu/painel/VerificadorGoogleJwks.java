package app.bizu.painel;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** Confere assinatura (chaves públicas do Google), emissor, destinatário, validade e e-mail verificado. */
@Component
class VerificadorGoogleJwks implements VerificadorGoogle {

    private static final Logger log = LoggerFactory.getLogger(VerificadorGoogleJwks.class);
    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> EMISSORES = List.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decodificador;

    VerificadorGoogleJwks(PainelProperties props) {
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
        OAuth2TokenValidator<Jwt> emissor = jwt -> EMISSORES.contains(jwt.getIssuer() == null ? "" : jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emissor inválido", null));
        OAuth2TokenValidator<Jwt> destinatario = jwt -> jwt.getAudience().contains(props.googleClientId())
                && props.googleClientId() != null && !props.googleClientId().isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Destinatário inválido", null));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), emissor, destinatario));
        this.decodificador = nimbus;
    }

    @Override
    public Optional<String> emailVerificado(String idToken) {
        try {
            Jwt jwt = decodificador.decode(idToken);
            String email = jwt.getClaimAsString("email");
            if (email == null || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
                return Optional.empty();
            }
            return Optional.of(email.toLowerCase());
        } catch (JwtException e) {
            log.info("ID token do Google recusado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
