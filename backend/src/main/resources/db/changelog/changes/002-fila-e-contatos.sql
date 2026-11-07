--liquibase formatted sql

--changeset corretor:002-fila-e-contatos
-- Fila de mensagens do cliente aguardando resposta da IA (consumida por conversa).
create table fila_mensagem (
    id            bigserial primary key,
    conversa_id   uuid not null references conversa (id),
    mensagem_id   uuid not null references mensagem (id),
    disponivel_em timestamptz not null,
    tentativas    integer not null default 0
);

create index ix_fila_disponivel on fila_mensagem (disponivel_em);
create index ix_fila_conversa on fila_mensagem (conversa_id);

-- Números que a IA nunca atende: contatos salvos na agenda ou com escrita manual do corretor.
create table contato_ignorado (
    telefone  varchar(32) primary key,
    motivo    varchar(16) not null check (motivo in ('AGENDA', 'ESCRITA_MANUAL')),
    criado_em timestamptz not null
);
--rollback drop table contato_ignorado; drop table fila_mensagem;
