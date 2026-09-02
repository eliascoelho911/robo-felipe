# Spec 04: Adapter Python (pet_tools.py)

**Ticket:** [04 — Adapter Python pet_tools.py](../tickets/04-adapter-python-pet-tools.md)
**Status:** done
**Blocked by:** 01 (Core HTTP REST), 03 (xiaozhi-server config)

## Problem Statement

O xiaozhi-server orquestra a voz (ASR→LLM→TTS) mas não sabe nada sobre o
estado do pet — stats, mood, estágio, health. O LLM (gpt-4o-mini) precisa
chamar tools (`pet.feed()`, `pet.get_state()`, `pet.dance()`) para mutar e
consultar o pet, mas o Core (TS/Bun, ADR-018) é um serviço HTTP separado. Sem
um adapter, o LLM não tem como acessar o pet, e o device não recebe ações
não-verbais (dance, express_emotion) geradas pelas tools. O MCP externo
(original da ADR-022) não funciona porque não tem acesso ao `conn` do
xiaozhi-server — impossibilitando enviar `pet_action` ao device.

## Solution

Um adapter Python (`pet_tools.py`) no xiaozhi-server registra as 12 pet tools
do ADR-023 como `ToolType.SYSTEM_CTL` no function-calling. O LLM chama as
tools via function calling; o adapter traduz cada chamada em HTTP requests ao
Core e, para tools write, envia ações não-verbais ao device via
`conn.websocket.send()`. O adapter também injeta o estado do pet no
`{{dynamic_context}}` do system prompt ao abrir a conexão, para o LLM moldar
respostas ao estado atual sem chamar `get_state()` a cada turno.

## User Stories

1. As a LLM (gpt-4o-mini), I want chamar `pet.get_state()` via function
   calling, so that eu saiba o estado atual do pet (mood, stats, estágio) para
   moldar minha resposta.
2. As a LLM, I want chamar `pet.get_mood()` via function calling, so that eu
   receba apenas o mood atual como string curta para contexto rápido.
3. As a LLM, I want chamar `pet.feed()` via function calling, so that eu
   alimente o pet e receba o estado atualizado para continuar a conversa.
4. As a LLM, I want chamar `pet.play()` via function calling, so que o pet
   brinque e o device execute a animação de dança.
5. As a LLM, I want chamar `pet.rest()` via function calling, so que o pet
   descanse e o device mostre a animação de dormir.
6. As a LLM, I want chamar `pet.clean()` via function calling, so que a
   higiene do pet melhore.
7. As a LLM, I want chamar `pet.cuddle()` via function calling, so que o
   afeto entre o pet e o Sobrinho aumente.
8. As a LLM, I want chamar `pet.heal()` via function calling, so que a
   sickness do pet diminua.
9. As a LLM, I want chamar `pet.train()` via function calling, so que a
   inteligência e maturidade do pet aumentem.
10. As a LLM, I want chamar `pet.dance()` via function calling, so que o
    device execute a animação de dança e a felicidade do pet aumente.
11. As a LLM, I want chamar `pet.express_emotion(emotion)` via function
    calling, so que o device mostre uma expressão facial específica.
12. As a LLM, I want chamar `pet.get_dizzy()` via function calling, so que
    o device execute a animação de "ficar tonto".
13. As a Sobrinho, I want o LLM saber que estou com fome sem eu dizer, so
    que o pet diga "estou com um pouquinho de fome" naturalmente — via
    `dynamic_context` injetado no system prompt.
14. As a desenvolvedor do adapter, I want chamar o Core via `httpx` (HTTP
    síncrono), so que eu possa usar `conn.websocket.send()` para enviar
    ações ao device — impossível com MCP externo.
15. As a desenvolvedor do adapter, I want o `pet_id` obtido do `conn`
    (Device-Id), so que cada device tenha seu pet no Core.
16. As a desenvolvedor do adapter, I want o `CORE_HTTP_URL` lido do config
    do xiaozhi-server, so que o adapter não tenha URL hardcoded.
17. As a desenvolvedor do adapter, I want `httpx` nas dependências do
    xiaozhi-server, so que o adapter possa fazer chamadas HTTP ao Core.
18. As a desenvolvedor, I want `pet_tools` na lista de `functions` do
    `Intent.function_call` no config, so que o adapter seja carregado pelo
    xiaozhi-server.
19. As a desenvolvedor, I want o adapter ser demoable com Core +
    xiaozhi-server rodando, so that eu valide o function calling end-to-end.
