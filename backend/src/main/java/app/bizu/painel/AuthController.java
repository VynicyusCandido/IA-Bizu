package app.bizu.painel;

import app.bizu.corretor.Corretor;
import app.bizu.corretor.CorretorService;
import app.bizu.painel.VerificadorGoogle.IdentidadeGoogle;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Troca o ID token do Google por uma sessão (JWT do Bizu). A conta nasce no primeiro login, inativa: sem
 * liberação do dono do sistema não há sessão. O auth id da sessão é o "sub" do Google.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    record PedidoLogin(String idToken) {
    }

    record Sessao(String token, Instant expiraEm) {
    }

    record Recusa(String motivo) {
    }

    private final VerificadorGoogle google;
    private final CorretorService corretores;
    private final JwtEncoder codificador;
    private final ChavesDaSessao chaves;
    private final PainelProperties props;
    private final Clock relogio;

    AuthController(VerificadorGoogle google, CorretorService corretores, JwtEncoder codificador,
                   ChavesDaSessao chaves, PainelProperties props, Clock relogio) {
        this.google = google;
        this.corretores = corretores;
        this.codificador = codificador;
        this.chaves = chaves;
        this.props = props;
        this.relogio = relogio;
    }

    @PostMapping("/google")
    ResponseEntity<?> entrar(@RequestBody PedidoLogin pedido) {
        if (pedido.idToken() == null || pedido.idToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<IdentidadeGoogle> identidade = google.verificar(pedido.idToken());
        if (identidade.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Instant agora = relogio.instant();
        IdentidadeGoogle quem = identidade.get();
        Corretor corretor = corretores.entrarComGoogle(quem.sub(), quem.email(), quem.nome(), agora);
        if (!corretor.ativo()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Recusa("AGUARDANDO_LIBERACAO"));
        }

        Instant expira = agora.plus(props.jwtValidade());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SegurancaConfig.EMISSOR)
                .subject(corretor.authId())
                .issuedAt(agora)
                .expiresAt(expira)
                .build();
        String token = codificador.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(chaves.chave().getKeyID()).build(), claims)).getTokenValue();
        return ResponseEntity.ok(new Sessao(token, expira));
    }
}
