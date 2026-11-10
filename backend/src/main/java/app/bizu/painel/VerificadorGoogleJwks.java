package app.bizu.painel;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
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

    private final NimbusJwtDecoder decodificador;

    @Autowired
    VerificadorGoogleJwks(PainelProperties props) {
        this(NimbusJwtDecoder.withJwkSetUri(JWKS).build(), props.googleClientId());
    }

    /** Aceita o decodificador de fora para os testes usarem uma chave local no lugar das chaves do Google. */
    VerificadorGoogleJwks(NimbusJwtDecoder decodificador, String googleClientId) {
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), emissor(), destinatario(googleClientId)));
        this.decodificador = decodificador;
    }

    /**
     * O Google emite "https://accounts.google.com" ou "accounts.google.com" (sem https). Por isso lê o emissor como
     * texto: {@code jwt.getIssuer()} converte para URL e estoura com a segunda forma, que viraria erro 500 no login.
     */
    private static OAuth2TokenValidator<Jwt> emissor() {
        return jwt -> EMISSORES.contains(jwt.getClaimAsString("iss") == null ? "" : jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emissor inválido", null));
    }

    /** O token precisa ter sido emitido para este app. Sem ID de cliente configurado, recusa todos. */
    private static OAuth2TokenValidator<Jwt> destinatario(String googleClientId) {
        boolean configurado = googleClientId != null && !googleClientId.isBlank();
        return jwt -> configurado && jwt.getAudience() != null && jwt.getAudience().contains(googleClientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Destinatário inválido", null));
    }

    @Override
    public Optional<IdentidadeGoogle> verificar(String idToken) {
        try {
            Jwt jwt = decodificador.decode(idToken);
            String email = jwt.getClaimAsString("email");
            if (jwt.getSubject() == null || email == null
                    || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
                return Optional.empty();
            }
            return Optional.of(new IdentidadeGoogle(jwt.getSubject(), email.toLowerCase(), jwt.getClaimAsString("name")));
        } catch (JwtException e) {
            log.info("ID token do Google recusado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
