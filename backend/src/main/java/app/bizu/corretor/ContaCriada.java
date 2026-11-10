package app.bizu.corretor;

import java.time.Instant;

/** Publicado depois do commit quando um corretor entra pela primeira vez: a conta nasce inativa e espera liberação. */
public record ContaCriada(String authId, String email, String nome, Instant criadaEm) {
}
