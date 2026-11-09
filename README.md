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

Ela roda em `http://localhost:8081`. Crie a instância nela (nome igual a `EVOLUTION_INSTANCIA`, padrão `bizu`) e configure o webhook para `http://host.docker.internal:8080/webhooks/evolution` com os eventos de mensagens e contatos e o header `apikey` igual a `WEBHOOK_SEGREDO`. Depois leia o QR code que o painel mostra em `GET /api/whatsapp`. Esses passos ainda não foram testados numa Evolution real.

Variáveis de ambiente:

| Variável | Para que serve |
|---|---|
| `BIZU_AGENTE` | `inativo` (padrão, não responde ninguém) ou `claude` |
| `ANTHROPIC_API_KEY` | chave da API da Anthropic (com `BIZU_AGENTE=claude`) |
| `WEBHOOK_SEGREDO` | segredo que a Evolution envia no header `apikey` do webhook |
| `EVOLUTION_URL` (padrão `http://localhost:8081`), `EVOLUTION_INSTANCIA`, `EVOLUTION_API_KEY` | acesso à Evolution API; a chave é a mesma usada no compose |
| `GOOGLE_CLIENT_ID`, `BIZU_EMAIL_CORRETOR` | login com Google e e-mail do corretor |
| `BIZU_JWT_SEGREDO` | assina a sessão do painel (mínimo de 32 caracteres; sem ele a aplicação não sobe) |
| `BIZU_PAINEL_ORIGEM` | endereço do painel permitido no CORS (padrão `http://localhost:3000`) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | conexão com o Postgres; `DB_PASSWORD` não tem padrão (em dev, use o perfil `dev`) |

Testes (os de integração usam um Postgres em contêiner e precisam do Docker):

```bash
cd backend
mvn verify
```

## API do painel

Todas exigem a sessão obtida em `POST /api/auth/google`.

| Rota | O que faz |
|---|---|
| `POST /api/auth/google` | troca o ID token do Google pela sessão do corretor |
| `GET` e `PUT /api/prompt` | lê e edita o prompt do agente |
| `GET /api/whatsapp` | mostra se o número está conectado ou o QR code |
| `GET /api/conversas` | lista as conversas com a qualificação do cliente |
| `POST /api/conversas/{id}/assumir` e `/devolver` | o corretor assume ou devolve a conversa |
| `POST /api/estilo/gerar` | gera o perfil de estilo do corretor |

## Estrutura

- `backend/`: aplicação Spring Boot, módulos `conversa` (núcleo), `whatsapp`, `agente` e `painel`.
- `doc/SPEC.md`: o que o produto deve fazer. `doc/ARQUITETURA.md`: decisões técnicas. `doc/PLANO.md`: escopo e situação atual.

## Situação

O backend está funcionando e testado. Ainda faltam a tela do painel (hoje só existe a API) e a validação com uma instância real da Evolution; veja `doc/PLANO.md`.
