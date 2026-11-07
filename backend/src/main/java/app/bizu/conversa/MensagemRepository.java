package app.bizu.conversa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MensagemRepository {

    enum Direcao { ENTRADA, SAIDA }

    enum Origem { CLIENTE, IA, CORRETOR }

    private final JdbcClient jdbc;

    MensagemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Insere a mensagem; devolve false se o ID do WhatsApp já existia (webhook repetido). */
    boolean inserir(UUID id, UUID conversaId, Direcao direcao, Origem origem, String texto,
                    String waMessageId, Instant agora) {
        return jdbc.sql("""
                insert into mensagem (id, conversa_id, direcao, origem, texto, wa_message_id, criada_em)
                values (:id, :conversa, :direcao, :origem, :texto, :wa, :agora)
                on conflict do nothing
                """)
                .param("id", id)
                .param("conversa", conversaId)
                .param("direcao", direcao.name())
                .param("origem", origem.name())
                .param("texto", texto)
                .param("wa", waMessageId)
                .param("agora", Tempo.sql(agora))
                .update() == 1;
    }

    boolean existePorWaId(String waMessageId) {
        return jdbc.sql("select count(*) from mensagem where wa_message_id = :wa")
                .param("wa", waMessageId)
                .query(Integer.class).single() > 0;
    }

    /** A IA enviou exatamente este texto nesta conversa desde o instante dado? (eco ainda sem ID gravado) */
    boolean existeSaidaIaRecente(UUID conversaId, String texto, Instant desde) {
        return jdbc.sql("""
                select count(*) from mensagem
                 where conversa_id = :c and origem = 'IA' and texto = :texto and criada_em >= :desde
                """)
                .param("c", conversaId)
                .param("texto", texto)
                .param("desde", Tempo.sql(desde))
                .query(Integer.class).single() > 0;
    }

    List<Agente.Trecho> historico(UUID conversaId) {
        return jdbc.sql("select origem, texto from mensagem where conversa_id = :c order by criada_em, id")
                .param("c", conversaId)
                .query((rs, n) -> new Agente.Trecho("CLIENTE".equals(rs.getString("origem")), rs.getString("texto")))
                .list();
    }
}
