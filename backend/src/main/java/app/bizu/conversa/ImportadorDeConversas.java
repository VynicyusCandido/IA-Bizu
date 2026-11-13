package app.bizu.conversa;

import app.bizu.conversa.ConversaService.ResumoDaImportacao;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Traz para o painel as conversas da última semana que já existiam no WhatsApp do corretor. */
@Service
public class ImportadorDeConversas {

    private static final Duration JANELA = Duration.ofDays(7);

    // shortcut: a trava vale por processo; com mais de uma instância do backend, trocar por lock no banco.
    private final Set<String> importando = ConcurrentHashMap.newKeySet();
    private final ConversaService conversas;
    private final HistoricoDeConversas historico;

    ImportadorDeConversas(ConversaService conversas, HistoricoDeConversas historico) {
        this.conversas = conversas;
        this.historico = historico;
    }

    /** Pode rodar de novo à vontade: mensagem já importada (mesmo id do WhatsApp) não entra duas vezes. Vazio se este corretor já tem uma importação em andamento. */
    public Optional<ResumoDaImportacao> ultimaSemana(String corretorId, Instant agora) {
        // Duas importações juntas tentariam criar as mesmas conversas e a segunda falharia no banco.
        if (!importando.add(corretorId)) {
            return Optional.empty();
        }
        try {
            // A busca na Evolution é lenta e fica fora de qualquer transação do banco.
            return Optional.of(conversas.importar(corretorId, historico.desde(corretorId, agora.minus(JANELA)), agora));
        } finally {
            importando.remove(corretorId);
        }
    }
}
