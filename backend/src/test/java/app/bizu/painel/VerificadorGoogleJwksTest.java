package app.bizu.painel;

import static org.assertj.core.api.Assertions.assertThat;

import app.bizu.painel.VerificadorGoogle.IdentidadeGoogle;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * A porta de entrada do sistema: confere o ID token do Google. Usa uma chave local no lugar das chaves do Google,
 * com o mesmo decodificador e os mesmos validadores de produção.
 */
class VerificadorGoogleJwksTest {

    private static final String CLIENTE = "meu-client-id.apps.googleusercontent.com";

    private static KeyPair par;
    private static KeyPair outroPar;

    @BeforeAll
    static void gerarChaves() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        par = gerador.generateKeyPair();
        outroPar = gerador.generateKeyPair();
    }

    private static VerificadorGoogleJwks verificador(String clientId) {
        return new VerificadorGoogleJwks(NimbusJwtDecoder.withPublicKey((RSAPublicKey) par.getPublic()).build(), clientId);
    }

    /** Token do jeito que o Google emite, assinado por {@code assinante}; {@code ajuste} altera o que o teste quer quebrar. */
    private static String token(KeyPair assinante, Consumer<JwtClaimsSet.Builder> ajuste) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENTE))
                .subject("108234567890123456789")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("email", "Fulana@Exemplo.com")
                .claim("email_verified", true)
                .claim("name", "Fulana de Tal");
        ajuste.accept(claims);
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) assinante.getPublic())
                .privateKey(assinante.getPrivate()).keyID("k").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("k").build(), claims.build()))
                .getTokenValue();
    }

    @Test
    void tokenValidoDevolveQuemEAPessoa() {
        Optional<IdentidadeGoogle> quem = verificador(CLIENTE).verificar(token(par, c -> { }));

        assertThat(quem).contains(new IdentidadeGoogle("108234567890123456789", "fulana@exemplo.com", "Fulana de Tal"));
    }

    @Test
    void emissorSemOHttpsTambemEAceito() {
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c.issuer("accounts.google.com")))).isPresent();
    }

    @Test
    void tokenEmitidoParaOutroAppNaoVale() {
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c.audience(List.of("outro-app"))))).isEmpty();
    }

    @Test
    void tokenSemDestinatarioNaoVaiAoErro500() {
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c.claims(m -> m.remove("aud"))))).isEmpty();
    }

    @Test
    void emissorDesconhecidoNaoVale() {
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c.issuer("https://accounts.falso.com")))).isEmpty();
    }

    @Test
    void tokenVencidoNaoVale() {
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c
                .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600))))).isEmpty();
    }

    @Test
    void emailNaoVerificadoOuAusenteNaoVale() {
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c.claim("email_verified", false)))).isEmpty();
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c.claims(m -> m.remove("email_verified"))))).isEmpty();
        assertThat(verificador(CLIENTE).verificar(token(par, c -> c.claims(m -> m.remove("email"))))).isEmpty();
    }

    @Test
    void tokenAssinadoPorOutraChaveNaoVale() {
        assertThat(verificador(CLIENTE).verificar(token(outroPar, c -> { }))).isEmpty();
    }

    @Test
    void semIdDeClienteConfiguradoNinguemEntra() {
        assertThat(verificador("").verificar(token(par, c -> { }))).isEmpty();
        assertThat(verificador(null).verificar(token(par, c -> { }))).isEmpty();
    }

    @Test
    void textoQueNaoEUmTokenNaoVale() {
        assertThat(verificador(CLIENTE).verificar("isto-nao-e-um-jwt")).isEmpty();
    }
}
