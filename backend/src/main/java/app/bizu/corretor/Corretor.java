package app.bizu.corretor;

import java.time.Duration;
import java.time.Instant;

/**
 * Conta de um corretor. {@code authId} é o código próprio do Bizu (nunca muda) e {@code googleSub} o
 * identificador dele no Google. {@code assinaturaAte} nulo = sem vencimento. {@code limiteRespostasMes} nulo =
 * usa o padrão da aplicação. {@code instancia} é o nome da instância dele na Evolution (nulo até conectar).
 */
public record Corretor(
        String authId,
        String googleSub,
        String email,
        String nome,
        boolean ativo,
        Instant assinaturaAte,
        Integer limiteRespostasMes,
        String instancia,
        Instant criadoEm,
        Instant ultimoLoginEm) {

    /** Avisa o corretor 72 h antes de vencer. */
    static final Duration ANTECEDENCIA_DO_AVISO = Duration.ofHours(72);
    /** Depois de vencer, a IA ainda responde por 24 h. */
    static final Duration CARENCIA = Duration.ofHours(24);

    public EstadoAssinatura estadoAssinatura(Instant agora) {
        if (assinaturaAte == null) {
            return EstadoAssinatura.ATIVA;
        }
        if (agora.isAfter(assinaturaAte.plus(CARENCIA))) {
            return EstadoAssinatura.VENCIDA;
        }
        if (agora.isAfter(assinaturaAte)) {
            return EstadoAssinatura.CARENCIA;
        }
        return agora.isAfter(assinaturaAte.minus(ANTECEDENCIA_DO_AVISO))
                ? EstadoAssinatura.AVISO
                : EstadoAssinatura.ATIVA;
    }

    /** A IA responde os clientes deste corretor: conta ativa e assinatura em dia ou ainda na carência. */
    public boolean iaLiberada(Instant agora) {
        return ativo && estadoAssinatura(agora) != EstadoAssinatura.VENCIDA;
    }
}
