package app.bizu.conversa;

import java.util.List;

/** Porta para o agente de IA. */
public interface Agente {

    RespostaAgente responder(Contexto contexto);

    record Contexto(Conversa conversa, List<Trecho> historico, String promptBase, String promptEstilo) {
    }

    /** Uma mensagem da conversa; {@code doCliente} diferencia cliente de IA/corretor. */
    record Trecho(boolean doCliente, String texto) {
    }

    /**
     * Resposta do agente. {@code passarAoCorretor} muda a conversa para HUMANO sem enviar nada;
     * {@code texto} em branco também não envia nada.
     */
    record RespostaAgente(String texto, Qualificacao qualificacao, boolean passarAoCorretor) {

        public static RespostaAgente passar() {
            return new RespostaAgente(null, null, true);
        }
    }

    record Qualificacao(String finalidade, String regiao, String faixaValor, String prazo, String imovelCitado) {

        public boolean vazia() {
            return finalidade == null && regiao == null && faixaValor == null && prazo == null && imovelCitado == null;
        }
    }
}
