package app.bizu.conversa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ConversaRepository {

    private static final RowMapper<Conversa> MAPPER = (rs, n) -> new Conversa(
            rs.getObject("id", UUID.class),
            rs.getString("corretor_id"),
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

    /** Busca a conversa ativa do número, deste corretor, travando a linha até o fim da transação. */
    Optional<Conversa> buscarAtivaParaAtualizar(String corretorId, String telefone) {
        return jdbc.sql("select * from conversa where corretor_id = :c and telefone = :t and ativa for update")
                .param("c", corretorId)
                .param("t", telefone)
                .query(MAPPER).optional();
    }

    /** Só devolve a conversa se for deste corretor: o id de outro corretor se comporta como inexistente. */
    Optional<Conversa> buscarParaAtualizar(String corretorId, UUID id) {
        return jdbc.sql("select * from conversa where id = :id and corretor_id = :c for update")
                .param("id", id)
                .param("c", corretorId)
                .query(MAPPER).optional();
    }

    /** Só devolve a conversa se for deste corretor. */
    Optional<Conversa> buscar(String corretorId, UUID id) {
        return jdbc.sql("select * from conversa where id = :id and corretor_id = :c")
                .param("id", id).param("c", corretorId).query(MAPPER).optional();
    }

    /** Uso interno da fila, que parte de uma conversa já reivindicada e conhece o dono por ela. */
    Optional<Conversa> buscar(UUID id) {
        return jdbc.sql("select * from conversa where id = :id").param("id", id).query(MAPPER).optional();
    }

    void inserir(Conversa c) {
        jdbc.sql("""
                insert into conversa (id, corretor_id, telefone, estado, respostas_ia, ativa, criada_em, ultima_atividade_em)
                values (:id, :corretor, :telefone, :estado, :respostas, :ativa, :criada, :ultima)
                """)
                .param("id", c.id())
                .param("corretor", c.corretorId())
                .param("telefone", c.telefone())
                .param("estado", c.estado().name())
                .param("respostas", c.respostasIa())
                .param("ativa", c.ativa())
                .param("criada", Tempo.sql(c.criadaEm()))
                .param("ultima", Tempo.sql(c.ultimaAtividadeEm()))
                .update();
    }

    /** Guarda o nome que o WhatsApp informa do contato; nome em branco não apaga o que já existe. */
    void atualizarNome(UUID id, String nome) {
        if (nome == null || nome.isBlank()) {
            return;
        }
        jdbc.sql("update conversa set nome_contato = :n where id = :id and nome_contato is distinct from :n")
                .param("id", id).param("n", nome.strip()).update();
    }

    /** Contato que acabou de ser identificado como salvo: a IA para de atender as conversas dele que estavam com ela. */
    void pararIa(String corretorId, java.util.Collection<String> telefones) {
        if (telefones.isEmpty()) {
            return;
        }
        jdbc.sql("""
                update conversa set estado = 'SEM_IA'
                 where corretor_id = :c and telefone in (:t) and ativa and estado = 'BOT'
                """)
                .param("c", corretorId).param("t", telefones).update();
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

    /** Conversas ativas do corretor, as mais recentes primeiro, com o que a IA já descobriu do cliente. */
    List<ConversaService.ConversaResumo> listarResumos(String corretorId) {
        return jdbc.sql("""
                select c.id, c.telefone, c.estado, c.respostas_ia, c.ultima_atividade_em,
                       l.finalidade, l.regiao, l.faixa_valor, l.prazo, l.imovel_citado,
                       left(u.texto, 120) as ultima_mensagem, u.origem as ultima_origem,
                       coalesce(c.nome_contato, ci.nome) as nome
                  from conversa c
                  left join lead l on l.conversa_id = c.id
                  left join contato_ignorado ci on ci.corretor_id = c.corretor_id and ci.telefone = c.telefone
                  left join lateral (select texto, origem from mensagem m
                                      where m.conversa_id = c.id
                                      order by m.criada_em desc, m.id desc limit 1) u on true
                 where c.ativa and c.corretor_id = :corretor
                 order by c.ultima_atividade_em desc
                 limit 200
                """)
                .param("corretor", corretorId)
                .query((rs, n) -> new ConversaService.ConversaResumo(
                        rs.getObject("id", UUID.class),
                        rs.getString("telefone"),
                        Estado.valueOf(rs.getString("estado")),
                        rs.getInt("respostas_ia"),
                        Tempo.ler(rs, "ultima_atividade_em"),
                        rs.getString("finalidade"),
                        rs.getString("regiao"),
                        rs.getString("faixa_valor"),
                        rs.getString("prazo"),
                        rs.getString("imovel_citado"),
                        rs.getString("ultima_mensagem"),
                        rs.getString("ultima_origem"),
                        rs.getString("nome")))
                .list();
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
