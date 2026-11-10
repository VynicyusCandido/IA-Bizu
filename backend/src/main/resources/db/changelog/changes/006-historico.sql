--liquibase formatted sql

--changeset bizu:006-historico
-- SEM_IA: conversa que aparece no painel mas a IA nunca atende (contato salvo ou contato sem número conhecido).
alter table conversa drop constraint conversa_estado_check;
alter table conversa add constraint conversa_estado_check check (estado in ('BOT', 'HUMANO', 'ENCERRADA', 'SEM_IA'));

-- Nome que aparece na lista: o que o WhatsApp informa do contato (pushName) ou o nome salvo na agenda.
alter table conversa add column nome_contato varchar(255);
alter table contato_ignorado add column nome varchar(255);
--rollback alter table contato_ignorado drop column nome; alter table conversa drop column nome_contato; update conversa set estado = 'HUMANO' where estado = 'SEM_IA'; alter table conversa drop constraint conversa_estado_check; alter table conversa add constraint conversa_estado_check check (estado in ('BOT', 'HUMANO', 'ENCERRADA'));
