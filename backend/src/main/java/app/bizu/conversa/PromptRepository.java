package app.bizu.conversa;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PromptRepository {

    private final JdbcClient jdbc;

    PromptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void salvar(String chave, String conteudo, java.time.Instant agora) {
        jdbc.sql("""
                insert into prompt_agente (chave, conteudo, atualizado_em) values (:k, :c, :agora)
                on conflict (chave) do update set conteudo = excluded.conteudo, atualizado_em = excluded.atualizado_em
                """)
                .param("k", chave)
                .param("c", conteudo)
                .param("agora", Tempo.sql(agora))
                .update();
    }

    /** Conteúdo do prompt da chave (BASE ou ESTILO), ou null se ainda não existe. */
    String conteudo(String chave) {
        return jdbc.sql("select conteudo from prompt_agente where chave = :k")
                .param("k", chave)
                .query(String.class).optional().orElse(null);
    }
}