20. As a desenvolvedor, I want o log do WebSocket mostrar
    `{"type":"pet_action","action":{"type":"dance"}}` quando o LLM chama
    `pet.dance()`, so that eu confirme que ações não-verbais chegam ao
    device.

## Implementation Decisions

### Arquitetura do adapter

O adapter é um plugin Python no xiaozhi-server que:

1. **Registra 12 tools** como `ToolType.SYSTEM_CTL` (tem acesso ao `conn`).
2. **Tool write** (10 tools): chama Core via `httpx.POST /pet/:id/:tool` →
   Core muta stats, recalcula health, persiste, retorna snapshot → adapter
   envia ações não-verbais ao device via `conn.websocket.send()` → retorna
   `ActionResponse(Action.REQLLM, context, None)` para o LLM continuar.
3. **Tool read** (2 tools): chama Core via `httpx.GET /pet/:id/state` (ou
   `/mood`) → retorna `ActionResponse(Action.REQLLM, state_summary, None)`.
4. **dynamic_context**: ao abrir conexão, busca estado no Core e injeta no
   `{{dynamic_context}}` do template `agent-base-prompt.txt`.

### 12 tools (alinhadas ao ADR-023)

**Read (2):**
- `pet.get_state()` → `GET /pet/:id/state` → retorna snapshot completo.
- `pet.get_mood()` → `GET /pet/:id/mood` → retorna `{mood: string}`.

**Write (10):**
- `pet.feed(food?)` → `POST /pet/:id/feed` → envia `express_emotion{happy}`
  ao device.
- `pet.play(game?)` → `POST /pet/:id/play` → envia `dance` ao device.
- `pet.rest()` → `POST /pet/:id/rest` → envia `sleep` ao device.
- `pet.clean()` → `POST /pet/:id/clean` → envia `express_emotion{comfort}`
  ao device.
- `pet.cuddle()` → `POST /pet/:id/cuddle` → envia `express_emotion{affection}`
  ao device.
- `pet.heal()` → `POST /pet/:id/heal` → envia `express_emotion{happy}` ao
  device.
- `pet.train(skill?)` → `POST /pet/:id/train` → sem ação não-verbal (só
  stats mudam; LLM gera texto).
- `pet.dance()` → `POST /pet/:id/dance` → envia `dance` ao device.
- `pet.express_emotion(emotion)` → `POST /pet/:id/express_emotion` (body
  `{emotion}`) → envia `express_emotion{emotion}` ao device.
- `pet.get_dizzy()` → `POST /pet/:id/get_dizzy` → envia `get_dizzy` ao
  device.

### Padrão de ActionResponse

O xiaozhi-server usa `ActionResponse` para sinalizar ao LLM o que fazer
após a tool executar:

- `Action.REQLLM` — LLM deve gerar texto (resposta falada) com o contexto
  atualizado. O adapter retorna o snapshot/estado como `context` para o
  LLM moldar a resposta.
- `Action.NONE` — sem resposta do LLM (não usado nas pet tools; todas
  retornam `REQLLM` para o pet "falar" algo após a ação).

### Ações não-verbais via `conn.websocket.send()`

Para tools write que produzem animações no device, o adapter envia um JSON
via WebSocket:

```json
{"type": "pet_action", "action": {"type": "dance", "duration_ms": 3000}}
{"type": "pet_action", "action": {"type": "express_emotion", "emotion": "happy"}}
{"type": "pet_action", "action": {"type": "get_dizzy"}}
{"type": "pet_action", "action": {"type": "sleep", "duration_ms": 5000}}
```

O device (app Android Fase 1, CoreS3 Fase 2) recebe esse JSON e executa a
animação correspondente. `speak` não é enviado via `pet_action` — o `speak`
no fluxo de voz vem do LLM → EdgeTTS → Opus (stream de áudio), não do
adapter.

### dynamic_context

Ao abrir uma conexão WSS, o adapter busca o estado do pet no Core
(`GET /pet/:id/state`) e serializa para o formato do `{{dynamic_context}}`
do template `agent-base-prompt.txt`:

```
Estado atual do Felipe: estágio=Jovem, mood=brincalhão, health=72,
saciedade=45 (com fome leve), felicidade=80. Última interação: 2h atrás.
```

Isto é injetado no `PromptManager` do xiaozhi-server para que o LLM receba
o estado como contexto inicial sem precisar chamar `get_state()` no
primeiro turno.

