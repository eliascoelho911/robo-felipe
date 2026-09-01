# 04: Adapter Python (pet_tools.py)

**What to build:** O adapter Python que integra o Core (HTTP REST) ao
xiaozhi-server é criado em
`esp32-server/upstream/main/xiaozhi-server/plugins_func/functions/pet_tools.py`.
Este adapter registra as 12 pet tools do ADR-023 como `ToolType.SYSTEM_CTL`
no function-calling do xiaozhi-server. O LLM (gpt-4o-mini) chama estas
tools via function calling quando reconhece intents de ação na fala do
Sobrinho.

O adapter é o **ponto de junção** entre voz (xiaozhi-server) e
comportamento (Core). Para cada tool:

- **Tool write** (`pet.feed`, `pet.play`, `pet.rest`, `pet.clean`,
  `pet.cuddle`, `pet.heal`, `pet.train`, `pet.dance`,
  `pet.express_emotion`, `pet.get_dizzy`): chama o Core via HTTP
  (`httpx.post(f"{CORE_HTTP_URL}/pet/{pet_id}/{tool}")`) → o Core muta
  stats, recalcula health, persiste e retorna o snapshot. O adapter então
  envia Ações não-verbais ao device via `conn.websocket.send({"type":
  "pet_action", "action": {"type": "dance", ...}})` (para dance,
  express_emotion, get_dizzy, sleep). Retorna `ActionResponse(Action.REQLLM,
  context)` para o LLM continuar a conversa com o estado atualizado.
- **Tool read** (`pet.get_state`, `pet.get_mood`): chama o Core via HTTP
  (`httpx.get(...)`) e retorna o estado/mood como contexto para o LLM.

O `pet_id` é obtido do `conn` (Device-Id ou session). O `CORE_HTTP_URL`
vem do config (já está em `config.yaml` como
`http://host.docker.internal:3000`).

O adapter também injeta o estado do pet no `dynamic_context` do
PromptManager: ao abrir uma conexão, busca o estado no Core e injeta no
template `{{dynamic_context}}` do `agent-base-prompt.txt` (ex.: "Estado
atual do Felipe: estágio=Jovem, mood=brincalhão, health=72, saciedade=45").
Isto permite ao LLM moldar respostas ao estado atual sem chamar
`get_state()` explicitamente a cada turno.

**Blocked by:** 01 (Core HTTP REST precisa estar rodando com as rotas
`/pet/:id/*`), 03 (xiaozhi-server precisa estar rodando para testar o
adapter in-place).

**Status:** in-review (PR #3)

- [x] `esp32-server/upstream/main/xiaozhi-server/plugins_func/functions/pet_tools.py` criado.
- [x] 12 tools registradas como `ToolType.SYSTEM_CTL` (2 read + 10 write).
- [x] Cada tool write chama Core via `httpx` e envia Ações não-verbais via `conn.websocket.send()`.
- [x] Cada tool read chama Core via `httpx` e retorna estado como contexto.
- [x] `dynamic_context` injeta estado do pet no PromptManager ao abrir conexão.
- [x] `pet_tools` adicionado à lista de functions no `Intent.function_call.functions` do config.
- [x] `httpx` já presente em `requirements.txt` (linha 17, `httpx==0.28.1`).
- [ ] Demoable: Core + xiaozhi-server rodando; LLM chama `pet.get_state()` via function calling e recebe JSON de estado.
- [ ] Demoable: LLM chama `pet.dance()` → adapter envia `{"type":"pet_action","action":{"type":"dance"}}` ao device (verificável no log do WebSocket) e retorna `ActionResponse(Action.REQLLM)`.
