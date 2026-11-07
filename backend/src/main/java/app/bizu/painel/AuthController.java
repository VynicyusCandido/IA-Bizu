package app.bizu.painel;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Troca o ID token do Google por uma sessão (JWT do Bizu), só para o e-mail do corretor. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    record PedidoLogin(String idToken) {
    }

    record Sessao(String token, Instant expiraEm) {
    }

    private final VerificadorGoogle google;
    private final JwtEncoder codificador;
    private final PainelProperties props;
    private final Clock relogio;

    AuthController(VerificadorGoogle google, JwtEncoder codificador, PainelProperties props, Clock relogio) {
        this.google = google;
        this.codificador = codificador;
        this.props = props;
        this.relogio = relogio;
    }

    @PostMapping("/google")
    ResponseEntity<Sessao> entrar(@RequestBody PedidoLogin pedido) {
        if (pedido.idToken() == null || pedido.idToken().isBlank()
                || props.emailCorretor() == null || props.emailCorretor().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<String> email = google.emailVerificado(pedido.idToken());
        if (email.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!email.get().equalsIgnoreCase(props.emailCorretor().trim())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Instant agora = relogio.instant();
        Instant expira = agora.plus(props.jwtValidade());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SegurancaConfig.EMISSOR)
                .subject(email.get())
                .issuedAt(agora)
                .expiresAt(expira)
                .build();
        String token = codificador.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return ResponseEntity.ok(new Sessao(token, expira));
    }
}
