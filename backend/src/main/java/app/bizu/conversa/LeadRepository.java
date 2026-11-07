package app.bizu.conversa;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class LeadRepository {

    private final JdbcClient jdbc;

    LeadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava a qualificação mantendo o que já existia quando o campo novo vem vazio. */
    void salvar(UUID conversaId, Agente.Qualificacao q, Instant agora) {
        jdbc.sql("""
                insert into lead (conversa_id, finalidade, regiao, faixa_valor, prazo, imovel_citado, atualizado_em)
                values (:c, :fin, :reg, :faixa, :prazo, :imovel, :agora)
                on conflict (conversa_id) do update set
                    finalidade    = coalesce(excluded.finalidade, lead.finalidade),
                    regiao        = coalesce(excluded.regiao, lead.regiao),
                    faixa_valor   = coalesce(excluded.faixa_valor, lead.faixa_valor),
                    prazo         = coalesce(excluded.prazo, lead.prazo),
                    imovel_citado = coalesce(excluded.imovel_citado, lead.imovel_citado),
                    atualizado_em = excluded.atualizado_em
                """)
                .param("c", conversaId)
                .param("fin", q.finalidade())
                .param("reg", q.regiao())
                .param("faixa", q.faixaValor())
                .param("prazo", q.prazo())
                .param("imovel", q.imovelCitado())
                .param("agora", Tempo.sql(agora))
                .update();
    }
}
