--liquibase formatted sql

--changeset bizu:008-mensagens-lidas
-- Até quando o corretor leu a conversa no painel; mensagem do cliente depois disso é "não lida".
alter table conversa add column lida_em timestamptz;
-- O que já existia conta como lido: senão toda conversa antiga apareceria cheia de não lidas.
update conversa set lida_em = now();
--rollback alter table conversa drop column lida_em;
