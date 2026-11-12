package app.bizu.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import app.bizu.BizuApplication;
import app.bizu.conversa.Agente.Qualificacao;
import app.bizu.conversa.Agente.RespostaAgente;
import app.bizu.conversa.ConversaService.ContatoSalvo;
import app.bizu.conversa.ConversaService.Resultado;
import app.bizu.conversa.HistoricoDeConversas.ConversaImportada;
import app.bizu.conversa.HistoricoDeConversas.MensagemImportada;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Fluxo completo contra um Postgres real (Liquibase aplica o esquema). Ignorado se o Docker não estiver ativo. */
@ExtendWith(OutputCaptureExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = {BizuApplication.class, FluxoDeConversaIT.Dublês.class},
        properties = {
                "bizu.conversa.janela-agrupamento=0s",
                "bizu.conversa.sondagem-fila=1h",
                "bizu.webhook-segredo=teste",
                "bizu.painel.jwt-chave-efemera=true"
        })
class FluxoDeConversaIT {

    private static final Instant T0 = Instant.parse("2026-11-06T12:00:00Z");
    private static final String CLIENTE = "5511999990001";
    private static final String C1 = "corretor-1";
    private static final String C2 = "corretor-2";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final List<String> enviados = java.util.Collections.synchronizedList(new ArrayList<>());
    static final List<String> donosDosEnvios = java.util.Collections.synchronizedList(new ArrayList<>());
    static RespostaAgente proximaResposta;
    static RuntimeException falhaDoAgente;
    /** Roda dentro da chamada da IA: simula o que acontece enquanto o Claude ainda está pensando. */
    static volatile Runnable duranteOAgente;

    @TestConfiguration
    static class Dublês {
        private final AtomicInteger ids = new AtomicInteger();

        @Bean
        @Primary
        Canal canalFalso() {
            return (corretorId, telefone, texto) -> {
                enviados.add(telefone + ":" + texto);
                donosDosEnvios.add(corretorId);
                return "WA-IA-" + ids.incrementAndGet();
            };
        }

        @Bean
        @Primary
        Agente agenteFalso() {
            return contexto -> {
                if (duranteOAgente != null) {
                    duranteOAgente.run();
                }
                if (falhaDoAgente != null) {
                    throw falhaDoAgente;
                }
                return proximaResposta != null ? proximaResposta : new RespostaAgente("Resposta", null, false);
            };
        }
    }

