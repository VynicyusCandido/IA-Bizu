package app.bizu.painel;

import app.bizu.conversa.Conexao;
import app.bizu.conversa.Conexao.Situacao;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mostra se o número do corretor está conectado e, quando ele pede para conectar, o QR code para ler. */
@RestController
@RequestMapping("/api/whatsapp")
class WhatsappController {

    private final Conexao conexao;

    WhatsappController(Conexao conexao) {
        this.conexao = conexao;
    }

    /** Barato e sem efeito colateral: a barra do painel pergunta isto a cada poucos segundos. */
    @GetMapping
    Situacao situacao(@AuthenticationPrincipal Jwt sessao) {
        boolean conectado = conexao.conectado(sessao.getSubject());
        return new Situacao(conectado, null, null, conectado ? conexao.fotoDoCorretor(sessao.getSubject()) : null);
    }

    /** Cria a instância na primeira vez e devolve o QR code; a tela de conexão repete enquanto não conectar. */
    @PostMapping("/conectar")
    Situacao conectar(@AuthenticationPrincipal Jwt sessao) {
        return conexao.conectar(sessao.getSubject());
    }
}
