package app.bizu.painel;

import app.bizu.conversa.Conexao;
import app.bizu.conversa.Conexao.Situacao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mostra se o número está conectado e, se não, o QR code para o corretor ler. */
@RestController
@RequestMapping("/api/whatsapp")
class WhatsappController {

    private final Conexao conexao;

    WhatsappController(Conexao conexao) {
        this.conexao = conexao;
    }

    @GetMapping
    Situacao situacao() {
        return conexao.situacao();
    }
}
