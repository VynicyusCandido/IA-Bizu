package app.corretor.whatsapp;

import app.corretor.conversa.ConversaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/evolution")
class WebhookController {

    private final ConversaService conversas;
    private final Clock relogio;
    private final String segredo;

    WebhookController(ConversaService conversas, Clock relogio,
                      @Value("${corretor.webhook-segredo:}") String segredo) {
        this.conversas = conversas;
        this.relogio = relogio;
        this.segredo = segredo;
    }

    @PostMapping
    ResponseEntity<Void> receber(@RequestHeader(value = "apikey", required = false) String apikey,
                                 @RequestBody EvolutionWebhook webhook) {
        if (!autorizado(apikey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!webhook.ehNovaMensagem()) {
            return ResponseEntity.ok().build();
        }

        var telefone = webhook.telefone();
        var texto = webhook.texto();
        if (telefone.isPresent() && texto.isPresent()) {
            var chave = webhook.data().key();
            if (chave.fromMe()) {
                conversas.receberDoCorretor(telefone.get(), texto.get(), chave.id(), relogio.instant());
            } else {
                conversas.receberDoCliente(telefone.get(), texto.get(), chave.id(), relogio.instant());
            }
        }
        return ResponseEntity.ok().build();
    }

    /** Falha fechada: sem segredo configurado, nenhuma chamada é aceita. Comparação em tempo constante. */
    private boolean autorizado(String apikey) {
        if (segredo.isBlank() || apikey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                segredo.getBytes(StandardCharsets.UTF_8), apikey.getBytes(StandardCharsets.UTF_8));
    }
}
