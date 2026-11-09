package app.bizu.painel;

import static org.assertj.core.api.Assertions.assertThat;

import app.bizu.BizuApplication;
import app.bizu.conversa.Conexao;
import app.bizu.conversa.ConversaService;
import app.bizu.conversa.ConversaService.Resultado;
import app.bizu.conversa.HistoricoDoCorretor;
import app.bizu.conversa.PromptService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Login com Google (verificador trocado por um dublê), sessão JWT e API do prompt, contra Postgres real. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = {BizuApplication.class, PainelApiIT.Dublês.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "bizu.conversa.sondagem-fila=1h",
                "bizu.webhook-segredo=teste",
                "bizu.painel.email-corretor=corretor@exemplo.com",
                "bizu.painel.google-client-id=client-teste",
                "bizu.painel.jwt-segredo=segredo-de-teste-com-mais-de-32-caracteres"
        })
class PainelApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @TestConfiguration
    static class Dublês {
        @Bean
        @Primary
        VerificadorGoogle googleFalso() {
            return token -> switch (token) {
                case "token-do-corretor" -> Optional.of("corretor@exemplo.com");
                case "token-de-outra-pessoa" -> Optional.of("outra@exemplo.com");
                default -> Optional.empty();
            };
        }

        @Bean
        @Primary
        Conexao conexaoFalsa() {
            return () -> new Conexao.Situacao(false, "data:image/png;base64,QR", "ABCD-1234");
        }

        @Bean
        @Primary
        HistoricoDoCorretor historicoFalso() {
            return maximo -> List.of();
        }
    }

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${local.server.port}") int porta;
    @Autowired PromptService prompts;
    @Autowired ConversaService conversas;
    @Autowired JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void limpar() {
        jdbc.sql("delete from fila_mensagem").update();
        jdbc.sql("delete from lead").update();
        jdbc.sql("delete from mensagem").update();
        jdbc.sql("delete from conversa").update();
        jdbc.sql("delete from contato_ignorado").update();
    }

    @Test
    void apiExigeSessao() throws Exception {
        assertThat(chamar("GET", "/api/prompt", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void loginRecusaTokenInvalidoEOutraConta() throws Exception {
        assertThat(login("token-qualquer").statusCode()).isEqualTo(401);
        assertThat(login("token-de-outra-pessoa").statusCode()).isEqualTo(403);
    }

    @Test
    void corretorEntraEAcessaOPrompt() throws Exception {
        String token = sessao();

        HttpResponse<String> resposta = chamar("GET", "/api/prompt", null, token);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("\"base\"").contains("\"regrasFixas\"").contains("Nunca invente preço");
    }

    @Test
    void promptSalvoNoPainelValeParaOAgente() throws Exception {
        String token = sessao();

        HttpResponse<String> salvo = chamar("PUT", "/api/prompt", "{\"base\":\"Seja direto e simpático.\"}", token);

        assertThat(salvo.statusCode()).isEqualTo(200);
        assertThat(salvo.body()).contains("Seja direto e simpático.");
        assertThat(prompts.base()).isEqualTo("Seja direto e simpático.");
        assertThat(chamar("GET", "/api/prompt", null, token).body()).contains("Seja direto e simpático.");
    }

    @Test
    void promptEnormeERecusado() throws Exception {
        String grande = "a".repeat(PromptController.TAMANHO_MAXIMO + 1);

        assertThat(chamar("PUT", "/api/prompt", "{\"base\":\"" + grande + "\"}", sessao()).statusCode())
                .isEqualTo(400);
    }

    @Test
    void tokenAssinadoComOutroSegredoENaoVale() throws Exception {
        var outraChave = new SecretKeySpec("outro-segredo-com-mais-de-32-caracteres!".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        String forjado = new NimbusJwtEncoder(new ImmutableSecret<>(outraChave)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("bizu").subject("corretor@exemplo.com")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build())).getTokenValue();

        assertThat(chamar("GET", "/api/prompt", null, forjado).statusCode()).isEqualTo(401);
    }

    @Test
    void webhookContinuaProtegidoPeloProprioSegredo() throws Exception {
        String corpo = "{\"event\":\"connection.update\"}";
        assertThat(webhook(null, corpo).statusCode()).isEqualTo(401);
        assertThat(webhook("segredo-errado", corpo).statusCode()).isEqualTo(401);
        assertThat(webhook("teste", corpo).statusCode()).isEqualTo(200);
    }

    @Test
    void contatosSalvosVindosDoWebhookNuncaSaoAtendidos() throws Exception {
        String corpo = "{\"event\":\"contacts.upsert\",\"data\":[{\"remoteJid\":\"5511977776666@s.whatsapp.net\"}]}";

        assertThat(webhook("teste", corpo).statusCode()).isEqualTo(200);

        assertThat(conversas.receberDoCliente("5511977776666", "Oi", "WA-AGENDA-1", Instant.now()))
                .isEqualTo(Resultado.IGNORADA);
        assertThat(conversas.receberDoCliente("5511966665555", "Oi", "WA-NOVO-1", Instant.now()))
                .isEqualTo(Resultado.ENFILEIRADA);
    }

    @Test
    void painelDeConversasExigeSessao() throws Exception {
        assertThat(chamar("GET", "/api/conversas", null, null).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/conversas/" + UUID.randomUUID() + "/assumir", null, null).statusCode())
                .isEqualTo(401);
        assertThat(chamar("GET", "/api/whatsapp", null, null).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/estilo/gerar", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void corretorVeAsConversasComAQualificacaoEAssumeEDevolve() throws Exception {
        String token = sessao();
        conversas.receberDoCliente("5511988887777", "Quero comprar", "WA-P-1", Instant.now());
        jdbc.sql("""
                insert into lead (conversa_id, finalidade, regiao, faixa_valor, prazo, atualizado_em)
                select id, 'compra', 'Centro', 'até 500 mil', '3 meses', now() from conversa
                """).update();

        HttpResponse<String> lista = chamar("GET", "/api/conversas", null, token);

        assertThat(lista.statusCode()).isEqualTo(200);
        assertThat(lista.body()).contains("5511988887777").contains("BOT").contains("Centro").contains("3 meses");
        Matcher id = Pattern.compile("\"id\"\s*:\s*\"([^\"]+)\"").matcher(lista.body());
        assertThat(id.find()).isTrue();

        HttpResponse<String> assumida = chamar("POST", "/api/conversas/" + id.group(1) + "/assumir", null, token);
        assertThat(assumida.statusCode()).isEqualTo(200);
        assertThat(assumida.body()).contains("HUMANO");
        assertThat(conversas.receberDoCliente("5511988887777", "Alô", "WA-P-2", Instant.now()))
                .isEqualTo(Resultado.REGISTRADA);

        HttpResponse<String> devolvida = chamar("POST", "/api/conversas/" + id.group(1) + "/devolver", null, token);
        assertThat(devolvida.statusCode()).isEqualTo(200);
        assertThat(devolvida.body()).contains("BOT");
        assertThat(conversas.receberDoCliente("5511988887777", "Voltei", "WA-P-3", Instant.now()))
                .isEqualTo(Resultado.ENFILEIRADA);
    }

    @Test
    void assumirConversaQueNaoExisteDa404() throws Exception {
        assertThat(chamar("POST", "/api/conversas/" + UUID.randomUUID() + "/assumir", null, sessao()).statusCode())
                .isEqualTo(404);
        assertThat(chamar("POST", "/api/conversas/nao-e-um-id/devolver", null, sessao()).statusCode())
                .isEqualTo(400);
    }

    @Test
    void painelMostraOQrCodeParaConectarONumero() throws Exception {
        HttpResponse<String> resposta = chamar("GET", "/api/whatsapp", null, sessao());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("\"conectado\":false").contains("data:image/png;base64,QR")
                .contains("ABCD-1234");
    }

    @Test
    void semMensagensSuficientesOPerfilDeEstiloNaoEGerado() throws Exception {
        HttpResponse<String> resposta = chamar("POST", "/api/estilo/gerar", null, sessao());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("\"gerado\":false");
    }

    private String sessao() throws Exception {
        HttpResponse<String> resposta = login("token-do-corretor");
        assertThat(resposta.statusCode()).isEqualTo(200);
        Matcher m = TOKEN.matcher(resposta.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private HttpResponse<String> login(String idToken) throws Exception {
        return chamar("POST", "/api/auth/google", "{\"idToken\":\"" + idToken + "\"}", null);
    }

    private HttpResponse<String> webhook(String apikey, String corpo) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/webhooks/evolution"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        if (apikey != null) {
            req.header("apikey", apikey);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> chamar(String metodo, String caminho, String corpo, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(corpo));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
