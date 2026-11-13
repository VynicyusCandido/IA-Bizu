package app.bizu.conversa;

/** Porta para saber se um contato está salvo na agenda do corretor. A IA nunca atende contato salvo. */
public interface AgendaDoCorretor {

    /** Salvo na agenda do WhatsApp do corretor? Falha (WhatsApp fora do ar) lança exceção. */
    boolean salvo(String corretorId, String identificador);
}
