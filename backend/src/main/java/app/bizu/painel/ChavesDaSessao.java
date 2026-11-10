package app.bizu.painel;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;

/**
 * Par de chaves que assina a sessão do painel. Só este backend tem a privada; outros serviços verificam pela
 * pública, publicada em /.well-known/jwks.json, e por isso não conseguem fabricar sessões.
 */
@Component
class ChavesDaSessao {

    private static final Logger log = LoggerFactory.getLogger(ChavesDaSessao.class);

    private final RSAKey chave;

    ChavesDaSessao(PainelProperties props) {
        boolean temArquivo = props.jwtChaveArquivo() != null && !props.jwtChaveArquivo().isBlank();
        if (!temArquivo && !props.jwtChaveEfemera()) {
            throw new IllegalStateException("Defina BIZU_JWT_CHAVE_ARQUIVO com o caminho da chave privada RSA (PEM PKCS8) "
                    + "que assina a sessão do painel. Em dev, use o perfil dev (chave nova a cada partida).");
        }
        try {
            KeyPair par = temArquivo ? lerDoArquivo(Path.of(props.jwtChaveArquivo())) : gerar();
            if (!temArquivo) {
                log.warn("Usando uma chave de sessão efêmera: as sessões do painel caem ao reiniciar. Só para dev e testes.");
            }
            this.chave = new RSAKey.Builder((RSAPublicKey) par.getPublic())
                    .privateKey((RSAPrivateKey) par.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Não consegui carregar a chave de sessão: " + e.getMessage(), e);
        }
    }

    RSAKey chave() {
        return chave;
    }

    RSAPublicKey publica() throws Exception {
        return chave.toRSAPublicKey();
    }

    /** Só a parte pública, no formato JWKS. Nunca inclui o expoente privado. */
    JWKSet publicaEmJwks() {
        return new JWKSet(chave.toPublicJWK());
    }

    private static KeyPair gerar() throws NoSuchAlgorithmException {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        return gerador.generateKeyPair();
    }

    private static KeyPair lerDoArquivo(Path arquivo) throws IOException, java.security.GeneralSecurityException {
        RSAPrivateKey privada;
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            privada = RsaKeyConverters.pkcs8().convert(entrada);
        }
        if (!(privada instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("A chave precisa ser RSA com os parâmetros CRT (padrão do openssl)");
        }
        RSAPublicKey publica = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        return new KeyPair(publica, privada);
    }
}