### pet_id

O `pet_id` é obtido do `conn` (Device-Id do handshake WSS). No MVP, há um
único pet (`felipe-tamagotchi`, configurado em `CORE_PET_ID` no Core), mas
o adapter usa o Device-Id do conn para futura multi-pet. Se o Device-Id
não corresponder a um pet no Core, o Core cria um novo pet com esse ID.

### CORE_HTTP_URL

Lido do config do xiaozhi-server (já em `config.yaml` como
`http://host.docker.internal:3000`). O adapter não tem URL hardcoded.

### Registro no config

`pet_tools` é adicionado à lista `Intent.function_call.functions` no
`config.yaml` do xiaozhi-server:

```yaml
Intent:
  function_call:
    functions:
      - pet_tools
      # ... outras tools nativas
```

### Dependência httpx

`httpx` é adicionado às dependências do xiaozhi-server (`requirements.txt`
ou equivalente). Se já presente (xiaozhi-server pode usar httpx em outros
lugares), apenas garantir que está disponível.

### Tratamento de erros

- **Core offline**: se o `httpx` call ao Core falhar (ConnectionError), o
  adapter retorna `ActionResponse(Action.REQLLM, "Core indisponível", None)`
  — o LLM gera uma resposta de "estou meio indisposto agora" sem crashar.
- **Timeout**: timeout de 5s no httpx. Se exceder, mesmo comportamento que
  Core offline.
- **404 pet não encontrado**: o adapter ignora (o Core cria o pet na
  primeira chamada se não existir — implícito no `POST /pet/:id/:tool`).

## Testing Decisions

### O que faz um bom teste

Testar que o adapter faz as coisas certas (chama Core com URL certa, envia
`pet_action` ao device, retorna `ActionResponse` correto) sem depender do
Core real ou do xiaozhi-server real. Mocks de `httpx` e `conn`.

### Módulos testados

- **`test_pet_tools.py`** (pytest): testa cada tool isoladamente com
  `httpx` mockado (responde com snapshot fake) e `conn` mockado (captura
  `websocket.send` calls).
  - Tool write: verifica que `httpx.post` foi chamado com URL+tool certa,
    que `conn.websocket.send` foi chamado com `pet_action` JSON esperado,
    que `ActionResponse` tem `Action.REQLLM` e contexto correto.
  - Tool read: verifica que `httpx.get` foi chamado, que `ActionResponse`
    tem estado como contexto.
  - Erro: Core offline → `ActionResponse` com mensagem de indisponível.
- **dynamic_context**: testa que a função de injeção busca estado no Core
  e formata a string de contexto corretamente.

### Prior art

- xiaozhi-server tem plugins existentes (calculator, weather, play_music)
  como referência de padrão de tool registration e `ActionResponse`.
- pytest com mocks é padrão Python; `httpx` tem `respx` para mockar HTTP
  ou usar `unittest.mock.patch`.

## Out of Scope

- App Android (WSS client, pet_action handler) — Specs 05, 06.
- Core HTTP REST (endpoints) — Spec 01.
- xiaozhi-server config (selected_module, persona) — Spec 03.
- Multi-pet (vários pet_ids) — MVP tem um pet; adapter suporta mas não
  testa multi-pet.
- Visão (VLLM, `Explain()`) — Fase 2; adapter não toca em visão.
- `speak` no `pet_action` — `speak` no fluxo de voz vem do LLM → EdgeTTS,
  não do adapter. O adapter só envia ações não-verbais.

## Further Notes

- **Adapter como ponto de junção**: o adapter é o único componente que
  conecta voz (xiaozhi-server) e comportamento (Core). Sem ele, o LLM não
  sabe do pet e o device não recebe animações.
- **`Action.REQLLM` em todas as tools**: o LLM sempre gera texto após a
  tool executar — o pet "fala" algo contextual. O adapter não gera texto;
  só fornece contexto para o LLM gerar.
- **`pet_action` vs `speak`**: `pet_action` (via WebSocket JSON) leva ações
  não-verbais (dance, express_emotion, get_dizzy, sleep). `speak` (texto
  falado) vem do LLM → EdgeTTS → Opus → device (stream de áudio), não do
  adapter. Esta separação é o "speak dual-path" da ADR-022.
- **Referências:** ADR-022 (Nuvem, adapter Python interno + Core HTTP),
  ADR-023 (12 pet tools, ActionResponse), ticket 04.