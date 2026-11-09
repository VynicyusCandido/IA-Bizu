package app.bizu.conversa;

import app.bizu.conversa.HistoricoDoCorretor.MensagemEnviada;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Gera o perfil de estilo a partir do que o próprio corretor escreveu. Só texto do corretor entra: o que a IA
 * enviou é filtrado (senão ela imitaria a si mesma) e nenhuma conversa de cliente vai em bloco para o modelo.
 */
@Service
public class EstiloService {

    static final int MINIMO_MENSAGENS = 20;
    static final int MAXIMO_BUSCADO = 200;
    static final int TAMANHO_MAXIMO_MENSAGEM = 500;

    private final HistoricoDoCorretor historico;
    private final MensagemRepository mensagens;
    private final Agente agente;
    private final PromptService prompts;

    EstiloService(HistoricoDoCorretor historico, MensagemRepository mensagens, Agente agente, PromptService prompts) {
        this.historico = historico;
        this.mensagens = mensagens;
        this.agente = agente;
        this.prompts = prompts;
    }

    /** Devolve true se um perfil foi gerado e salvo; false se faltam mensagens ou o agente não gerou nada. */
    public boolean gerar(Instant agora) {
        List<String> textos = historico.mensagensEnviadas(MAXIMO_BUSCADO).stream()
                .filter(m -> m.texto() != null && !m.texto().isBlank())
                .filter(m -> m.id() == null || !mensagens.existeSaidaIaPorWaId(m.id()))
                .map(EstiloService::cortada)
                .toList();
        if (textos.size() < MINIMO_MENSAGENS) {
            return false;
        }
        Optional<String> perfil = agente.perfilDeEstilo(textos).filter(p -> !p.isBlank());
        perfil.ifPresent(p -> prompts.salvarEstilo(p.strip(), agora));
        return perfil.isPresent();
    }

    private static String cortada(MensagemEnviada m) {
        String texto = m.texto().strip();
        return texto.length() <= TAMANHO_MAXIMO_MENSAGEM ? texto : texto.substring(0, TAMANHO_MAXIMO_MENSAGEM);
    }
}
