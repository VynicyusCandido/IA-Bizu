package app.bizu.conversa;

/** Porta de saída para o WhatsApp. O domínio só conhece esta interface. */
public interface Canal {

    /** Envia o texto ao número, pelo WhatsApp deste corretor, e devolve o ID da mensagem no WhatsApp. */
    String enviar(String corretorId, String telefone, String texto);

    /** Imagem, vídeo ou documento a enviar; {@code tipo} é "image", "video" ou "document". */
    record Midia(String tipo, String mimetype, String nome, byte[] bytes, String legenda) {
    }

    /** Envia a mídia ao número e devolve o ID da mensagem no WhatsApp. */
    default String enviarMidia(String corretorId, String telefone, Midia midia) {
        throw new UnsupportedOperationException("Este canal não envia anexos");
    }
}
