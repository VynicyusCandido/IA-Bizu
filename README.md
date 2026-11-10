# Bizu

Atendente de IA no WhatsApp para corretor de imóveis. A IA faz o primeiro atendimento em nome do corretor, descobre o que o cliente procura e entrega a conversa pronta quando o cliente está pronto, quando pede uma pessoa ou quando as 20 respostas acabam.

Esta versão atende **um corretor, com um número de WhatsApp**, só com mensagens de texto em português do Brasil.

## O que o projeto cobre

- **Conexão do número:** o corretor lê um QR code e o número fica conectado (Evolution API).
- **Atendimento em nome do corretor:** a IA responde cada cliente seguindo um prompt que o corretor edita. Usa o estilo de escrita dele quando há perfil e um tom formal quando não há. Mensagens em rajada viram uma resposta só.
- **Qualificação do cliente:** a IA descobre finalidade (compra, venda ou aluguel), região, faixa de valor e prazo, e registra o imóvel que o cliente citar.
- **Limite de 20 respostas por conversa:** depois da 20ª a IA fica em silêncio, sem aviso ao cliente, e a conversa segue com o corretor. Isso também limita o custo.
- **O corretor assume quando quiser:** escrevendo do próprio celular ou pelo painel, a IA para de responder naquela conversa. Ele também pode devolvê-la.
- **Só clientes novos:** contatos salvos na agenda e números com quem o corretor já conversou nunca são atendidos pela IA.
- **Estilo do corretor:** o sistema aprende o jeito dele escrever a partir das mensagens que ele mesmo enviou, sem usar conversas de clientes.
- **Painel protegido:** login com a conta Google do corretor; nenhuma outra conta entra.

A IA nunca inventa preço, metragem ou disponibilidade de imóvel. Se perguntarem, ela diz que o corretor confirma e passa a conversa. Ela também não afirma ser uma pessoa a quem pergunta diretamente se está falando com uma IA.

## Como funciona

```
Cliente <-> WhatsApp <-> Evolution API --webhook--> Bizu (Spring Boot) <-> Postgres
                                                       |
                                                       +--> Claude (API da Anthropic)
```

1. A Evolution avisa o Bizu de cada mensagem por webhook (protegido por segredo).
2. O Bizu grava a mensagem e a coloca numa fila; mensagens seguidas do mesmo cliente são agrupadas.
3. Uma conversa por vez, o agente (Claude) gera a resposta, registra a qualificação e o Bizu envia pelo WhatsApp.
4. Se o corretor escreve do celular, a conversa passa para ele; o eco das respostas da própria IA é descartado.

## Tecnologia

Java 21, Spring Boot 4, Spring Modulith, Spring JDBC, Liquibase, Postgres, Evolution API, SDK Java da Anthropic.

## Como rodar

Pré-requisitos: Java 21, Maven e Docker.

```bash
cd backend
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

O `compose.yaml` sobe só o Postgres (banco e usuário `bizu`, senha `2222`, só para uso local). O perfil `dev` (`application-dev.yml`) é o único lugar com essa senha; sem ele o backend exige `DB_PASSWORD`. O backend roda na máquina, na porta 8080. Se você já tinha um volume antigo do Postgres, recrie-o com `docker compose down -v`.

Para conectar um WhatsApp de verdade, suba também a Evolution API (o conector que liga o número por QR code), que fica à parte:

```bash
export EVOLUTION_API_KEY=escolha-uma-chave
docker compose -f compose.evolution.yaml up -d
```

Ela roda em `http://localhost:8081`. Cada corretor tem a própria instância (`<EVOLUTION_INSTANCIA>-<auth id>`), que o Bizu cria sozinho na primeira vez que ele abre a tela Conexão, já com o webhook apontando para `BIZU_WEBHOOK_URL` e o header `apikey` igual a `WEBHOOK_SEGREDO`. Esses passos ainda não foram testados numa Evolution real.

Variáveis de ambiente:

