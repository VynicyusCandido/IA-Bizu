--liquibase formatted sql

--changeset bizu:005-identidade-e-uso
-- google_sub: identificador da conta no Google, separado do auth_id (código próprio do Bizu, nunca muda).
-- Contas criadas antes desta mudança usaram o sub como auth_id: ele é copiado para google_sub.
alter table corretor add column google_sub varchar(64) unique;
update corretor set google_sub = auth_id where auth_id <> 'legado';

-- Teto mensal de respostas da IA por corretor (nulo = usa o padrão da aplicação, bizu.corretor.limite-mensal-respostas).
alter table corretor add column limite_respostas_mes integer check (limite_respostas_mes is null or limite_respostas_mes >= 0);

-- Respostas da IA enviadas por corretor em cada mês (UTC, formato 2026-11).
create table uso_ia (
    corretor_id varchar(64) not null references corretor (auth_id),
    mes         char(7) not null,
    respostas   integer not null default 0,
    primary key (corretor_id, mes)
);
--rollback drop table uso_ia; alter table corretor drop column limite_respostas_mes; alter table corretor drop column google_sub;
