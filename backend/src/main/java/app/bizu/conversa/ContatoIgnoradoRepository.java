package app.bizu.conversa;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ContatoIgnoradoRepository {

    enum Motivo { AGENDA, ESCRITA_MANUAL }

    private final JdbcClient jdbc;

    ContatoIgnoradoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Por que a IA não atende este número, ou vazio se ela pode atender. */
    Optional<Motivo> motivo(String corretorId, String telefone) {
        return jdbc.sql("select motivo from contato_ignorado where corretor_id = :c and telefone = :t")
                .param("c", corretorId)
                .param("t", telefone)
                .query(String.class).optional().map(Motivo::valueOf);
    }

    /** Marca o número. Se já estava marcado, mantém o motivo e só completa o nome que faltava. */
    void marcar(String corretorId, String telefone, Motivo motivo, String nome, Instant agora) {
        jdbc.sql("""
                insert into contato_ignorado (corretor_id, telefone, motivo, nome, criado_em) values (:c, :t, :m, :nome, :agora)
                on conflict (corretor_id, telefone) do update set nome = coalesce(contato_ignorado.nome, excluded.nome)
                """)
                .param("c", corretorId)
                .param("t", telefone)
                .param("m", motivo.name())
                .param("nome", nome)
                .param("agora", Tempo.sql(agora))
                .update();
    }
}
