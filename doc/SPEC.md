---
id: SPEC-whatsapp-corretor
companions: [ARQUITETURA.md]
---

# Atendente de IA no WhatsApp para corretor de imóveis

## Why

Oportunidade e dor: o corretor perde tempo e leads respondendo as mesmas primeiras mensagens no WhatsApp. Ele quer uma IA que atenda em seu nome, com formalidade, descubra o que o cliente procura e entregue a conversa qualificada quando o cliente estiver pronto ou as 20 mensagens acabarem. Esta versão cobre só a IA do WhatsApp: um corretor, um número. Catálogo, agenda de visitas e dashboard são features seguintes.

## Capabilities

- **CAP-1**
  - **intent:** O corretor conecta seu número de WhatsApp lendo um QR code e a conexão se mantém.
  - **success:** Após ler o QR, uma mensagem recebida chega ao sistema e uma resposta enviada aparece no WhatsApp do cliente; a sessão volta sozinha após reiniciar o servidor.
- **CAP-2**
  - **intent:** A IA responde cada cliente em nome do corretor, seguindo um prompt de agente editável; usa o estilo das conversas recentes dele e, sem perfil de estilo, um tom formal.
  - **success:** Em 10 conversas de teste, nenhuma resposta foge do tom definido (estilo do corretor quando há perfil, formal quando não há); mensagens em rajada do cliente geram uma resposta só.
- **CAP-3**
  - **intent:** A IA qualifica o cliente (finalidade, região, faixa de valor, prazo) e pergunta se ele já viu algum imóvel específico, registrando a resposta.
  - **success:** Ao fim de uma conversa de teste, o sistema guarda finalidade, região, faixa de valor, prazo e o imóvel citado (se houver), sem a IA inventar dados de imóvel.
- **CAP-4**
  - **intent:** A conversa tem limite de 20 respostas da IA; depois da 20ª a IA fica em silêncio e a conversa segue manualmente com o corretor. O limite é também o teto de custo.
  - **success:** A 20ª resposta sai normalmente; nenhuma 21ª mensagem da IA e nenhuma mensagem de despedida ou aviso sai para o cliente; as mensagens do cliente continuam chegando ao celular do corretor.
- **CAP-5**
  - **intent:** O corretor assume a conversa a qualquer momento e a IA para de responder nela; ele pode devolvê-la.
  - **success:** Mensagem enviada pelo corretor do próprio celular para aquele cliente silencia a IA naquela conversa em até 5 s; ao devolver, a IA retoma.
- **CAP-6**
  - **intent:** O corretor entra num painel web mínimo com sua conta Google e usa o painel para ver o QR, editar o prompt do agente e ver (com os dados de qualificação), assumir e devolver conversas.
  - **success:** Só a conta Google do corretor entra (qualquer outra é recusada); pelo painel ele lê o QR, salva um novo prompt que vale na próxima resposta, vê finalidade, região, faixa de valor e prazo de cada conversa, e assume e devolve uma conversa.
- **CAP-7**
  - **intent:** A IA aprende o jeito do corretor escrever a partir das conversas recentes dele, sem ele aprovar cada resposta.
  - **success:** Após conectar o número, o sistema gera um perfil de estilo (tom, tamanho, expressões, saudações) das mensagens recentes que o corretor já enviou; se vierem poucas para formar um perfil, a IA usa o tom formal; em teste cego com 10 respostas da IA misturadas a 10 do corretor, quem conhece o corretor não distingue a maioria.

## Constraints

- A IA não afirma dados de imóvel (preço, metragem, disponibilidade) enquanto não houver catálogo; diante de pergunta assim, diz que o corretor confirma e sinaliza handoff.
- A IA fala em nome do corretor e não anuncia que é IA (decisão do usuário). Ressalva minha, não decidida pelo usuário: se o cliente perguntar diretamente se fala com uma pessoa ou uma IA, ela não afirma ser humana. Não construo uma IA que negue a quem pergunta sinceramente.
- Todas as mensagens da conversa aparecem no WhatsApp do celular do corretor (dispositivo vinculado); o sistema não envia avisos à parte.
- O histórico usado para aprender o estilo contém dados de terceiros (LGPD): só as mensagens do próprio corretor entram no perfil de estilo, e nenhuma conversa de outros clientes vai em bloco para a IA.
- Conexão por QR code não oficial (decisão do usuário): o sistema só responde a quem escreveu primeiro, sem disparos em massa, para reduzir risco de banimento.
- A IA só atende clientes novos e sem contato salvo: números salvos na agenda do corretor ou com quem ele já escreveu manualmente (família, amigos, clientes antigos) nunca são atendidos pela IA. Mensagens que a própria IA enviou não contam como o corretor ter escrito.
- O limite de 20 respostas é por cliente. Depois da 20ª, a IA não gera mais mensagem para aquele número; se o cliente ficar 7 dias sem trocar mensagens e voltar a escrever, começa uma nova conversa com mais 20 respostas, desde que o corretor nunca tenha escrito manualmente para ele e o número não esteja salvo nos contatos.
- O painel e a API só aceitam o login Google do corretor, com sessão em JWT; o webhook do WhatsApp só aceita chamadas com segredo compartilhado.
- Mensagens que o próprio sistema enviou e que voltam do WhatsApp como "enviadas pelo corretor" não contam como o corretor ter assumido a conversa.
- O contador de 20 respostas é por conversa e persiste mesmo se o sistema reiniciar.
- Dados de clientes (telefone, conversa) são pessoais (LGPD): acesso só do corretor.

## Non-goals

- Catálogo de imóveis, painel de itens e consulta a imóveis (próxima feature, num microsserviço dentro do dashboard).
- Agenda e marcação de visitas (também no microsserviço do dashboard).
- Dashboard de gestão; aqui só o painel mínimo de CAP-6.
- Vários corretores ou multi-cliente (SaaS).
- Áudio, imagem e vídeo recebidos do cliente.
- Disparo ativo de mensagens.

## Success signal

O corretor conecta um número de teste e, em 20 conversas simuladas, a IA qualifica o cliente, respeita o tom e o limite de 20 respostas, nunca inventa dado de imóvel, e o corretor assume uma conversa sem a IA interferir.

## Assumptions

- Um corretor, um número, pt-BR.
- Cliente final escreve primeiro.
- Só texto como entrada nesta versão.
- "Mensagem" no limite de 20 = mensagem enviada pela IA.
- A agenda de contatos só é conhecida pelo sistema se o WhatsApp a entregar ao vincular; se não entregar, vale só a checagem de histórico de mensagens.
- O perfil de estilo usa só o histórico que o WhatsApp entregar ao vincular, mesmo que parcial (decisão do usuário).
- Quando o cliente pede um humano, a IA fica em silêncio até o corretor assumir a conversa (decisão do usuário).
- Sem catálogo, "já viu um imóvel?" só registra o que o cliente disser; o envio de catálogo entra na próxima feature.

## Open Questions

- Nenhuma no momento.
