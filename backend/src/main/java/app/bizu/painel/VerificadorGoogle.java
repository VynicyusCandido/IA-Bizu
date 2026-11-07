package app.bizu.painel;

import java.util.Optional;

/** Valida o ID token do Google e devolve o e-mail verificado, ou vazio se o token não vale. */
interface VerificadorGoogle {

    Optional<String> emailVerificado(String idToken);
}
