package app.bizu.conversa;

/** Porta para o estado da conexão do WhatsApp (QR code). O domínio só conhece esta interface. */
public interface Conexao {

    Situacao situacao();

    /** Conectado, ou o QR (imagem em base64) e o código de emparelhamento para conectar o número. */
    record Situacao(boolean conectado, String qrCodeBase64, String codigoDeEmparelhamento) {
    }
}
