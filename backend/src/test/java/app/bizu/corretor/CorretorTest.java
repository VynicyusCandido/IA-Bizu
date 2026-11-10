package app.bizu.corretor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CorretorTest {

    private static final Instant VENCE = Instant.parse("2026-12-01T12:00:00Z");

    private static Corretor com(boolean ativo, Instant assinaturaAte) {
        return new Corretor("id", "sub", "a@b.c", "Nome", ativo, assinaturaAte, null, null, VENCE, null);
    }

    @Test
    void assinaturaPassaPorAvisoCarenciaEVencida() {
        Corretor c = com(true, VENCE);

        assertThat(c.estadoAssinatura(VENCE.minus(Duration.ofDays(10)))).isEqualTo(EstadoAssinatura.ATIVA);
        assertThat(c.estadoAssinatura(VENCE.minus(Duration.ofHours(72)).plusSeconds(1))).isEqualTo(EstadoAssinatura.AVISO);
        assertThat(c.estadoAssinatura(VENCE.plusSeconds(1))).isEqualTo(EstadoAssinatura.CARENCIA);
        assertThat(c.estadoAssinatura(VENCE.plus(Duration.ofHours(24)))).isEqualTo(EstadoAssinatura.CARENCIA);
        assertThat(c.estadoAssinatura(VENCE.plus(Duration.ofHours(24)).plusSeconds(1))).isEqualTo(EstadoAssinatura.VENCIDA);
    }

    @Test
    void semDataDeVencimentoNuncaVence() {
        assertThat(com(true, null).estadoAssinatura(VENCE.plus(Duration.ofDays(3650)))).isEqualTo(EstadoAssinatura.ATIVA);
    }

    @Test
    void iaFicaLiberadaAteOFimDaCarenciaESoParaContaAtiva() {
        assertThat(com(true, VENCE).iaLiberada(VENCE.plus(Duration.ofHours(23)))).isTrue();
        assertThat(com(true, VENCE).iaLiberada(VENCE.plus(Duration.ofHours(25)))).isFalse();
        assertThat(com(false, null).iaLiberada(VENCE)).isFalse();
    }
}
