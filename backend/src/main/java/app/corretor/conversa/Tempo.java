package app.corretor.conversa;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Conversão entre Instant e timestamptz (o driver do Postgres não lê Instant direto). */
final class Tempo {

    private Tempo() {
    }

    static OffsetDateTime sql(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    static Instant ler(ResultSet rs, String coluna) throws SQLException {
        return rs.getObject(coluna, OffsetDateTime.class).toInstant();
    }
}
