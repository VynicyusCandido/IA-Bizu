package app.bizu.painel;

import app.bizu.corretor.CorretorService;
import app.bizu.corretor.EstadoAssinatura;
import app.bizu.corretor.MotivoDaPausa;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quem está logado e a situação da assinatura, para o painel avisar (modal e notificação). */
@RestController
@RequestMapping("/api/conta")
class ContaController {

    /** {@code pausaPor} é nulo quando a IA está respondendo; senão diz o motivo da pausa. */
    record Conta(String nome, String email, Instant assinaturaAte, EstadoAssinatura estadoAssinatura,
                 boolean iaAtiva, MotivoDaPausa pausaPor, int respostasNoMes, int limiteDoMes) {
    }

    private final CorretorService corretores;
    private final Clock relogio;

    ContaController(CorretorService corretores, Clock relogio) {
        this.corretores = corretores;
        this.relogio = relogio;
    }

    @GetMapping
    ResponseEntity<Conta> obter(@AuthenticationPrincipal Jwt sessao) {
        Instant agora = relogio.instant();
        return corretores.buscar(sessao.getSubject())
                .map(c -> {
                    MotivoDaPausa pausa = corretores.motivoDaPausa(c.authId(), agora).orElse(null);
                    return new Conta(c.nome(), c.email(), c.assinaturaAte(), c.estadoAssinatura(agora),
                            pausa == null, pausa, corretores.respostasNoMes(c.authId(), agora),
                            corretores.limiteMensal(c));
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
