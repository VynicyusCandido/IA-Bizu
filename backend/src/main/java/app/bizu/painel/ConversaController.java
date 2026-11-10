package app.bizu.painel;

import app.bizu.conversa.Conversa;
import app.bizu.conversa.ConversaService;
import app.bizu.conversa.ConversaService.ConversaResumo;
import app.bizu.conversa.ConversaService.MensagemDaConversa;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conversas para o corretor ver (com a qualificação do cliente), assumir e devolver à IA. */
@RestController
@RequestMapping("/api/conversas")
class ConversaController {

    private final ConversaService conversas;
    private final Clock relogio;

    ConversaController(ConversaService conversas, Clock relogio) {
        this.conversas = conversas;
        this.relogio = relogio;
    }

    @GetMapping
    List<ConversaResumo> listar() {
        return conversas.listar();
    }

    @GetMapping("/{id}/mensagens")
    ResponseEntity<List<MensagemDaConversa>> mensagens(@PathVariable UUID id) {
        return conversas.mensagens(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/assumir")
    ResponseEntity<Conversa> assumir(@PathVariable UUID id) {
        return conversas.assumir(id, relogio.instant()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/devolver")
    ResponseEntity<Conversa> devolver(@PathVariable UUID id) {
        return conversas.devolver(id, relogio.instant()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
