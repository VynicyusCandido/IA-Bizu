package app.bizu.conversa;

import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ContatoIgnoradoRepository {

    enum Motivo { AGENDA, ESCRITA_MANUAL }

    private final JdbcClient jdbc;

    ContatoIgnoradoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean existe(String corretorId, String telefone) {
        return jdbc.sql("select count(*) from contato_ignorado where corretor_id = :c and telefone = :t")
                .param("c", corretorId)
                .param("t", telefone)
                .query(Integer.class).single() > 0;
    }

    void marcar(String corretorId, String telefone, Motivo motivo, Instant agora) {
        jdbc.sql("""
                insert into contato_ignorado (corretor_id, telefone, motivo, criado_em) values (:c, :t, :m, :agora)
                on conflict do nothing
                """)
                .param("c", corretorId)
                .param("t", telefone)
                .param("m", motivo.name())
                .param("agora", Tempo.sql(agora))
                .update();
    }
}
