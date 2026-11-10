--liquibase formatted sql

--changeset bizu:004-corretores
-- Um corretor por conta Google. auth_id é o "sub" do Google: fixo, identifica e nunca prova nada sozinho.
-- Conta nasce inativa; o dono do sistema ativa e define a validade da assinatura (null = sem vencimento).
create table corretor (
    auth_id         varchar(64) primary key,
    email           varchar(255) not null,
    nome            varchar(255),
    ativo           boolean not null default false,
    assinatura_ate  timestamptz,
    instancia       varchar(100) unique,
    senha_hash      text,
    criado_em       timestamptz not null,
    ultimo_login_em timestamptz
);

-- Dono dos dados de antes (uso com um corretor só). Inativo: ninguém entra por ele.
insert into corretor (auth_id, email, nome, ativo, criado_em)
values ('legado', 'legado@bizu.local', 'Dados anteriores', false, now());

alter table conversa add column corretor_id varchar(64) references corretor (auth_id);
update conversa set corretor_id = 'legado';
alter table conversa alter column corretor_id set not null;
drop index ux_conversa_ativa_telefone;
-- No máximo uma conversa ativa por número, por corretor.
create unique index ux_conversa_ativa_corretor_telefone on conversa (corretor_id, telefone) where ativa;
create index ix_conversa_corretor on conversa (corretor_id);

alter table contato_ignorado add column corretor_id varchar(64) references corretor (auth_id);
update contato_ignorado set corretor_id = 'legado';
alter table contato_ignorado alter column corretor_id set not null;
alter table contato_ignorado drop constraint contato_ignorado_pkey;
alter table contato_ignorado add primary key (corretor_id, telefone);

alter table prompt_agente add column corretor_id varchar(64) references corretor (auth_id);
update prompt_agente set corretor_id = 'legado';
alter table prompt_agente alter column corretor_id set not null;
alter table prompt_agente drop constraint prompt_agente_pkey;
alter table prompt_agente add primary key (corretor_id, chave);
--rollback alter table prompt_agente drop constraint prompt_agente_pkey; alter table prompt_agente add primary key (chave); alter table prompt_agente drop column corretor_id;
--rollback alter table contato_ignorado drop constraint contato_ignorado_pkey; alter table contato_ignorado add primary key (telefone); alter table contato_ignorado drop column corretor_id;
--rollback drop index ix_conversa_corretor; drop index ux_conversa_ativa_corretor_telefone; create unique index ux_conversa_ativa_telefone on conversa (telefone) where ativa; alter table conversa drop column corretor_id;
--rollback drop table corretor;
