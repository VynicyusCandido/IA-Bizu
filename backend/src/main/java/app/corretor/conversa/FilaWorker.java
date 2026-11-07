package app.corretor.conversa;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sonda a fila em intervalo fixo e responde as conversas prontas. */
@Component
class FilaWorker {

    private static final Logger log = LoggerFactory.getLogger(FilaWorker.class);

    private final ProcessadorDeFila processador;
    private final Clock relogio;

    FilaWorker(ProcessadorDeFila processador, Clock relogio) {
        this.processador = processador;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelayString = "${corretor.conversa.sondagem-fila}")
    void sondar() {
        try {
            while (processador.processarProxima(relogio.instant())) {
                // esvazia as conversas prontas antes de dormir
            }
        } catch (ProcessadorDeFila.FalhaNoProcessamento falha) {
            log.warn("{}", falha.getMessage(), falha.getCause());
            processador.registrarFalha(falha.conversaId(), relogio.instant());
        }
    }
}
