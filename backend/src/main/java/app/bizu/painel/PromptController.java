package app.bizu.painel;

import app.bizu.agente.RegrasDoAgente;
import app.bizu.conversa.PromptService;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O prompt que o corretor escreve para o agente, mais o que ele não edita (regras fixas e estilo). */
@RestController
@RequestMapping("/api/prompt")
class PromptController {

    static final int TAMANHO_MAXIMO = 8000;

    record PedidoPrompt(String base) {
    }

    record Prompt(String base, String estilo, String regrasFixas) {
    }

    private final PromptService prompts;
    private final Clock relogio;

    PromptController(PromptService prompts, Clock relogio) {
        this.prompts = prompts;
        this.relogio = relogio;
    }

    @GetMapping
    Prompt obter() {
        return atual();
    }

    @PutMapping
    ResponseEntity<Prompt> salvar(@RequestBody PedidoPrompt pedido) {
        if (pedido.base() == null || pedido.base().length() > TAMANHO_MAXIMO) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        prompts.salvarBase(pedido.base(), relogio.instant());
        return ResponseEntity.ok(atual());
    }

    private Prompt atual() {
        return new Prompt(prompts.base(), prompts.estilo(), RegrasDoAgente.texto());
    }
}
