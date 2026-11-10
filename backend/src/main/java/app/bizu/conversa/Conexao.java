package app.bizu.conversa;

/** Porta para a conexão do WhatsApp (QR code) de cada corretor. O domínio só conhece esta interface. */
public interface Conexao {

    /** Só consulta: o corretor ainda sem instância é "não conectado". Nunca cria nada nem pede QR. */
    boolean conectado(String corretorId);

    /** Cria a instância do corretor na primeira vez e devolve o QR e o código de emparelhamento se faltar conectar. */
    Situacao conectar(String corretorId);

    /** Conectado, ou o QR (imagem em base64) e o código de emparelhamento para conectar o número. */
    record Situacao(boolean conectado, String qrCodeBase64, String codigoDeEmparelhamento) {
    }
}
