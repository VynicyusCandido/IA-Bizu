package app.bizu.agente;

/** Regras fixas do agente, expostas para o painel mostrá-las ao corretor como texto não editável. */
public final class RegrasDoAgente {

    private RegrasDoAgente() {
    }

    public static String texto() {
        return MontadorDeConversa.REGRAS_FIXAS.strip();
    }
}
