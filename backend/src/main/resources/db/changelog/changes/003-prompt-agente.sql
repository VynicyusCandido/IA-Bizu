--liquibase formatted sql

--changeset bizu:003-prompt-agente
-- BASE: prompt do agente editável pelo corretor. ESTILO: perfil gerado das conversas dele.
create table prompt_agente (
    chave         varchar(16) primary key check (chave in ('BASE', 'ESTILO')),
    conteudo      text not null,
    atualizado_em timestamptz not null
);

insert into prompt_agente (chave, conteudo, atualizado_em) values (
    'BASE',
    'Você atende clientes no WhatsApp em nome de um corretor de imóveis, em português do Brasil, com educação e formalidade. '
    || 'Descubra a finalidade (compra, venda ou aluguel), a região de interesse, a faixa de valor e o prazo do cliente, e pergunte se ele já viu algum imóvel específico. '
    || 'Nunca invente preço, metragem, disponibilidade ou qualquer dado de imóvel: se perguntarem, diga que o corretor confirma. '
    || 'Não afirme ser uma pessoa se o cliente perguntar diretamente se está falando com uma pessoa ou com uma IA.',
    now()
);
--rollback drop table prompt_agente;
