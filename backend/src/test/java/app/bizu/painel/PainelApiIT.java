package app.bizu.painel;

import static org.assertj.core.api.Assertions.assertThat;

import app.bizu.BizuApplication;
import app.bizu.conversa.Conexao;
import app.bizu.conversa.ConversaService;
import app.bizu.conversa.ConversaService.Resultado;
import app.bizu.conversa.HistoricoDeConversas;
import app.bizu.conversa.HistoricoDeConversas.ConversaImportada;
import app.bizu.conversa.HistoricoDeConversas.MensagemImportada;
import app.bizu.conversa.HistoricoDoCorretor;
import app.bizu.conversa.PromptService;
import app.bizu.corretor.ContaCriada;
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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
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
                "bizu.painel.google-client-id=client-teste",
                "bizu.painel.jwt-chave-efemera=true"
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
                case "token-do-corretor" ->
                        Optional.of(new VerificadorGoogle.IdentidadeGoogle(DONO, "corretor@exemplo.com", "Corretor Teste"));
                case "token-de-outra-pessoa" ->
                        Optional.of(new VerificadorGoogle.IdentidadeGoogle(OUTRO, "outra@exemplo.com", "Outra Pessoa"));
                default -> Optional.empty();
            };
        }

        @Bean
        CapturaDeContas capturaDeContas() {
            return new CapturaDeContas();
        }

        @Bean
        @Primary
        Conexao conexaoFalsa() {
            return new Conexao() {
                @Override
                public boolean conectado(String corretorId) {
                    return false;
                }

                @Override
                public Situacao conectar(String corretorId) {
                    return new Situacao(false, "data:image/png;base64,QR", "ABCD-1234");
                }
            };
        }

        @Bean
        @Primary
        HistoricoDeConversas historicoDeConversasFalso() {
            return (corretorId, desde) -> List.of(new ConversaImportada("5511933332222", "Cliente Importado", false,
                    List.of(new MensagemImportada("IMP-1", false, "Oi, vi o anúncio", Instant.now().minusSeconds(7200)),
                            new MensagemImportada("IMP-2", false, "[áudio]", Instant.now().minusSeconds(3600)))));
        }

        @Bean
        @Primary
        HistoricoDoCorretor historicoFalso() {
            return (corretorId, maximo) -> List.of();
        }
    }

    static final String DONO = "sub-corretor";
    static final String OUTRO = "sub-outra";

    static final List<ContaCriada> contasCriadas = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Guarda os eventos de conta nova para o teste conferir que sai um por conta, e só um. */
    static class CapturaDeContas {
        @org.springframework.context.event.EventListener
        public void aoCriar(ContaCriada evento) {
            contasCriadas.add(evento);
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
        jdbc.sql("delete from prompt_agente").update();
        jdbc.sql("delete from uso_ia").update();
        jdbc.sql("delete from corretor").update();
        contasCriadas.clear();
    }

    @Test
    void apiExigeSessao() throws Exception {
        assertThat(chamar("GET", "/api/prompt", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void navegadorDoPainelPodeChamarAApiMasOutrasOrigensNao() throws Exception {
        assertThat(preflight("http://localhost:3000").headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:3000");
        assertThat(preflight("http://site-qualquer.com").headers().firstValue("Access-Control-Allow-Origin"))
                .isEmpty();
    }

    private HttpResponse<String> preflight(String origem) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/conversas"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origem)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void contaNovaFicaAguardandoLiberacaoAteSerAtivada() throws Exception {
        assertThat(login("token-qualquer").statusCode()).isEqualTo(401);

        HttpResponse<String> primeira = login("token-de-outra-pessoa");
        assertThat(primeira.statusCode()).isEqualTo(403);
        assertThat(primeira.body()).contains("AGUARDANDO_LIBERACAO").doesNotContain("token");
        assertThat(jdbc.sql("select ativo from corretor where google_sub = :id").param("id", OUTRO)
                .query(Boolean.class).single()).isFalse();

        ativar(OUTRO);
        assertThat(login("token-de-outra-pessoa").statusCode()).isEqualTo(200);
    }

    @Test
    void corretorDesligadoPerdeOAcessoNaHora() throws Exception {
        String token = sessao();
        assertThat(chamar("GET", "/api/prompt", null, token).statusCode()).isEqualTo(200);

        jdbc.sql("update corretor set ativo = false where auth_id = :id").param("id", DONO).update();

        assertThat(chamar("GET", "/api/prompt", null, token).statusCode()).isEqualTo(401);
        assertThat(login("token-do-corretor").statusCode()).isEqualTo(403);
    }

    @Test
    void contaMostraNomeESituacaoDaAssinatura() throws Exception {
        String token = sessao();

        HttpResponse<String> ativa = chamar("GET", "/api/conta", null, token);
        assertThat(ativa.body()).contains("Corretor Teste").contains("\"estadoAssinatura\":\"ATIVA\"")
                .contains("\"iaAtiva\":true").contains("\"pausaPor\":null").contains("\"respostasNoMes\":0")
                .contains("\"limiteDoMes\":3000");

        jdbc.sql("update corretor set assinatura_ate = now() - interval '3 days' where auth_id = :id")
                .param("id", DONO).update();
        HttpResponse<String> vencida = chamar("GET", "/api/conta", null, token);
        assertThat(vencida.body()).contains("\"estadoAssinatura\":\"VENCIDA\"").contains("\"iaAtiva\":false")
                .contains("\"pausaPor\":\"ASSINATURA_VENCIDA\"");

        jdbc.sql("update corretor set assinatura_ate = now() + interval '2 days' where auth_id = :id")
                .param("id", DONO).update();
        assertThat(chamar("GET", "/api/conta", null, token).body()).contains("\"estadoAssinatura\":\"AVISO\"");

        jdbc.sql("update corretor set assinatura_ate = null, limite_respostas_mes = 0 where auth_id = :id")
                .param("id", DONO).update();
        assertThat(chamar("GET", "/api/conta", null, token).body()).contains("\"pausaPor\":\"LIMITE_MENSAL\"")
                .contains("\"limiteDoMes\":0");
    }

    @Test
    void corretorNaoVeNemAssumeNemLeConversaDeOutro() throws Exception {
        String tokenDono = sessao();
        conversas.receberDoCliente(DONO, "5511955554444", "Oi", "WA-I-1", Instant.now());
        UUID id = jdbc.sql("select id from conversa").query(UUID.class).single();
        String tokenOutro = sessaoDe("token-de-outra-pessoa", OUTRO);

        assertThat(chamar("GET", "/api/conversas", null, tokenOutro).body()).doesNotContain("5511955554444");
        assertThat(chamar("GET", "/api/conversas/" + id + "/mensagens", null, tokenOutro).statusCode()).isEqualTo(404);
        assertThat(chamar("POST", "/api/conversas/" + id + "/assumir", null, tokenOutro).statusCode()).isEqualTo(404);
        assertThat(chamar("POST", "/api/conversas/" + id + "/devolver", null, tokenOutro).statusCode()).isEqualTo(404);
        assertThat(chamar("POST", "/api/conversas/" + id + "/assumir", null, tokenDono).statusCode()).isEqualTo(200);
    }

    @Test
    void cadaCorretorTemOSeuPrompt() throws Exception {
        String tokenDono = sessao();
        String tokenOutro = sessaoDe("token-de-outra-pessoa", OUTRO);

        chamar("PUT", "/api/prompt", "{\"base\":\"Prompt só do primeiro.\"}", tokenDono);

        assertThat(chamar("GET", "/api/prompt", null, tokenDono).body()).contains("Prompt só do primeiro.");
        assertThat(chamar("GET", "/api/prompt", null, tokenOutro).body()).doesNotContain("Prompt só do primeiro.");
    }

    @Test
    void webhookDeInstanciaDesconhecidaEIgnorado() throws Exception {
        String corpo = "{\"event\":\"messages.upsert\",\"instance\":\"bizu-ninguem\",\"data\":{\"key\":"
                + "{\"remoteJid\":\"5511944443333@s.whatsapp.net\",\"fromMe\":false,\"id\":\"X1\"},"
                + "\"message\":{\"conversation\":\"Oi\"}}}";

        assertThat(webhook("teste", corpo).statusCode()).isEqualTo(200);

        assertThat(jdbc.sql("select count(*) from conversa").query(Integer.class).single()).isZero();
    }

    @Test
    void webhookDeCorretorDesligadoEIgnorado() throws Exception {
        ativar(DONO);
        jdbc.sql("update corretor set instancia = 'bizu-sub-corretor', ativo = false where auth_id = :id")
                .param("id", DONO).update();
        String corpo = "{\"event\":\"messages.upsert\",\"instance\":\"bizu-sub-corretor\",\"data\":{\"key\":"
                + "{\"remoteJid\":\"5511944443333@s.whatsapp.net\",\"fromMe\":false,\"id\":\"X3\"},"
                + "\"message\":{\"conversation\":\"Oi\"}}}";

        assertThat(webhook("teste", corpo).statusCode()).isEqualTo(200);

        assertThat(jdbc.sql("select count(*) from conversa").query(Integer.class).single()).isZero();
    }

    @Test
    void webhookDaInstanciaDoCorretorCriaAConversaDele() throws Exception {
        ativar(DONO);
        jdbc.sql("update corretor set instancia = 'bizu-sub-corretor' where auth_id = :id").param("id", DONO).update();
        String corpo = "{\"event\":\"messages.upsert\",\"instance\":\"bizu-sub-corretor\",\"data\":{\"key\":"
                + "{\"remoteJid\":\"5511944443333@s.whatsapp.net\",\"fromMe\":false,\"id\":\"X2\"},"
                + "\"message\":{\"conversation\":\"Oi\"}}}";

        assertThat(webhook("teste", corpo).statusCode()).isEqualTo(200);

        assertThat(jdbc.sql("select corretor_id from conversa").query(String.class).single()).isEqualTo(DONO);
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
        assertThat(prompts.base(DONO)).isEqualTo("Seja direto e simpático.");
        assertThat(chamar("GET", "/api/prompt", null, token).body()).contains("Seja direto e simpático.");
    }

    @Test
    void promptEnormeERecusado() throws Exception {
        String grande = "a".repeat(PromptController.TAMANHO_MAXIMO + 1);

        assertThat(chamar("PUT", "/api/prompt", "{\"base\":\"" + grande + "\"}", sessao()).statusCode())
                .isEqualTo(400);
    }

    @Test
    void tokenAssinadoComOutraChaveNaoVale() throws Exception {
        sessao(); // garante a conta ativa; o token forjado abaixo usa o mesmo sub
        var outroPar = java.security.KeyPairGenerator.getInstance("RSA");
        outroPar.initialize(2048);
        var par = outroPar.generateKeyPair();
        var jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey) par.getPublic())
                .privateKey(par.getPrivate()).keyID("outra").build();
        String forjado = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(
                new com.nimbusds.jose.jwk.JWKSet(jwk))).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("outra").build(),
                JwtClaimsSet.builder().issuer("bizu").subject(DONO)
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build())).getTokenValue();

        assertThat(chamar("GET", "/api/prompt", null, forjado).statusCode()).isEqualTo(401);
    }

    @Test
    void chavePublicaVerificaASessaoMasNaoTrazAPrivada() throws Exception {
        String token = sessao();

        HttpResponse<String> jwks = chamar("GET", "/.well-known/jwks.json", null, null);

        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(jwks.body()).contains("\"kty\":\"RSA\"").contains("\"kid\"")
                .doesNotContain("\"d\":").doesNotContain("\"p\":").doesNotContain("\"q\":");
        var conjunto = com.nimbusds.jose.jwk.JWKSet.parse(jwks.body());
        var publica = conjunto.getKeys().get(0).toRSAKey().toRSAPublicKey();
        var verificado = org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withPublicKey(publica).build().decode(token);
        assertThat(verificado.getSubject()).isEqualTo(DONO);
        assertThat(verificado.getClaimAsString("iss")).isEqualTo("bizu");
    }

    @Test
    void tokenHs256AssinadoComAChavePublicaNaoVale() throws Exception {
        sessao();
        var jwks = com.nimbusds.jose.jwk.JWKSet.parse(chamar("GET", "/.well-known/jwks.json", null, null).body());
        byte[] chavePublica = jwks.getKeys().get(0).toRSAKey().toRSAPublicKey().getEncoded();
        // Ataque clássico: usar a chave pública (que todos conhecem) como se fosse um segredo HS256.
        var segredo = new SecretKeySpec(java.util.Arrays.copyOf(chavePublica, 64), "HmacSHA256");
        String forjado = new NimbusJwtEncoder(new ImmutableSecret<>(segredo)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("bizu").subject(DONO)
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build())).getTokenValue();

        assertThat(chamar("GET", "/api/prompt", null, forjado).statusCode()).isEqualTo(401);
    }

    @Test
    void saudeEstaAbertaMasOutrosEndpointsDoActuatorNao() throws Exception {
        assertThat(chamar("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
        assertThat(chamar("GET", "/actuator/health", null, null).body()).contains("UP");
        assertThat(chamar("GET", "/actuator/env", null, null).statusCode()).isIn(401, 403, 404);
    }

    @Test
    void primeiroLoginCriaContaComCodigoProprioEGuardaOSubSeparadoEAvisaUmaVez() throws Exception {
        assertThat(login("token-de-outra-pessoa").statusCode()).isEqualTo(403);
        assertThat(login("token-de-outra-pessoa").statusCode()).isEqualTo(403);

        var conta = jdbc.sql("select auth_id, google_sub from corretor where google_sub = :s").param("s", OUTRO)
                .query().singleRow();
        assertThat(conta.get("google_sub")).isEqualTo(OUTRO);
        assertThat(conta.get("auth_id")).isNotEqualTo(OUTRO);
        assertThat(java.util.UUID.fromString((String) conta.get("auth_id"))).isNotNull();
        assertThat(jdbc.sql("select count(*) from corretor").query(Integer.class).single()).isEqualTo(1);
        assertThat(contasCriadas).hasSize(1);
        assertThat(contasCriadas.get(0).email()).isEqualTo("outra@exemplo.com");
    }

    @Test
    void contaAntigaQueUsavaOSubComoAuthIdContinuaValendoEGuardaOSub() throws Exception {
        // Conta de antes da separação das colunas: auth_id = sub e google_sub vazio.
        jdbc.sql("insert into corretor (auth_id, email, nome, ativo, criado_em) values (:id, 'a@b.c', 'Antiga', true, now())")
                .param("id", OUTRO).update();

        HttpResponse<String> resposta = login("token-de-outra-pessoa");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(jdbc.sql("select count(*) from corretor").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select google_sub from corretor where auth_id = :id").param("id", OUTRO)
                .query(String.class).single()).isEqualTo(OUTRO);
        assertThat(contasCriadas).isEmpty();
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
        ativar(DONO);
        jdbc.sql("update corretor set instancia = 'bizu-sub-corretor' where auth_id = :id").param("id", DONO).update();
        String corpo = "{\"event\":\"contacts.upsert\",\"instance\":\"bizu-sub-corretor\","
                + "\"data\":[{\"remoteJid\":\"5511977776666@s.whatsapp.net\"}]}";

        assertThat(webhook("teste", corpo).statusCode()).isEqualTo(200);

        assertThat(conversas.receberDoCliente(DONO, "5511977776666", "Oi", "WA-AGENDA-1", Instant.now()))
                .isEqualTo(Resultado.REGISTRADA);
        assertThat(jdbc.sql("select estado from conversa where telefone = '5511977776666'").query(String.class).single())
                .isEqualTo("SEM_IA");
        assertThat(conversas.receberDoCliente(DONO, "5511966665555", "Oi", "WA-NOVO-1", Instant.now()))
                .isEqualTo(Resultado.ENFILEIRADA);
    }

    @Test
    void importarTrazAsConversasDaSemanaParaOPainelEPodeRodarDeNovo() throws Exception {
        String token = sessao();

        HttpResponse<String> primeira = chamar("POST", "/api/conversas/importar", null, token);
        assertThat(primeira.statusCode()).isEqualTo(200);
        assertThat(primeira.body()).contains("\"conversas\":1").contains("\"mensagens\":2");

        HttpResponse<String> lista = chamar("GET", "/api/conversas", null, token);
        assertThat(lista.body()).contains("\"nome\":\"Cliente Importado\"").contains("5511933332222")
                .contains("\"ultimaMensagem\":\"[áudio]\"");
        UUID id = jdbc.sql("select id from conversa").query(UUID.class).single();
        assertThat(chamar("GET", "/api/conversas/" + id + "/mensagens", null, token).body())
                .contains("Oi, vi o anúncio").contains("[áudio]");

        assertThat(chamar("POST", "/api/conversas/importar", null, token).body()).contains("\"mensagens\":0");
    }

    @Test
    void painelDeConversasExigeSessao() throws Exception {
        assertThat(chamar("GET", "/api/conversas", null, null).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/conversas/" + UUID.randomUUID() + "/assumir", null, null).statusCode())
                .isEqualTo(401);
        assertThat(chamar("GET", "/api/whatsapp", null, null).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/whatsapp/conectar", null, null).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/conversas/importar", null, null).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/estilo/gerar", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void corretorVeAsConversasComAQualificacaoEAssumeEDevolve() throws Exception {
        String token = sessao();
        conversas.receberDoCliente(DONO, "5511988887777", "Quero comprar", "WA-P-1", Instant.now());
        jdbc.sql("""
                insert into lead (conversa_id, finalidade, regiao, faixa_valor, prazo, atualizado_em)
                select id, 'compra', 'Centro', 'até 500 mil', '3 meses', now() from conversa
                """).update();

        HttpResponse<String> lista = chamar("GET", "/api/conversas", null, token);

        assertThat(lista.statusCode()).isEqualTo(200);
        assertThat(lista.body()).contains("5511988887777").contains("BOT").contains("Centro").contains("3 meses");
        assertThat(lista.body()).contains("\"ultimaMensagem\":\"Quero comprar\"").contains("\"ultimaOrigem\":\"CLIENTE\"");
        Matcher id = Pattern.compile("\"id\"\s*:\s*\"([^\"]+)\"").matcher(lista.body());
        assertThat(id.find()).isTrue();

        HttpResponse<String> assumida = chamar("POST", "/api/conversas/" + id.group(1) + "/assumir", null, token);
        assertThat(assumida.statusCode()).isEqualTo(200);
        assertThat(assumida.body()).contains("HUMANO");
        assertThat(conversas.receberDoCliente(DONO, "5511988887777", "Alô", "WA-P-2", Instant.now()))
                .isEqualTo(Resultado.REGISTRADA);

        HttpResponse<String> devolvida = chamar("POST", "/api/conversas/" + id.group(1) + "/devolver", null, token);
        assertThat(devolvida.statusCode()).isEqualTo(200);
        assertThat(devolvida.body()).contains("BOT");
        assertThat(conversas.receberDoCliente(DONO, "5511988887777", "Voltei", "WA-P-3", Instant.now()))
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
    void corretorLeOHistoricoDaConversaEmOrdem() throws Exception {
        String token = sessao();
        Instant t = Instant.now();
        conversas.receberDoCliente(DONO, "5511977771111", "Primeira", "WA-H-1", t);
        conversas.receberDoCliente(DONO, "5511977771111", "Segunda", "WA-H-2", t.plusSeconds(1));
        UUID id = jdbc.sql("select id from conversa where telefone = '5511977771111'").query(UUID.class).single();

        assertThat(chamar("GET", "/api/conversas/" + id + "/mensagens", null, null).statusCode()).isEqualTo(401);
        HttpResponse<String> resposta = chamar("GET", "/api/conversas/" + id + "/mensagens", null, token);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("\"origem\":\"CLIENTE\"").contains("\"criadaEm\"");
        assertThat(resposta.body().indexOf("Primeira")).isPositive().isLessThan(resposta.body().indexOf("Segunda"));
        assertThat(chamar("GET", "/api/conversas/" + UUID.randomUUID() + "/mensagens", null, token).statusCode())
                .isEqualTo(404);
    }

    @Test
    void painelMostraOQrCodeParaConectarONumero() throws Exception {
        String token = sessao();

        HttpResponse<String> estado = chamar("GET", "/api/whatsapp", null, token);
        assertThat(estado.statusCode()).isEqualTo(200);
        assertThat(estado.body()).contains("\"conectado\":false").doesNotContain("QR").doesNotContain("ABCD-1234");

        HttpResponse<String> resposta = chamar("POST", "/api/whatsapp/conectar", null, token);
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
        return sessaoDe("token-do-corretor", DONO);
    }

    /** Libera a conta (como o dono do sistema faria) e entra com o token do Google correspondente. */
    private String sessaoDe(String idToken, String authId) throws Exception {
        ativar(authId);
        HttpResponse<String> resposta = login(idToken);
        assertThat(resposta.statusCode()).isEqualTo(200);
        Matcher m = TOKEN.matcher(resposta.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    /** Como o dono do sistema faria: libera a conta desse sub, e a cria como conta antiga se ainda não existir. */
    private void ativar(String sub) {
        int liberadas = jdbc.sql("update corretor set ativo = true where google_sub = :s or auth_id = :s")
                .param("s", sub).update();
        if (liberadas == 0) {
            jdbc.sql("insert into corretor (auth_id, email, nome, ativo, criado_em) values (:id, :e, :id, true, now())")
                    .param("id", sub).param("e", sub + "@exemplo.com").update();
        }
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
