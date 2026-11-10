package app.bizu.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversaTest {

    private static final int LIMITE = 20;
    private static final Instant T0 = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void conversaNovaComecaNoBotComContadorZero() {
        Conversa c = Conversa.nova("c1", "5511999990000", T0);

        assertThat(c.estado()).isEqualTo(Estado.BOT);
        assertThat(c.respostasIa()).isZero();
        assertThat(c.podeResponder(LIMITE)).isTrue();
    }

    @Test
    void vigesimaRespostaSaiENaSequenciaAConversaEncerra() {
        Conversa c = Conversa.nova("c1", "5511999990000", T0);

        for (int i = 1; i <= LIMITE; i++) {
            assertThat(c.podeResponder(LIMITE)).as("resposta %d deve poder sair", i).isTrue();
            c = c.aposRespostaIa(LIMITE, T0);
        }

        assertThat(c.respostasIa()).isEqualTo(LIMITE);
        assertThat(c.estado()).isEqualTo(Estado.ENCERRADA);
        assertThat(c.podeResponder(LIMITE)).isFalse();
    }

    @Test
    void assumirSilenciaAIA() {
        Conversa c = Conversa.nova("c1", "5511999990000", T0).assumida(T0);

        assertThat(c.estado()).isEqualTo(Estado.HUMANO);
        assertThat(c.podeResponder(LIMITE)).isFalse();
    }

    @Test
    void devolverNaoZeraOContador() {
        Conversa c = Conversa.nova("c1", "5511999990000", T0)
                .aposRespostaIa(LIMITE, T0)
                .aposRespostaIa(LIMITE, T0)
                .assumida(T0)
                .devolvida(LIMITE, T0);

        assertThat(c.estado()).isEqualTo(Estado.BOT);
        assertThat(c.respostasIa()).isEqualTo(2);
    }

    @Test
    void devolverConversaQueJaAtingiuOLimiteContinuaEncerrada() {
        Conversa c = Conversa.nova("c1", "5511999990000", T0);
        for (int i = 0; i < LIMITE; i++) {
            c = c.aposRespostaIa(LIMITE, T0);
        }

        Conversa devolvida = c.assumida(T0).devolvida(LIMITE, T0);

        assertThat(devolvida.estado()).isEqualTo(Estado.ENCERRADA);
        assertThat(devolvida.podeResponder(LIMITE)).isFalse();
    }

    @Test
    void expiraSoDepoisDeSeteDiasSemAtividade() {
        Conversa c = Conversa.nova("c1", "5511999990000", T0);
        Duration seteDias = Duration.ofDays(7);

        assertThat(c.expirada(T0.plus(Duration.ofDays(6)).plusSeconds(86_399), seteDias)).isFalse();
        assertThat(c.expirada(T0.plus(seteDias), seteDias)).isTrue();
    }
}
