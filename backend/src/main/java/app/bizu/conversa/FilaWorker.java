package app.bizu.conversa;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sonda a fila em intervalo fixo e responde as conversas prontas, várias ao mesmo tempo: uma conversa lenta (a
 * IA demorando) não segura as dos outros corretores. Cada trabalhador pega uma conversa diferente, porque a
 * reserva da fila ({@code for update skip locked}) nunca entrega a mesma duas vezes.
 */
@Component
class FilaWorker {

    private static final Logger log = LoggerFactory.getLogger(FilaWorker.class);

    private final ProcessadorDeFila processador;
    private final Clock relogio;
    private final Semaphore vagas;
    private final ExecutorService trabalhadores = Executors.newVirtualThreadPerTaskExecutor();

    FilaWorker(ProcessadorDeFila processador, Clock relogio, ConversaProperties props) {
        this.processador = processador;
        this.relogio = relogio;
        this.vagas = new Semaphore(Math.max(1, props.trabalhadores()));
    }

    /** A cada sondagem completa as vagas livres; trabalhador sem nada a fazer termina e devolve a vaga. */
    @Scheduled(fixedDelayString = "${bizu.conversa.sondagem-fila}")
    void sondar() {
        while (vagas.tryAcquire()) {
            trabalhadores.execute(() -> {
                try {
                    esvaziar();
                } finally {
                    vagas.release();
                }
            });
        }
    }

    /** Responde as conversas prontas até a fila esvaziar. */
    private void esvaziar() {
        try {
            while (processador.processarProxima(relogio.instant())) {
                // próxima conversa pronta
            }
        } catch (ProcessadorDeFila.FalhaNoProcessamento falha) {
            log.warn("{}", falha.getMessage(), falha.getCause());
            processador.registrarFalha(falha.conversaId(), relogio.instant());
        } catch (RuntimeException e) {
            log.error("Falha inesperada ao processar a fila", e);
        }
    }

    @PreDestroy
    void parar() {
        trabalhadores.shutdown();
    }
}