| Variável | Para que serve |
|---|---|
| `BIZU_AGENTE` | `inativo` (padrão, não responde ninguém) ou `claude` |
| `ANTHROPIC_API_KEY` | chave da API da Anthropic (com `BIZU_AGENTE=claude`) |
| `WEBHOOK_SEGREDO` | segredo que a Evolution envia no header `apikey` do webhook |
| `EVOLUTION_URL` (padrão `http://localhost:8081`), `EVOLUTION_INSTANCIA`, `EVOLUTION_API_KEY` | acesso à Evolution API; a chave é a mesma usada no compose; `EVOLUTION_INSTANCIA` é só o prefixo do nome da instância de cada corretor |
| `BIZU_WEBHOOK_URL` | endereço deste backend visto de dentro do contêiner da Evolution (padrão `http://host.docker.internal:8080/webhooks/evolution`) |
| `GOOGLE_CLIENT_ID` | login com Google (o ID do cliente OAuth) |
| `BIZU_JWT_CHAVE_ARQUIVO` | caminho da chave privada RSA (PEM) que assina a sessão do painel; sem ela a aplicação não sobe, a não ser no perfil `dev` (chave nova a cada partida) |
| `BIZU_EMAIL_DONO`, `BIZU_EMAIL_REMETENTE` | quem recebe o aviso de conta nova esperando liberação, e o remetente do e-mail |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | servidor SMTP; sem `SPRING_MAIL_HOST` não sai e-mail e o aviso fica só no log |
| `BIZU_PAINEL_ORIGEM` | endereço do painel permitido no CORS (padrão `http://localhost:3000`) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | conexão com o Postgres; `DB_PASSWORD` não tem padrão (em dev, use o perfil `dev`) |

Testes (os de integração usam um Postgres em contêiner e precisam do Docker):

```bash
cd backend
mvn verify
```

## Corretores: quem pode usar

Cada corretor entra com a própria conta Google, e os dados dele (conversas, prompt, estilo, número) ficam isolados dos outros. A conta é criada no **primeiro login**, mas nasce **inativa**: o login responde 403 `AGUARDANDO_LIBERACAO` e o painel mostra "Conta aguardando liberação". Quem libera é o dono do sistema, por enquanto direto no Postgres (o painel de administração ficou para depois):

```sql
-- quem está esperando
select auth_id, email, nome, criado_em from corretor where not ativo order by criado_em;

-- liberar, com assinatura de 30 dias (assinatura_ate nulo = sem vencimento)
update corretor set ativo = true, assinatura_ate = now() + interval '30 days' where auth_id = '<auth_id>';

-- renovar
update corretor set assinatura_ate = assinatura_ate + interval '30 days' where auth_id = '<auth_id>';

-- desligar na hora (a sessão aberta também deixa de valer)
update corretor set ativo = false where auth_id = '<auth_id>';
```

O `auth_id` é um código próprio do Bizu (UUID gerado na criação da conta) que nunca muda e identifica o corretor em tudo; o `sub` do Google fica em outra coluna (`google_sub`). O login acha a conta pelo `google_sub`; contas antigas, em que o `sub` era o próprio `auth_id`, continuam valendo e passam a guardar o `google_sub`. Nenhum dos dois é aceito como prova de identidade: quem prova é o token do Google, e depois a sessão assinada pelo backend. Assinatura: o painel avisa 72 h antes de vencer; depois de vencer a IA responde por mais 24 h (carência) e então **pausa** (o painel continua ativo, as conversas não mudam de estado e a IA volta quando o cliente escrever de novo depois da renovação). Quando uma conta nova aparece, o backend registra uma linha de aviso no log e, com SMTP e `BIZU_EMAIL_DONO` configurados, manda um e-mail ao dono com o comando para liberar. O aviso ao corretor sobre a própria assinatura por e-mail ainda não existe.

**Teto de uso da IA:** cada corretor tem um teto mensal de respostas (padrão em `bizu.corretor.limite-mensal-respostas`, 3000). Ao atingir, a IA pausa até o mês seguinte (UTC) e o painel avisa. Para mudar o de um corretor: `update corretor set limite_respostas_mes = 5000 where auth_id = '<auth_id>';` (nulo volta ao padrão).

Para qualquer conta Google chegar ao login, o app no Google Cloud precisa estar com público **Externo** e **publicado** (Google Auth Platform > Público-alvo > Publicar app). Em modo de teste só entram os e-mails cadastrados como usuários de teste.

## Quais conversas aparecem, e quais a IA atende

O painel mostra **toda conversa individual** do WhatsApp do corretor (grupos não). Só a IA tem regra de quem atende:

