package app.bizu.conversa;

import java.util.Optional;

/** Porta para buscar a foto de perfil de um contato no WhatsApp do corretor. Só para exibir no painel. */
public interface FotoDoContato {

    /** URL https da foto, ou vazio se o contato não tem (ou esconde) a foto. Pode demorar: nunca chamar em transação. */
    Optional<String> de(String corretorId, String identificador);
}
