package app.bizu.painel;

import app.bizu.conversa.Canal;
import app.bizu.conversa.Conversa;
import app.bizu.conversa.ConversaService;
import app.bizu.conversa.EnvioRecusadoException;
import app.bizu.conversa.ConversaService.ConversaResumo;
import app.bizu.conversa.ConversaService.MensagemDaConversa;
import app.bizu.conversa.ConversaService.ResumoDaImportacao;
import app.bizu.conversa.ImportadorDeConversas;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

/** Conversas para o corretor ver (com a qualificação do cliente), assumir e devolver à IA. */
@RestController
@RequestMapping("/api/conversas")
class ConversaController {

    private final ConversaService conversas;
    private final ImportadorDeConversas importador;
    private final Clock relogio;

    ConversaController(ConversaService conversas, ImportadorDeConversas importador, Clock relogio) {
        this.conversas = conversas;
        this.importador = importador;
        this.relogio = relogio;
    }

    @GetMapping
    List<ConversaResumo> listar(@AuthenticationPrincipal Jwt sessao) {
        return conversas.listar(sessao.getSubject());
    }

    @GetMapping("/{id}/mensagens")
    ResponseEntity<List<MensagemDaConversa>> mensagens(@AuthenticationPrincipal Jwt sessao, @PathVariable UUID id) {
        return conversas.mensagens(sessao.getSubject(), id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Traz para o painel as conversas da última semana que já existiam no WhatsApp; pode rodar de novo à vontade; 409 se já há uma em andamento. */
    @PostMapping("/importar")
    ResponseEntity<ResumoDaImportacao> importar(@AuthenticationPrincipal Jwt sessao) {
        return importador.ultimaSemana(sessao.getSubject(), relogio.instant()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
    }

    record NovaMensagem(String texto) {
    }

    /** O corretor responde pelo painel. 400 se vazia ou longa demais, 404 se não for dele, 502 se o WhatsApp recusar. */
    @PostMapping("/{id}/mensagens")
    ResponseEntity<Conversa> responder(@AuthenticationPrincipal Jwt sessao, @PathVariable UUID id,
                                       @RequestBody NovaMensagem corpo) {
        String texto = corpo.texto() == null ? "" : corpo.texto().strip();
        if (texto.isEmpty() || texto.length() > 4000) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return conversas.responder(sessao.getSubject(), id, texto, relogio.instant()).map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EnvioRecusadoException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static final long LIMITE_DO_ANEXO = 10L * 1024 * 1024;

    /** Anexo do corretor (até 10 MB) com legenda opcional. 400 se vazio ou grande demais; demais códigos como no texto. */
    @PostMapping(value = "/{id}/anexos", consumes = "multipart/form-data")
    ResponseEntity<Conversa> anexar(@AuthenticationPrincipal Jwt sessao, @PathVariable UUID id,
                                    @RequestParam("arquivo") MultipartFile arquivo,
                                    @RequestParam(value = "legenda", required = false) String legenda) throws IOException {
        if (arquivo.isEmpty() || arquivo.getSize() > LIMITE_DO_ANEXO || (legenda != null && legenda.length() > 1000)) {
            return ResponseEntity.badRequest().build();
        }
        String mimetype = arquivo.getContentType() == null ? "application/octet-stream" : arquivo.getContentType();
        String tipo = mimetype.startsWith("image/") ? "image" : mimetype.startsWith("video/") ? "video" : "document";
        String nome = arquivo.getOriginalFilename() == null || arquivo.getOriginalFilename().isBlank()
                ? "arquivo" : arquivo.getOriginalFilename();
        var midia = new Canal.Midia(tipo, mimetype, nome, arquivo.getBytes(), legenda);
        try {
            return conversas.responderComAnexo(sessao.getSubject(), id, midia, relogio.instant()).map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EnvioRecusadoException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    record Leitura(String ate) {
    }

    /** O painel mostrou a conversa até a data {@code ate} (ISO): essas mensagens deixam de contar como não lidas. */
    @PostMapping("/{id}/lida")
    ResponseEntity<Void> lida(@AuthenticationPrincipal Jwt sessao, @PathVariable UUID id, @RequestBody Leitura corpo) {
        Instant ate;
        try {
            ate = Instant.parse(corpo.ate());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
        return conversas.marcarLida(sessao.getSubject(), id, ate, relogio.instant())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/assumir")
    ResponseEntity<Conversa> assumir(@AuthenticationPrincipal Jwt sessao, @PathVariable UUID id) {
        return conversas.assumir(sessao.getSubject(), id, relogio.instant()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/devolver")
    ResponseEntity<Conversa> devolver(@AuthenticationPrincipal Jwt sessao, @PathVariable UUID id) {
        return conversas.devolver(sessao.getSubject(), id, relogio.instant()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
