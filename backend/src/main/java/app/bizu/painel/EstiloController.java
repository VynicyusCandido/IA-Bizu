package app.bizu.painel;

import app.bizu.conversa.EstiloService;
import java.time.Clock;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gera (ou refaz) o perfil de estilo do corretor a partir das mensagens que ele já enviou. */
@RestController
@RequestMapping("/api/estilo")
class EstiloController {

    record Resultado(boolean gerado) {
    }

    private final EstiloService estilo;
    private final Clock relogio;

    EstiloController(EstiloService estilo, Clock relogio) {
        this.estilo = estilo;
        this.relogio = relogio;
    }

    @PostMapping("/gerar")
    Resultado gerar() {
        return new Resultado(estilo.gerar(relogio.instant()));
    }
}
