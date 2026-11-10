package app.bizu.conversa;

/** Porta de saída para o WhatsApp. O domínio só conhece esta interface. */
public interface Canal {

    /** Envia o texto ao número, pelo WhatsApp deste corretor, e devolve o ID da mensagem no WhatsApp. */
    String enviar(String corretorId, String telefone, String texto);
}
