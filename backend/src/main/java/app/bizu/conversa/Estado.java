package app.bizu.conversa;

/**
 * BOT: a IA responde. HUMANO: o corretor assumiu. ENCERRADA: a IA chegou ao limite de respostas.
 * SEM_IA: aparece no painel, mas a IA nunca atende (contato salvo, ou contato que o WhatsApp só identifica por
 * código, sem telefone).
 */
public enum Estado {
    BOT, HUMANO, ENCERRADA, SEM_IA
}
