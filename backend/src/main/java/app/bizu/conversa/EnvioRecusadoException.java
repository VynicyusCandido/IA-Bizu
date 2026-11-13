package app.bizu.conversa;

/** O WhatsApp não aceitou a mensagem que o corretor mandou pelo painel; nada foi gravado. */
public class EnvioRecusadoException extends RuntimeException {

    EnvioRecusadoException(Throwable causa) {
        super("Não foi possível enviar a mensagem pelo WhatsApp", causa);
    }
}