    @Autowired ConversaService service;
    @Autowired ProcessadorDeFila processador;
    @Autowired JdbcClient jdbc;

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
        for (String id : List.of(C1, C2)) {
            jdbc.sql("insert into corretor (auth_id, email, nome, ativo, criado_em) values (:id, :e, :id, true, now())")
                    .param("id", id).param("e", id + "@exemplo.com").update();
        }
        enviados.clear();
        donosDosEnvios.clear();
        proximaResposta = null;
        falhaDoAgente = null;
        duranteOAgente = null;
    }

    @Test
    void corretorAssumeEnquantoAIAPensaEARespostaNaoEEnviada() {
        service.receberDoCliente(C1, CLIENTE, "Olá", "WA-1", T0);
        UUID id = jdbc.sql("select id from conversa").query(UUID.class).single();
        // A conversa não pode estar travada durante a chamada da IA: o painel consegue assumir sem esperar.
        duranteOAgente = () -> service.assumir(C1, id, T0);

        processador.processarProxima(T0.plusSeconds(1));

        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
        assertThat(respostasIa(CLIENTE)).isZero();
        assertThat(jdbc.sql("select count(*) from fila_mensagem").query(Integer.class).single()).isZero();
    }

    @Test
    void clienteNovoGeraConversaNoBotEUmaRespostaDaIA() {
        assertThat(service.receberDoCliente(C1, CLIENTE, "Olá", "WA-1", T0)).isEqualTo(Resultado.ENFILEIRADA);

        assertThat(processador.processarProxima(T0.plusSeconds(1))).isTrue();

        assertThat(enviados).containsExactly(CLIENTE + ":Resposta");
        assertThat(respostasIa(CLIENTE)).isEqualTo(1);
        assertThat(processador.processarProxima(T0.plusSeconds(2))).isFalse();
    }

    @Test
    void rajadaDeMensagensGeraUmaRespostaSo() {
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);
        service.receberDoCliente(C1, CLIENTE, "Tudo bem?", "WA-2", T0);
        service.receberDoCliente(C1, CLIENTE, "Quero comprar", "WA-3", T0);

        while (processador.processarProxima(T0.plusSeconds(1))) {
            // esvazia
        }

        assertThat(enviados).hasSize(1);
    }

    @Test
    void webhookRepetidoNaoDuplicaMensagem() {
        service.receberDoCliente(C1, CLIENTE, "Olá", "WA-1", T0);

        assertThat(service.receberDoCliente(C1, CLIENTE, "Olá", "WA-1", T0)).isEqualTo(Resultado.DUPLICADA);
    }

    @Test
    void depoisDaVigesimaRespostaAIAFicaEmSilencio() {
        for (int i = 1; i <= 25; i++) {
            Instant t = T0.plusSeconds(i * 10L);
            service.receberDoCliente(C1, CLIENTE, "Mensagem " + i, "WA-C-" + i, t);
            processador.processarProxima(t.plusSeconds(1));
        }

        assertThat(enviados).hasSize(20);
        assertThat(estado(CLIENTE)).isEqualTo("ENCERRADA");
    }

    @Test
    void depoisDeSeteDiasSemMensagensComecaNovaConversaComMaisVinte() {
        for (int i = 1; i <= 20; i++) {
            Instant t = T0.plusSeconds(i * 10L);
            service.receberDoCliente(C1, CLIENTE, "Mensagem " + i, "WA-C-" + i, t);
            processador.processarProxima(t.plusSeconds(1));
        }
        enviados.clear();

        Instant depois = T0.plus(Duration.ofDays(8));
        assertThat(service.receberDoCliente(C1, CLIENTE, "Voltei", "WA-VOLTA", depois)).isEqualTo(Resultado.ENFILEIRADA);
        processador.processarProxima(depois.plusSeconds(1));

        assertThat(enviados).hasSize(1);
        assertThat(respostasIa(CLIENTE)).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from conversa where telefone = :t").param("t", CLIENTE)
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void ecoDaPropriaRespostaDaIANaoFazOCorretorAssumir() {
        service.receberDoCliente(C1, CLIENTE, "Olá", "WA-1", T0);
        processador.processarProxima(T0.plusSeconds(1));

        Resultado eco = service.receberDoCorretor(C1, CLIENTE, "Resposta", "WA-IA-1", T0.plusSeconds(2));

        assertThat(eco).isEqualTo(Resultado.ECO);
        assertThat(estado(CLIENTE)).isEqualTo("BOT");
    }

    @Test
    void mensagemManualDoCorretorSilenciaAIAEExcluiONumeroParaSempre() {
        service.receberDoCliente(C1, CLIENTE, "Olá", "WA-1", T0);
        processador.processarProxima(T0.plusSeconds(1));
        enviados.clear();

        Resultado manual = service.receberDoCorretor(C1, CLIENTE, "Oi, aqui é o corretor", "WA-M-1", T0.plusSeconds(5));
        assertThat(manual).isEqualTo(Resultado.ASSUMIDA);
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");

        // A conversa continua aparecendo no painel, mas a IA nunca volta sozinha.
        assertThat(service.receberDoCliente(C1, CLIENTE, "Obrigado", "WA-2", T0.plusSeconds(10)))
                .isEqualTo(Resultado.REGISTRADA);
        Instant muitoDepois = T0.plus(Duration.ofDays(30));
        assertThat(service.receberDoCliente(C1, CLIENTE, "Voltei", "WA-3", muitoDepois)).isEqualTo(Resultado.REGISTRADA);
        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
    }

    @Test
    void contatoSalvoApareceNoPainelMasNuncaEAtendidoPelaIA() {
        service.marcarContatosSalvos(C1, List.of(new ContatoSalvo(CLIENTE, "Mãe")), T0);

        assertThat(service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0)).isEqualTo(Resultado.REGISTRADA);
        while (processador.processarProxima(T0.plusSeconds(1))) {
            // esvazia
        }

        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("SEM_IA");
        assertThat(service.listar(C1)).singleElement().satisfies(r -> assertThat(r.nome()).isEqualTo("Mãe"));
    }

    @Test
    void passarAoCorretorSilenciaSemEnviarNada() {
        proximaResposta = RespostaAgente.passar();
        service.receberDoCliente(C1, CLIENTE, "Quero falar com o corretor", "WA-1", T0);

        processador.processarProxima(T0.plusSeconds(1));

        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
    }

    @Test
    void qualificacaoDoAgenteFicaGravadaNoLead() {
        proximaResposta = new RespostaAgente("Entendi", new Qualificacao("compra", "Centro", null, "3 meses", null), false);
        service.receberDoCliente(C1, CLIENTE, "Quero comprar no centro", "WA-1", T0);

        processador.processarProxima(T0.plusSeconds(1));

        var lead = jdbc.sql("select finalidade, regiao, prazo from lead").query().singleRow();
        assertThat(lead).containsEntry("finalidade", "compra").containsEntry("regiao", "Centro")
                .containsEntry("prazo", "3 meses");
    }

    @Test
    void conversaPassadaAoCorretorNaoVoltaParaAIADepoisDeSeteDias() {
        proximaResposta = RespostaAgente.passar();
        service.receberDoCliente(C1, CLIENTE, "Quero falar com o corretor", "WA-1", T0);
        processador.processarProxima(T0.plusSeconds(1));
        proximaResposta = null;

        Instant depois = T0.plus(Duration.ofDays(8));
        assertThat(service.receberDoCliente(C1, CLIENTE, "Alô?", "WA-2", depois)).isEqualTo(Resultado.REGISTRADA);

        assertThat(processador.processarProxima(depois.plusSeconds(1))).isFalse();
        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
    }

    @Test
    void filaDescartadaDepoisDasTentativasDeixaLogDeErroComAConversa(CapturedOutput saida) {
        falhaDoAgente = new IllegalStateException("Claude fora do ar");
        service.receberDoCliente(C1, CLIENTE, "Olá", "WA-1", T0);
        String conversaId = jdbc.sql("select id from conversa").query(String.class).single();

        Instant t = T0.plusSeconds(1);
        for (int i = 0; i < 3; i++) {
            try {
                processador.processarProxima(t);
            } catch (ProcessadorDeFila.FalhaNoProcessamento falha) {
                processador.registrarFalha(falha.conversaId(), t);
            }
            t = t.plusSeconds(60);
        }

        assertThat(jdbc.sql("select count(*) from fila_mensagem").query(Integer.class).single()).isZero();
        assertThat(saida.getOut()).contains("descartada").contains(conversaId);
    }

    @Test
    void corretoresDiferentesTemConversasSeparadasParaOMesmoNumero() {
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-C1", T0);
        service.receberDoCliente(C2, CLIENTE, "Oi", "WA-C2", T0);

        while (processador.processarProxima(T0.plusSeconds(1))) {
            // esvazia
        }

        assertThat(jdbc.sql("select count(*) from conversa").query(Integer.class).single()).isEqualTo(2);
        assertThat(donosDosEnvios).containsExactlyInAnyOrder(C1, C2);
    }

    @Test
    void contatoIgnoradoDeUmCorretorNaoAfetaOOutro() {
        service.marcarContatosSalvos(C1, List.of(new ContatoSalvo(CLIENTE, null)), T0);

        assertThat(service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0)).isEqualTo(Resultado.REGISTRADA);
        assertThat(service.receberDoCliente(C2, CLIENTE, "Oi", "WA-2", T0)).isEqualTo(Resultado.ENFILEIRADA);
    }

    @Test
    void corretorNaoLeNemAssumeNemDevolveConversaDeOutro() {
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);
        UUID id = jdbc.sql("select id from conversa").query(UUID.class).single();

        assertThat(service.mensagens(C2, id)).isEmpty();
        assertThat(service.assumir(C2, id, T0)).isEmpty();
        assertThat(service.devolver(C2, id, T0)).isEmpty();
        assertThat(service.listar(C2)).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("BOT");
        assertThat(service.mensagens(C1, id)).isPresent();
    }

    @Test
    void assinaturaVencidaAlemDaCarenciaPausaAIASemMudarAConversa() {
        jdbc.sql("update corretor set assinatura_ate = :t where auth_id = :id")
                .param("t", java.time.OffsetDateTime.ofInstant(T0.minus(Duration.ofDays(2)), java.time.ZoneOffset.UTC))
                .param("id", C1).update();
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);

        processador.processarProxima(T0.plusSeconds(1));

        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("BOT");
        assertThat(jdbc.sql("select count(*) from fila_mensagem").query(Integer.class).single()).isZero();

        // Renovou: a IA volta quando o cliente escrever de novo.
        jdbc.sql("update corretor set assinatura_ate = null where auth_id = :id").param("id", C1).update();
        service.receberDoCliente(C1, CLIENTE, "Alô?", "WA-2", T0.plusSeconds(10));
        processador.processarProxima(T0.plusSeconds(11));

        assertThat(enviados).hasSize(1);
    }

    @Test
    void naCarenciaDe24HAIAAindaResponde() {
        jdbc.sql("update corretor set assinatura_ate = :t where auth_id = :id")
                .param("t", java.time.OffsetDateTime.ofInstant(T0.minus(Duration.ofHours(12)), java.time.ZoneOffset.UTC))
                .param("id", C1).update();
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);

        processador.processarProxima(T0.plusSeconds(1));

        assertThat(enviados).hasSize(1);
    }

    @Test
    void contaDesligadaPausaAIAImediatamente() {
        jdbc.sql("update corretor set ativo = false where auth_id = :id").param("id", C1).update();
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);

        processador.processarProxima(T0.plusSeconds(1));

        assertThat(enviados).isEmpty();
    }

    @Test
    void tetoMensalPausaAIASemMudarAConversaEZeraNoMesSeguinte() {
        jdbc.sql("update corretor set limite_respostas_mes = 1 where auth_id = :id").param("id", C1).update();
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);
        service.receberDoCliente(C1, "5511988880002", "Oi", "WA-2", T0);

        while (processador.processarProxima(T0.plusSeconds(1))) {
            // esvazia
        }

        assertThat(enviados).hasSize(1);
        assertThat(jdbc.sql("select count(*) from conversa where estado = 'BOT'").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select sum(respostas) from uso_ia where mes = '2026-11'").query(Integer.class).single()).isEqualTo(1);

        // Mês seguinte: o contador é outro e a IA volta a responder.
        Instant dezembro = T0.plus(Duration.ofDays(30));
        service.receberDoCliente(C1, "5511988880002", "Voltei", "WA-3", dezembro);
        processador.processarProxima(dezembro.plusSeconds(1));

        assertThat(enviados).hasSize(2);
    }

    @Test
    void cadaRespostaDaIASomaNoUsoDoMes() {
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);
        service.receberDoCliente(C1, "5511988880002", "Oi", "WA-2", T0);

        while (processador.processarProxima(T0.plusSeconds(1))) {
            // esvazia
        }

        assertThat(jdbc.sql("select respostas from uso_ia where corretor_id = :id and mes = '2026-11'")
                .param("id", C1).query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void duasConversasSaoRespondidasAoMesmoTempo() throws Exception {
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);
        service.receberDoCliente(C2, CLIENTE, "Oi", "WA-2", T0);
        // Cada IA só termina quando as duas estão rodando: se o processamento fosse um por vez, a espera estouraria.
        java.util.concurrent.CountDownLatch encontro = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicBoolean simultaneas = new java.util.concurrent.atomic.AtomicBoolean(true);
        duranteOAgente = () -> {
            encontro.countDown();
            try {
                if (!encontro.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    simultaneas.set(false);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> processador.processarProxima(T0.plusSeconds(1)));
            var b = pool.submit(() -> processador.processarProxima(T0.plusSeconds(1)));
            assertThat(a.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(b.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(simultaneas).isTrue();
        assertThat(donosDosEnvios).containsExactlyInAnyOrder(C1, C2);
    }

    @Test
    void corretorEscreveuAntesDoClienteAConversaApareceComEleENuncaVoltaParaAIA() {
        assertThat(service.receberDoCorretor(C1, CLIENTE, "Oi, tudo bem?", "WA-M-1", T0)).isEqualTo(Resultado.ASSUMIDA);

        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
        assertThat(service.receberDoCliente(C1, CLIENTE, "Tudo ótimo", "WA-2", T0.plusSeconds(5))).isEqualTo(Resultado.REGISTRADA);
        UUID id = jdbc.sql("select id from conversa").query(UUID.class).single();
        assertThat(service.mensagens(C1, id).orElseThrow()).extracting(m -> m.origem()).containsExactly("CORRETOR", "CLIENTE");
        assertThat(enviados).isEmpty();
    }

    @Test
    void mensagemDoCorretorParaContatoSalvoNaoViraConversaComEle() {
        service.marcarContatosSalvos(C1, List.of(new ContatoSalvo(CLIENTE, null)), T0);

        service.receberDoCorretor(C1, CLIENTE, "Oi, mãe", "WA-M-1", T0.plusSeconds(1));

        assertThat(estado(CLIENTE)).isEqualTo("SEM_IA");
        assertThat(jdbc.sql("select count(*) from mensagem").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void contatoSoComCodigoLidApareceComONomeMasAIANuncaResponde() {
        assertThat(service.receberDoCliente(C1, "98765@lid", "Fulano", "Oi", "WA-1", T0)).isEqualTo(Resultado.REGISTRADA);

        while (processador.processarProxima(T0.plusSeconds(1))) {
            // esvazia
        }

        assertThat(enviados).isEmpty();
        assertThat(service.listar(C1)).singleElement().satisfies(r -> {
            assertThat(r.estado()).isEqualTo(Estado.SEM_IA);
            assertThat(r.nome()).isEqualTo("Fulano");
        });
    }

    @Test
    void mensagemQueChegouAntesDaAgendaPassaASemIaQuandoAAgendaChega() {
        assertThat(service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0)).isEqualTo(Resultado.ENFILEIRADA);

        service.marcarContatosSalvos(C1, List.of(new ContatoSalvo(CLIENTE, null)), T0);
        processador.processarProxima(T0.plusSeconds(1));

        assertThat(estado(CLIENTE)).isEqualTo("SEM_IA");
        assertThat(enviados).isEmpty();
    }

    @Test
    void corretorNaoAssumeNemDevolveConversaSemIA() {
        service.marcarContatosSalvos(C1, List.of(new ContatoSalvo(CLIENTE, null)), T0);
        service.receberDoCliente(C1, CLIENTE, "Oi", "WA-1", T0);
        UUID id = jdbc.sql("select id from conversa").query(UUID.class).single();

        assertThat(service.assumir(C1, id, T0)).isEmpty();
        assertThat(service.devolver(C1, id, T0)).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("SEM_IA");
    }

    private static List<ConversaImportada> historicoDaSemana() {
        Instant ontem = T0.minus(Duration.ofDays(1));
        return List.of(
                // número novo, só o cliente falou: continua com a IA para a próxima mensagem
                new ConversaImportada("5511900000001", "Cliente Novo", "https://pps.whatsapp.net/foto.jpg", false,
                        List.of(new MensagemImportada("H-1", false, "Vi o anúncio", ontem),
                                new MensagemImportada("H-2", false, "[áudio]", ontem.plusSeconds(60)))),
                // número novo que o corretor já atendeu: fica com ele
                new ConversaImportada("5511900000002", null, null, false,
                        List.of(new MensagemImportada("H-3", false, "Oi", ontem),
                                new MensagemImportada("H-4", true, "Olá, tudo bem?", ontem.plusSeconds(120)))),
                // contato salvo: aparece, sem IA
                new ConversaImportada("5511900000003", "Mãe", null, true,
                        List.of(new MensagemImportada("H-5", false, "Bom dia", ontem))),
                // só código do WhatsApp, sem telefone: aparece, sem IA
                new ConversaImportada("55555@lid", "Sem Número", null, false,
                        List.of(new MensagemImportada("H-6", false, "Oi", ontem))));
    }

    @Test
    void importacaoGravaComADataOriginalSemMexerNaFilaENaoFazAIAResponderRetroativamente() {
        var resumo = service.importar(C1, historicoDaSemana(), T0);

        assertThat(resumo.conversas()).isEqualTo(4);
        assertThat(resumo.mensagens()).isEqualTo(6);
        assertThat(jdbc.sql("select count(*) from fila_mensagem").query(Integer.class).single()).isZero();
        assertThat(estado("5511900000001")).isEqualTo("BOT");
        assertThat(estado("5511900000002")).isEqualTo("HUMANO");
        assertThat(estado("5511900000003")).isEqualTo("SEM_IA");
        assertThat(estado("55555@lid")).isEqualTo("SEM_IA");
        assertThat(jdbc.sql("select motivo from contato_ignorado where telefone = '5511900000003'")
                .query(String.class).single()).isEqualTo("AGENDA");
        assertThat(jdbc.sql("select motivo from contato_ignorado where telefone = '5511900000002'")
                .query(String.class).single()).isEqualTo("ESCRITA_MANUAL");
        // a data da conversa é a da última mensagem, e não a de agora
        assertThat(jdbc.sql("select ultima_atividade_em = :t from conversa where telefone = '5511900000001'")
                .param("t", java.time.OffsetDateTime.ofInstant(T0.minus(Duration.ofDays(1)).plusSeconds(60), java.time.ZoneOffset.UTC))
                .query(Boolean.class).single()).isTrue();
        assertThat(processador.processarProxima(T0.plusSeconds(1))).isFalse();
        assertThat(enviados).isEmpty();
        assertThat(service.listar(C1)).extracting(r -> r.nome()).contains("Cliente Novo", "Mãe", "Sem Número");
        assertThat(service.listar(C1)).filteredOn(r -> "Cliente Novo".equals(r.nome()))
                .extracting(r -> r.fotoUrl()).containsExactly("https://pps.whatsapp.net/foto.jpg");
        assertThat(service.listar(C1)).filteredOn(r -> "Mãe".equals(r.nome()))
                .extracting(r -> r.fotoUrl()).containsOnlyNulls();
    }

    @Test
    void importarDeNovoNaoRepeteNada() {
        service.importar(C1, historicoDaSemana(), T0);

        var denovo = service.importar(C1, historicoDaSemana(), T0.plusSeconds(30));

        assertThat(denovo.mensagens()).isZero();
        assertThat(jdbc.sql("select count(*) from mensagem").query(Integer.class).single()).isEqualTo(6);
        assertThat(jdbc.sql("select count(*) from conversa").query(Integer.class).single()).isEqualTo(4);
    }

    @Test
    void depoisDeImportadaAConversaNovaDoClienteFalaComAIA() {
        service.importar(C1, historicoDaSemana(), T0);

        assertThat(service.receberDoCliente(C1, "5511900000001", "Ainda está disponível?", "WA-NOVA", T0.plusSeconds(10)))
                .isEqualTo(Resultado.ENFILEIRADA);
        processador.processarProxima(T0.plusSeconds(20));

        assertThat(enviados).hasSize(1);
    }

    private int respostasIa(String telefone) {
        return jdbc.sql("select respostas_ia from conversa where telefone = :t and ativa")
                .param("t", telefone).query(Integer.class).single();
    }

    private String estado(String telefone) {
        return jdbc.sql("select estado from conversa where telefone = :t and ativa")
                .param("t", telefone).query(String.class).single();
    }
}
