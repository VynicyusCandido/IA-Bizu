# Plano MVP: IA que responde WhatsApp com portfólio

**Objetivo:** provar que a IA responde clientes só com o portfólio cadastrado, sem inventar, e passa a um humano o que não souber.

**Escopo (um negócio, só texto):**
1. Conectar número de teste por QR code.
2. Cadastrar itens (nome, descrição, preço/condições, disponibilidade) e FAQ.
3. IA responde só com esses dados; sem base = "não sei" ou transfere.
4. Painel simples: ver conversas, assumir uma (IA para) e devolver.

**Fora:** multi-cliente, documentos/PDF, áudio, agendamento, vários atendentes, API oficial.

**Regras:**
- Nunca inventar preço, prazo ou condição.
- Usar número de teste (QR não oficial pode banir).

**Pronto quando:** em 30 perguntas (20 cobertas, 10 fora), ao menos 18 cobertas certas e as 10 fora viram "não sei"/transferência, 0 invenção; uma conversa é assumida e devolvida sem a IA interferir.

**A decidir:** qual LLM e limite de custo por conversa; critérios de transferência além de "cliente pede humano" e "sem base".
