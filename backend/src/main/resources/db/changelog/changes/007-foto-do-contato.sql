--liquibase formatted sql

--changeset bizu:007-foto-do-contato
-- Foto do contato no WhatsApp (URL do CDN deles, que expira: a importação seguinte renova).
alter table conversa add column foto_url text;
--rollback alter table conversa drop column foto_url;
