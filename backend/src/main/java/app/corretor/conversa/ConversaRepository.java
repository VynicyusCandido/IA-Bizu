package app.corretor.conversa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ConversaRepository {

    private static final RowMapper<Conversa> MAPPER = (rs, n) -> new Conversa(
            rs.getObject("id", UUID.class),
            rs.getString("telefone"),
            Estado.valueOf(rs.getString("estado")),
            rs.getInt("respostas_ia"),
            rs.getBoolean("ativa"),
            Tempo.ler(rs, "criada_em"),
            Tempo.ler(rs, "ultima_atividade_em"));

    private final JdbcClient jdbc;

    ConversaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Busca a conversa ativa do número travando a linha até o fim da transação. */
    Optional<Conversa> buscarAtivaParaAtualizar(String telefone) {
        return jdbc.sql("select * from conversa where telefone = :t and ativa for update")
                .param("t", telefone)
                .query(MAPPER).optional();
    }

    Optional<Conversa> buscarParaAtualizar(UUID id) {
        return jdbc.sql("select * from conversa where id = :id for update")
                .param("id", id)
                .query(MAPPER).optional();
    }

    Optional<Conversa> buscar(UUID id) {
        return jdbc.sql("select * from conversa where id = :id").param("id", id).query(MAPPER).optional();
    }

    void inserir(Conversa c) {
        jdbc.sql("""
                insert into conversa (id, telefone, estado, respostas_ia, ativa, criada_em, ultima_atividade_em)
                values (:id, :telefone, :estado, :respostas, :ativa, :criada, :ultima)
                """)
                .param("id", c.id())
                .param("telefone", c.telefone())
                .param("estado", c.estado().name())
                .param("respostas", c.respostasIa())
                .param("ativa", c.ativa())
                .param("criada", Tempo.sql(c.criadaEm()))
                .param("ultima", Tempo.sql(c.ultimaAtividadeEm()))
                .update();
    }

    void atualizar(Conversa c) {
        jdbc.sql("""
                update conversa
                   set estado = :estado, respostas_ia = :respostas, ativa = :ativa, ultima_atividade_em = :ultima
                 where id = :id
                """)
                .param("id", c.id())
                .param("estado", c.estado().name())
                .param("respostas", c.respostasIa())
                .param("ativa", c.ativa())
                .param("ultima", Tempo.sql(c.ultimaAtividadeEm()))
                .update();
    }

    /** Conversa com mensagens prontas na fila (janela de agrupamento vencida), travada sem bloquear outras. */
    Optional<UUID> reivindicarPronta(Instant agora) {
        return jdbc.sql("""
                select c.id from conversa c
                 where exists (select 1 from fila_mensagem f
                                where f.conversa_id = c.id and f.disponivel_em <= :agora)
                 order by c.ultima_atividade_em
                 limit 1
                 for update skip locked
                """)
                .param("agora", Tempo.sql(agora))
                .query((rs, n) -> rs.getObject("id", UUID.class)).optional();
    }
}
