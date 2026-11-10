package app.bizu.conversa;

import app.bizu.conversa.ConversaService.ResumoDaImportacao;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Traz para o painel as conversas da última semana que já existiam no WhatsApp do corretor. */
@Service
public class ImportadorDeConversas {

    private static final Duration JANELA = Duration.ofDays(7);

    private final ConversaService conversas;
    private final HistoricoDeConversas historico;

    ImportadorDeConversas(ConversaService conversas, HistoricoDeConversas historico) {
        this.conversas = conversas;
        this.historico = historico;
    }

    /** Pode rodar de novo à vontade: mensagem já importada (mesmo id do WhatsApp) não entra duas vezes. */
    public ResumoDaImportacao ultimaSemana(String corretorId, Instant agora) {
        // A busca na Evolution é lenta e fica fora de qualquer transação do banco.
        return conversas.importar(corretorId, historico.desde(corretorId, agora.minus(JANELA)), agora);
    }
}
