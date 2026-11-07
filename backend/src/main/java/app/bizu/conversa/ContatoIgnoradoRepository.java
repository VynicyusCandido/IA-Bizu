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

    boolean existe(String telefone) {
        return jdbc.sql("select count(*) from contato_ignorado where telefone = :t")
                .param("t", telefone)
                .query(Integer.class).single() > 0;
    }

    void marcar(String telefone, Motivo motivo, Instant agora) {
        jdbc.sql("""
                insert into contato_ignorado (telefone, motivo, criado_em) values (:t, :m, :agora)
                on conflict do nothing
                """)
                .param("t", telefone)
                .param("m", motivo.name())
                .param("agora", Tempo.sql(agora))
                .update();
    }
}
