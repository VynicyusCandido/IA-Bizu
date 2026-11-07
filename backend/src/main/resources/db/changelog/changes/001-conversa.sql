--liquibase formatted sql

--changeset bizu:001-conversa
create table conversa (
    id                  uuid primary key,
    telefone            varchar(32) not null,
    estado              varchar(16) not null check (estado in ('BOT', 'HUMANO', 'ENCERRADA')),
    respostas_ia        integer not null default 0 check (respostas_ia >= 0),
    ativa               boolean not null default true,
    criada_em           timestamptz not null,
    ultima_atividade_em timestamptz not null
);

-- No máximo uma conversa ativa por número; as antigas ficam como histórico.
create unique index ux_conversa_ativa_telefone on conversa (telefone) where ativa;

create table mensagem (
    id            uuid primary key,
    conversa_id   uuid not null references conversa (id),
    direcao       varchar(8) not null check (direcao in ('ENTRADA', 'SAIDA')),
    origem        varchar(8) not null check (origem in ('CLIENTE', 'IA', 'CORRETOR')),
    texto         text not null,
    wa_message_id varchar(128),
    criada_em     timestamptz not null
);

create unique index ux_mensagem_wa_id on mensagem (wa_message_id) where wa_message_id is not null;
create index ix_mensagem_conversa on mensagem (conversa_id, criada_em);

create table lead (
    conversa_id   uuid primary key references conversa (id),
    finalidade    varchar(255),
    regiao        varchar(255),
    faixa_valor   varchar(255),
    prazo         varchar(255),
    imovel_citado varchar(255),
    atualizado_em timestamptz not null
);
--rollback drop table lead; drop table mensagem; drop table conversa;
