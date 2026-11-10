package app.bizu.corretor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CorretorRepository {

    private static final RowMapper<Corretor> MAPPER = (rs, n) -> new Corretor(
            rs.getString("auth_id"),
            rs.getString("google_sub"),
            rs.getString("email"),
            rs.getString("nome"),
            rs.getBoolean("ativo"),
            instante(rs, "assinatura_ate"),
            (Integer) rs.getObject("limite_respostas_mes"),
            rs.getString("instancia"),
            instante(rs, "criado_em"),
            instante(rs, "ultimo_login_em"));

    private final JdbcClient jdbc;

    CorretorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Corretor> buscar(String authId) {
        return jdbc.sql("select * from corretor where auth_id = :id").param("id", authId).query(MAPPER).optional();
    }

    Optional<Corretor> buscarPorGoogleSub(String sub) {
        return jdbc.sql("select * from corretor where google_sub = :s").param("s", sub).query(MAPPER).optional();
    }

    /** Conta de antes da separação das colunas: o sub do Google foi usado como auth_id e google_sub ficou vazio. */
    Optional<Corretor> buscarPorAuthIdSemGoogleSub(String sub) {
        return jdbc.sql("select * from corretor where auth_id = :s and google_sub is null")
                .param("s", sub).query(MAPPER).optional();
    }

    Optional<Corretor> buscarPorInstancia(String instancia) {
        return jdbc.sql("select * from corretor where instancia = :i").param("i", instancia).query(MAPPER).optional();
    }

    /**
     * Conta nova nasce inativa. Devolve false se outro login do mesmo Google criou a conta no mesmo instante
     * (o google_sub é único), para quem chamou não tratar como conta nova duas vezes.
     */
    boolean criar(String authId, String googleSub, String email, String nome, Instant agora) {
        return jdbc.sql("""
                insert into corretor (auth_id, google_sub, email, nome, ativo, criado_em, ultimo_login_em)
                values (:id, :sub, :email, :nome, false, :agora, :agora)
                on conflict do nothing
                """)
                .param("id", authId).param("sub", googleSub).param("email", email).param("nome", nome)
                .param("agora", sql(agora))
                .update() == 1;
    }

    /** Nome e e-mail acompanham o que o Google informa; o vínculo é pelo identificador, nunca pelo e-mail. */
    void registrarLogin(String authId, String googleSub, String email, String nome, Instant agora) {
        jdbc.sql("""
                update corretor set google_sub = :sub, email = :email, nome = :nome, ultimo_login_em = :agora
                 where auth_id = :id
                """)
                .param("id", authId).param("sub", googleSub).param("email", email).param("nome", nome)
                .param("agora", sql(agora))
                .update();
    }

    void definirInstancia(String authId, String instancia) {
        jdbc.sql("update corretor set instancia = :i where auth_id = :id")
                .param("id", authId).param("i", instancia).update();
    }

    int respostasNoMes(String authId, String mes) {
        return jdbc.sql("select coalesce(sum(respostas), 0) from uso_ia where corretor_id = :id and mes = :mes")
                .param("id", authId).param("mes", mes)
                .query(Integer.class).single();
    }

    void contarResposta(String authId, String mes) {
        jdbc.sql("""
                insert into uso_ia (corretor_id, mes, respostas) values (:id, :mes, 1)
                on conflict (corretor_id, mes) do update set respostas = uso_ia.respostas + 1
                """)
                .param("id", authId).param("mes", mes).update();
    }

    private static OffsetDateTime sql(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime valor = rs.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
