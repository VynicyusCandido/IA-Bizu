package app.corretor.conversa;

/** Porta de saída para o WhatsApp. O domínio só conhece esta interface. */
public interface Canal {

    /** Envia o texto ao número e devolve o ID da mensagem no WhatsApp. */
    String enviar(String telefone, String texto);
}
