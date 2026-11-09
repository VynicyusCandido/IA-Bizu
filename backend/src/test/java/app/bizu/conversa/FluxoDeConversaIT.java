package app.bizu.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import app.bizu.BizuApplication;
import app.bizu.conversa.Agente.Qualificacao;
import app.bizu.conversa.Agente.RespostaAgente;
import app.bizu.conversa.ConversaService.Resultado;
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
                "bizu.painel.jwt-segredo=segredo-de-teste-com-mais-de-32-caracteres"
        })
class FluxoDeConversaIT {

    private static final Instant T0 = Instant.parse("2026-11-06T12:00:00Z");
    private static final String CLIENTE = "5511999990001";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final List<String> enviados = new ArrayList<>();
    static RespostaAgente proximaResposta;
    static RuntimeException falhaDoAgente;
    /** Roda dentro da chamada da IA: simula o que acontece enquanto o Claude ainda está pensando. */
    static Runnable duranteOAgente;

    @TestConfiguration
    static class Dublês {
        private final AtomicInteger ids = new AtomicInteger();

        @Bean
        @Primary
        Canal canalFalso() {
            return (telefone, texto) -> {
                enviados.add(telefone + ":" + texto);
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
        enviados.clear();
        proximaResposta = null;
        falhaDoAgente = null;
        duranteOAgente = null;
    }

    @Test
    void corretorAssumeEnquantoAIAPensaEARespostaNaoEEnviada() {
        service.receberDoCliente(CLIENTE, "Olá", "WA-1", T0);
        UUID id = jdbc.sql("select id from conversa").query(UUID.class).single();
        // A conversa não pode estar travada durante a chamada da IA: o painel consegue assumir sem esperar.
        duranteOAgente = () -> service.assumir(id, T0);

        processador.processarProxima(T0.plusSeconds(1));

        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
        assertThat(respostasIa(CLIENTE)).isZero();
        assertThat(jdbc.sql("select count(*) from fila_mensagem").query(Integer.class).single()).isZero();
    }

    @Test
    void clienteNovoGeraConversaNoBotEUmaRespostaDaIA() {
        assertThat(service.receberDoCliente(CLIENTE, "Olá", "WA-1", T0)).isEqualTo(Resultado.ENFILEIRADA);

        assertThat(processador.processarProxima(T0.plusSeconds(1))).isTrue();

        assertThat(enviados).containsExactly(CLIENTE + ":Resposta");
        assertThat(respostasIa(CLIENTE)).isEqualTo(1);
        assertThat(processador.processarProxima(T0.plusSeconds(2))).isFalse();
    }

    @Test
    void rajadaDeMensagensGeraUmaRespostaSo() {
        service.receberDoCliente(CLIENTE, "Oi", "WA-1", T0);
        service.receberDoCliente(CLIENTE, "Tudo bem?", "WA-2", T0);
        service.receberDoCliente(CLIENTE, "Quero comprar", "WA-3", T0);

        while (processador.processarProxima(T0.plusSeconds(1))) {
            // esvazia
        }

        assertThat(enviados).hasSize(1);
    }

    @Test
    void webhookRepetidoNaoDuplicaMensagem() {
        service.receberDoCliente(CLIENTE, "Olá", "WA-1", T0);

        assertThat(service.receberDoCliente(CLIENTE, "Olá", "WA-1", T0)).isEqualTo(Resultado.DUPLICADA);
    }

    @Test
    void depoisDaVigesimaRespostaAIAFicaEmSilencio() {
        for (int i = 1; i <= 25; i++) {
            Instant t = T0.plusSeconds(i * 10L);
            service.receberDoCliente(CLIENTE, "Mensagem " + i, "WA-C-" + i, t);
            processador.processarProxima(t.plusSeconds(1));
        }

        assertThat(enviados).hasSize(20);
        assertThat(estado(CLIENTE)).isEqualTo("ENCERRADA");
    }

    @Test
    void depoisDeSeteDiasSemMensagensComecaNovaConversaComMaisVinte() {
        for (int i = 1; i <= 20; i++) {
            Instant t = T0.plusSeconds(i * 10L);
            service.receberDoCliente(CLIENTE, "Mensagem " + i, "WA-C-" + i, t);
            processador.processarProxima(t.plusSeconds(1));
        }
        enviados.clear();

        Instant depois = T0.plus(Duration.ofDays(8));
        assertThat(service.receberDoCliente(CLIENTE, "Voltei", "WA-VOLTA", depois)).isEqualTo(Resultado.ENFILEIRADA);
        processador.processarProxima(depois.plusSeconds(1));

        assertThat(enviados).hasSize(1);
        assertThat(respostasIa(CLIENTE)).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from conversa where telefone = :t").param("t", CLIENTE)
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void ecoDaPropriaRespostaDaIANaoFazOCorretorAssumir() {
        service.receberDoCliente(CLIENTE, "Olá", "WA-1", T0);
        processador.processarProxima(T0.plusSeconds(1));

        Resultado eco = service.receberDoCorretor(CLIENTE, "Resposta", "WA-IA-1", T0.plusSeconds(2));

        assertThat(eco).isEqualTo(Resultado.ECO);
        assertThat(estado(CLIENTE)).isEqualTo("BOT");
    }

    @Test
    void mensagemManualDoCorretorSilenciaAIAEExcluiONumeroParaSempre() {
        service.receberDoCliente(CLIENTE, "Olá", "WA-1", T0);
        processador.processarProxima(T0.plusSeconds(1));
        enviados.clear();

        Resultado manual = service.receberDoCorretor(CLIENTE, "Oi, aqui é o corretor", "WA-M-1", T0.plusSeconds(5));
        assertThat(manual).isEqualTo(Resultado.ASSUMIDA);
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");

        assertThat(service.receberDoCliente(CLIENTE, "Obrigado", "WA-2", T0.plusSeconds(10)))
                .isEqualTo(Resultado.IGNORADA);
        Instant muitoDepois = T0.plus(Duration.ofDays(30));
        assertThat(service.receberDoCliente(CLIENTE, "Voltei", "WA-3", muitoDepois)).isEqualTo(Resultado.IGNORADA);
        assertThat(enviados).isEmpty();
    }

    @Test
    void contatoSalvoNuncaEAtendido() {
        service.marcarContatoSalvo(CLIENTE, T0);

        assertThat(service.receberDoCliente(CLIENTE, "Oi", "WA-1", T0)).isEqualTo(Resultado.IGNORADA);
        assertThat(jdbc.sql("select count(*) from conversa").query(Integer.class).single()).isZero();
    }

    @Test
    void passarAoCorretorSilenciaSemEnviarNada() {
        proximaResposta = RespostaAgente.passar();
        service.receberDoCliente(CLIENTE, "Quero falar com o corretor", "WA-1", T0);

        processador.processarProxima(T0.plusSeconds(1));

        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
    }

    @Test
    void qualificacaoDoAgenteFicaGravadaNoLead() {
        proximaResposta = new RespostaAgente("Entendi", new Qualificacao("compra", "Centro", null, "3 meses", null), false);
        service.receberDoCliente(CLIENTE, "Quero comprar no centro", "WA-1", T0);

        processador.processarProxima(T0.plusSeconds(1));

        var lead = jdbc.sql("select finalidade, regiao, prazo from lead").query().singleRow();
        assertThat(lead).containsEntry("finalidade", "compra").containsEntry("regiao", "Centro")
                .containsEntry("prazo", "3 meses");
    }

    @Test
    void conversaPassadaAoCorretorNaoVoltaParaAIADepoisDeSeteDias() {
        proximaResposta = RespostaAgente.passar();
        service.receberDoCliente(CLIENTE, "Quero falar com o corretor", "WA-1", T0);
        processador.processarProxima(T0.plusSeconds(1));
        proximaResposta = null;

        Instant depois = T0.plus(Duration.ofDays(8));
        assertThat(service.receberDoCliente(CLIENTE, "Alô?", "WA-2", depois)).isEqualTo(Resultado.REGISTRADA);

        assertThat(processador.processarProxima(depois.plusSeconds(1))).isFalse();
        assertThat(enviados).isEmpty();
        assertThat(estado(CLIENTE)).isEqualTo("HUMANO");
    }

    @Test
    void filaDescartadaDepoisDasTentativasDeixaLogDeErroComAConversa(CapturedOutput saida) {
        falhaDoAgente = new IllegalStateException("Claude fora do ar");
        service.receberDoCliente(CLIENTE, "Olá", "WA-1", T0);
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

    private int respostasIa(String telefone) {
        return jdbc.sql("select respostas_ia from conversa where telefone = :t and ativa")
                .param("t", telefone).query(Integer.class).single();
    }

    private String estado(String telefone) {
        return jdbc.sql("select estado from conversa where telefone = :t and ativa")
                .param("t", telefone).query(String.class).single();
    }
}
