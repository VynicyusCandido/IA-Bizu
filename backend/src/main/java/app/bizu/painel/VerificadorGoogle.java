package app.bizu.painel;

import java.util.Optional;

/** Valida o ID token do Google e devolve quem é a pessoa, ou vazio se o token não vale. */
interface VerificadorGoogle {

    /** {@code sub} é o identificador fixo da conta no Google; e-mail e nome podem mudar. */
    record IdentidadeGoogle(String sub, String email, String nome) {
    }

    Optional<IdentidadeGoogle> verificar(String idToken);
}
