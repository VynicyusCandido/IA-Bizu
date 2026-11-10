package app.bizu.whatsapp;

import app.bizu.conversa.ConversaService;
import app.bizu.corretor.Corretor;
import app.bizu.corretor.CorretorService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
    private final CorretorService corretores;
    private final Clock relogio;
    private final String segredo;

    WebhookController(ConversaService conversas, CorretorService corretores, Clock relogio,
                      @Value("${bizu.webhook-segredo:}") String segredo) {
        this.conversas = conversas;
        this.corretores = corretores;
        this.relogio = relogio;
        this.segredo = segredo;
    }

    @PostMapping
    ResponseEntity<Void> receber(@RequestHeader(value = "apikey", required = false) String apikey,
                                 @RequestBody EvolutionWebhook webhook) {
        if (!autorizado(apikey)) {
            log.warn("Webhook recusado: apikey ausente ou diferente de WEBHOOK_SEGREDO (instância '{}')",
                    webhook.instance());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!webhook.ehContatos() && !webhook.ehNovaMensagem()) {
            return ResponseEntity.ok().build();
        }

        // Cada corretor tem a própria instância: é por ela que se sabe de quem é a mensagem.
        Optional<Corretor> dono = corretores.porInstancia(webhook.instance());
        if (dono.isEmpty()) {
            log.warn("Webhook descartado: a instância '{}' não pertence a nenhum corretor", webhook.instance());
            return ResponseEntity.ok().build();
        }
        // Corretor desligado: o painel já não abre para ele, então guardar as conversas dos clientes dele só
        // acumula dados de quem ninguém mais atende. (Assinatura vencida é outro caso: a conta segue ativa.)
        if (!dono.get().ativo()) {
            log.info("Webhook ignorado: a conta do corretor {} está desligada", dono.get().authId());
            return ResponseEntity.ok().build();
        }
        String corretorId = dono.get().authId();

        if (webhook.ehContatos()) {
            Instant agora = relogio.instant();
            conversas.marcarContatosSalvos(corretorId, webhook.telefonesDeContatos(), agora);
            return ResponseEntity.ok().build();
        }

        var telefone = webhook.telefone();
        var texto = webhook.texto();
        if (telefone.isPresent() && texto.isPresent()) {
            if (webhook.fromMe()) {
                conversas.receberDoCorretor(corretorId, telefone.get(), texto.get(), webhook.idMensagem(),
                        relogio.instant());
            } else {
                conversas.receberDoCliente(corretorId, telefone.get(), texto.get(), webhook.idMensagem(),
                        relogio.instant());
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
