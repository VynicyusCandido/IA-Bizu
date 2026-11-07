package app.bizu.conversa;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura e edição dos prompts do agente (BASE é editável pelo corretor; ESTILO é gerado). */
@Service
public class PromptService {

    private final PromptRepository prompts;

    PromptService(PromptRepository prompts) {
        this.prompts = prompts;
    }

    /** Prompt que o corretor escreve; texto vazio significa usar o prompt padrão do agente. */
    public String base() {
        String base = prompts.conteudo("BASE");
        return base == null ? "" : base;
    }

    /** Perfil de estilo gerado das conversas do corretor, ou texto vazio se ainda não existe. */
    public String estilo() {
        String estilo = prompts.conteudo("ESTILO");
        return estilo == null ? "" : estilo;
    }

    @Transactional
    public void salvarBase(String conteudo, Instant agora) {
        prompts.salvar("BASE", conteudo, agora);
    }
}