| Situação do contato | Estado no painel | A IA responde? |
|---|---|---|
| Número novo, que o corretor nunca atendeu | IA respondendo | sim |
| Número que o corretor atendeu ou assumiu | Com você | não, até ele devolver |
| Contato salvo na agenda | IA não atende | nunca |
| Contato que o WhatsApp só identifica por código (`@lid`), sem telefone | IA não atende | nunca (por enquanto) |

Para trazer o que já existia, use o botão de histórico (ícone de relógio) no topo da lista de Conversas, ou "Importar a última semana" quando a lista está vazia. A importação lê o que a Evolution guardou, mantém a data original de cada mensagem, mostra mídia como marcador (`[áudio]`, `[imagem]`...) e **nunca faz a IA responder retroativamente**.

## Sessão do painel: par de chaves

A sessão (JWT) é assinada com RS256. Só o backend tem a chave privada; qualquer outro serviço (catálogo, dashboard) verifica a sessão pela chave pública em `/.well-known/jwks.json` e não consegue emitir sessões. Para produção, gere o par uma vez e guarde o arquivo fora do repositório:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out bizu-sessao.pem
export BIZU_JWT_CHAVE_ARQUIVO=/caminho/seguro/bizu-sessao.pem
```

No perfil `dev` não precisa de arquivo: a chave é nova a cada partida, e as sessões caem ao reiniciar (o painel volta ao login).

## Painel (front-end)

Next.js, shadcn/ui (paleta zinc), ícones Lucide, modo claro e escuro. Telas: Conversas (lista com prévia e conversa aberta, com Assumir e Devolver à IA), Agente (instruções e estilo) e Conexão (QR code do WhatsApp).

```bash
cd frontend
cp .env.example .env.local   # preencha NEXT_PUBLIC_GOOGLE_CLIENT_ID (o mesmo GOOGLE_CLIENT_ID do backend)
npm install
npm run dev                  # http://localhost:3000
```

No Google Cloud, em "Origens JavaScript autorizadas" do cliente OAuth, cadastre `http://localhost:3000`. Outros comandos: `npm run lint`, `npm test`, `npm run build`.

## API do painel

Todas exigem a sessão obtida em `POST /api/auth/google`.

| Rota | O que faz |
|---|---|
| `POST /api/auth/google` | troca o ID token do Google pela sessão; conta nova ou inativa recebe 403 `AGUARDANDO_LIBERACAO` |
| `GET /api/conta` | nome, e-mail e situação da assinatura (`ATIVA`, `AVISO`, `CARENCIA`, `VENCIDA`) |
| `GET /api/conversas/{id}/mensagens` | histórico da conversa |
| `GET` e `PUT /api/prompt` | lê e edita o prompt do agente |
| `GET /api/whatsapp` | só diz se o número está conectado (sem efeito colateral; a barra do painel usa) |
| `POST /api/whatsapp/conectar` | cria a instância do corretor na primeira vez e devolve o QR code (a tela Conexão usa) |
| `GET /.well-known/jwks.json` (aberta) | chave pública que verifica as sessões, para outros serviços |
| `GET /actuator/health` (aberta) | saúde da aplicação, para monitoramento |
| `GET /api/conversas` | lista as conversas com a qualificação do cliente, o nome do contato e a prévia da última mensagem |
| `POST /api/conversas/importar` | traz para o painel as conversas da última semana que já existiam no WhatsApp; pode repetir sem duplicar |
| `POST /api/conversas/{id}/assumir` e `/devolver` | o corretor assume ou devolve a conversa |
| `POST /api/estilo/gerar` | gera o perfil de estilo do corretor |

## Estrutura

- `backend/`: aplicação Spring Boot, módulos `corretor` e `conversa` (núcleo), `whatsapp`, `agente` e `painel`.
- `frontend/`: painel em Next.js.
- `doc/SPEC.md`: o que o produto deve fazer. `doc/ARQUITETURA.md`: decisões técnicas. `doc/PLANO.md`: escopo e situação atual.

## Situação

Backend e painel funcionando e testados, com vários corretores isolados. Ainda faltam a validação com uma instância real da Evolution, o aviso de assinatura por e-mail, o painel de administração e o grupo de corretores (imobiliária); veja `doc/PLANO.md`.
