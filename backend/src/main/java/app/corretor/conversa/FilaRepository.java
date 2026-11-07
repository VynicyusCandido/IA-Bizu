package app.corretor.conversa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FilaRepository {

    private final JdbcClient jdbc;

    FilaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Enfileira a mensagem e adia toda a fila da conversa: é isso que agrupa rajadas numa só chamada de IA. */
    void enfileirar(UUID conversaId, UUID mensagemId, Instant disponivelEm) {
        jdbc.sql("insert into fila_mensagem (conversa_id, mensagem_id, disponivel_em) values (:c, :m, :d)")
                .param("c", conversaId)
                .param("m", mensagemId)
                .param("d", Tempo.sql(disponivelEm))
                .update();
        jdbc.sql("update fila_mensagem set disponivel_em = :d where conversa_id = :c")
                .param("c", conversaId)
                .param("d", Tempo.sql(disponivelEm))
                .update();
    }

    List<Long> idsDaConversa(UUID conversaId) {
        return jdbc.sql("select id from fila_mensagem where conversa_id = :c order by id")
                .param("c", conversaId)
                .query(Long.class).list();
    }

    void remover(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("delete from fila_mensagem where id in (:ids)").param("ids", ids).update();
    }

    /** Registra uma falha: adia a conversa e descarta a fila ao passar do máximo de tentativas. */
    void registrarFalha(UUID conversaId, Instant novaDisponibilidade, int maximoTentativas) {
        jdbc.sql("""
                update fila_mensagem set tentativas = tentativas + 1, disponivel_em = :d
                 where conversa_id = :c
                """)
                .param("c", conversaId)
                .param("d", Tempo.sql(novaDisponibilidade))
                .update();
        jdbc.sql("delete from fila_mensagem where conversa_id = :c and tentativas >= :max")
                .param("c", conversaId)
                .param("max", maximoTentativas)
                .update();
    }
}
