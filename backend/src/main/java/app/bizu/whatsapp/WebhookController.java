package app.bizu.whatsapp;

import app.bizu.conversa.ConversaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ConversaService conversas;
    private final Clock relogio;
    private final String segredo;

    WebhookController(ConversaService conversas, Clock relogio,
                      @Value("${bizu.webhook-segredo:}") String segredo) {
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
        if (webhook.ehContatos()) {
            Instant agora = relogio.instant();
            webhook.telefonesDeContatos().forEach(telefone -> conversas.marcarContatoSalvo(telefone, agora));
            return ResponseEntity.ok().build();
        }
        if (!webhook.ehNovaMensagem()) {
            return ResponseEntity.ok().build();
        }

        var telefone = webhook.telefone();
        var texto = webhook.texto();
        if (telefone.isPresent() && texto.isPresent()) {
            if (webhook.fromMe()) {
                conversas.receberDoCorretor(telefone.get(), texto.get(), webhook.idMensagem(), relogio.instant());
            } else {
                conversas.receberDoCliente(telefone.get(), texto.get(), webhook.idMensagem(), relogio.instant());
            }
        } else if (texto.isPresent() && webhook.jid().filter(j -> j.endsWith("@lid")).isPresent()) {
            // Formato de endereço que o sistema ainda não entende: sem este aviso a mensagem sumiria em silêncio.
            log.warn("Mensagem descartada: o destino {} não traz telefone (formato @lid)", webhook.jid().get());
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
