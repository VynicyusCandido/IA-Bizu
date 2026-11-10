package app.bizu.painel;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chave pública que verifica as sessões do Bizu: é o que outros serviços usam, sem poder emitir sessões. */
@RestController
class JwksController {

    private final ChavesDaSessao chaves;

    JwksController(ChavesDaSessao chaves) {
        this.chaves = chaves;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return chaves.publicaEmJwks().toJSONObject();
    }
}
