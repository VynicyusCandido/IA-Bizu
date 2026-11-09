# Plano do Bizu: atendente de IA no WhatsApp para corretor de imóveis

**Objetivo:** provar que uma IA consegue fazer o primeiro atendimento no WhatsApp do corretor, em nome dele e no jeito dele escrever, qualificar o cliente e entregar a conversa pronta, sem inventar dado de imóvel.

**Escopo (um corretor, um número, só texto, pt-BR):**
1. Conectar o número do corretor por QR code (Evolution API).
2. A IA responde clientes novos seguindo um prompt editável pelo corretor, com tom formal ou o estilo aprendido das mensagens dele.
3. A IA qualifica o cliente (finalidade, região, faixa de valor, prazo) e registra o imóvel que ele citar.
4. Limite de 20 respostas da IA por conversa; depois disso a IA fica em silêncio e a conversa segue com o corretor.
5. O corretor assume qualquer conversa (pelo celular ou pelo painel) e pode devolvê-la à IA.
6. Painel mínimo com login Google: QR code, prompt do agente, conversas com a qualificação, assumir e devolver.
7. Perfil de estilo gerado só com as mensagens que o próprio corretor enviou.

**Fora do escopo:** vários corretores, áudio, imagem e vídeo, disparo ativo de mensagens, API oficial do WhatsApp e consulta a dados de imóveis (a IA não afirma preço, metragem nem disponibilidade).

**Regras:**
- Nunca inventar dado de imóvel; diante de pergunta assim, a IA diz que o corretor confirma e passa a conversa.
- A IA só atende clientes novos: contatos salvos e números com quem o corretor já escreveu nunca são atendidos.
- A IA só responde a quem escreveu primeiro, sem disparos em massa (a conexão por QR não é oficial e pode ser banida; usar número de teste).
- Cliente que pede um humano deixa a IA em silêncio até o corretor assumir.
- Dados de clientes são pessoais (LGPD): acesso só do corretor, e nenhuma conversa de cliente vai em bloco para a IA.

**Pronto quando:** o corretor conecta um número de teste e, em 20 conversas simuladas, a IA qualifica o cliente, respeita o tom e o limite de 20 respostas, nunca inventa dado de imóvel, e o corretor assume uma conversa sem a IA interferir.

**Situação atual:**
- Pronto e testado: fila com agrupamento de rajadas, estados BOT/HUMANO/ENCERRADA, limite de 20, eco das mensagens da IA, contatos salvos, login Google, API do prompt, conversas, assumir e devolver, QR e geração do perfil de estilo (API).
- A validar contra a Evolution real: formato dos eventos do webhook (inclusive `contacts.upsert` e endereços `@lid`), do QR code e do histórico de mensagens.
- Falta: tela do painel (hoje só a API), geração automática do perfil de estilo ao conectar o número, limite de envios por minuto e alerta ao corretor se a sessão cair.
