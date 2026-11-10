package app.bizu.conversa;

import java.util.List;

/** Porta para as mensagens de texto que saíram do número do corretor (o WhatsApp entrega o que tiver). */
public interface HistoricoDoCorretor {

    List<MensagemEnviada> mensagensEnviadas(String corretorId, int maximo);

    record MensagemEnviada(String id, String texto) {
    }
}
