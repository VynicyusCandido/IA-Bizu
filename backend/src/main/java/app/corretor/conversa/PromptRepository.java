package app.corretor.conversa;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PromptRepository {

    private final JdbcClient jdbc;

    PromptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Conteúdo do prompt da chave (BASE ou ESTILO), ou null se ainda não existe. */
    String conteudo(String chave) {
        return jdbc.sql("select conteudo from prompt_agente where chave = :k")
                .param("k", chave)
                .query(String.class).optional().orElse(null);
    }
}
