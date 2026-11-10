package app.bizu.conversa;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PromptRepository {

    private final JdbcClient jdbc;

    PromptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void salvar(String corretorId, String chave, String conteudo, java.time.Instant agora) {
        jdbc.sql("""
                insert into prompt_agente (corretor_id, chave, conteudo, atualizado_em) values (:corretor, :k, :c, :agora)
                on conflict (corretor_id, chave)
                do update set conteudo = excluded.conteudo, atualizado_em = excluded.atualizado_em
                """)
                .param("corretor", corretorId)
                .param("k", chave)
                .param("c", conteudo)
                .param("agora", Tempo.sql(agora))
                .update();
    }

    /** Conteúdo do prompt da chave (BASE ou ESTILO), ou null se ainda não existe. */
    String conteudo(String corretorId, String chave) {
        return jdbc.sql("select conteudo from prompt_agente where corretor_id = :c and chave = :k")
                .param("c", corretorId)
                .param("k", chave)
                .query(String.class).optional().orElse(null);
    }
}
