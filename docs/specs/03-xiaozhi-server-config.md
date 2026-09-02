# Spec 03: xiaozhi-server config + API keys + docker

**Ticket:** [03 — xiaozhi-server config + API keys + docker](../tickets/03-xiaozhi-server-config-api-keys-docker.md)
**Status:** done

## Problem Statement

O Robô Felipe precisa de um orquestrador de voz (ASR→LLM→TTS) que fale
diretamente com o dispositivo via WSS/Opus, sem relay. O
`xinnan-tech/xiaozhi-esp32-server` (MIT, comunidade pt-BR) é a escolha
arquitetural (ADR-022), mas precisa ser configurado com provedores pt-BR,
persona do Robô Felipe, e API keys preenchidas. Sem isso, não há pipeline de
voz para o app Android se conectar.

## Solution

O fork do `xiaozhi-esp32-server` em `esp32-server/` é configurado via Docker
Compose com provedores cloud pt-BR (GroqASR + gpt-4o-mini + EdgeTTS +
ChatGLMVLLM), persona do Robô Felipe em pt-BR, exit_commands em pt-BR, e
`CORE_HTTP_URL` apontando para o Core. O servidor sobe via
`docker-compose.override.yml` e é demoable com `wscat` + push-to-talk manual.

## User Stories

1. As a desenvolvedor, I want o xiaozhi-server rodando via Docker Compose,
   so that eu possa subir a Nuvem com um comando.
2. As a desenvolvedor, I want `selected_module` configurado com GroqASR,
   OpenAILLM (gpt-4o-mini), EdgeTTS, ChatGLMVLLM, SileroVAD, nomem,
   function_call, so that a stack cloud pt-BR do ADR-022 esteja ativa.
3. As a desenvolvedor, I want a persona do Robô Felipe no `prompt` do
   config.yaml, so that o LLM responda como o pet de bolso pt-BR para o
   Sobrinho de 8 anos.
4. As a desenvolvedor, I want `exit_commands` em pt-BR (["tchau", "até
   mais", "até logo"]), so que o Sobrinho possa encerrar a conversa
   naturalmente.
5. As a desenvolvedor, I want `wakeup_words` vazio ou ["Felipe", "ei
   Felipe"], so que não interfira no fluxo push-to-talk do MVP (ADR-021).
6. As a desenvolvedor, I want `CORE_HTTP_URL` apontando para
   `http://host.docker.internal:3000`, so that o adapter Python possa
   chamar o Core do dentro do container.
7. As a desenvolvedor, I want API keys (OpenAI, Groq, ChatGLM) preenchidas
   em `data/.config.yaml` (não no config.yaml tracked), so that as chaves
   não entrem no git.
8. As a desenvolvedor, I want `server.auth.enabled = false` para MVP em LAN,
   so that eu não precise de tokens de dispositivo para testar localmente.
9. As a desenvolvedor, I want o handshake WSS em
   `ws://localhost:8000/xiaozhi/v1/`, so that o app Android possa conectar.
10. As a desenvolvedor, I want o endpoint de visão em
    `http://localhost:8003/mcp/vision/explain`, so que o device possa
    enviar fotos da câmera (Fase 2).
11. As a desenvolvedor, I want o servidor ser demoable com `wscat`, so that
    eu possa validar o handshake sem o app Android.
12. As a desenvolvedor, I want os logs de startup mostrando as URLs (WSS,
    visão, OTA, MCP endpoint), so that eu saiba onde apontar os clientes.
13. As a desenvolvedor, I want `docker compose config` validar o override
    sem erros, so that eu saiba que o YAML está correto antes de subir.

## Implementation Decisions

### Provedores (selected_module, ADR-022)

```yaml
selected_module:
  VAD: SileroVAD
  ASR: GroqASR           # whisper-large-v3-turbo, free tier, pt-BR multilíngue
  LLM: OpenAILLM         # gpt-4o-mini, ~$0.09/mês, function_call nativo
  VLLM: ChatGLMVLLM      # glm-4v-flash, free, visão multimodal
  TTS: EdgeTTS           # pt-BR-FranciscaNeural, free, vozes Neural Microsoft
  Memory: nomem           # estado do pet mora no Core (ADR-023)
  Intent: function_call   # gpt-4o-mini decide quando chamar tools
```

### Persona (prompt field)

Persona Robô Felipe pt-BR: pet de bolso conversacional, curioso, brincalhão,
carinhoso, para o Sobrinho de 8 anos. Frases curtas (máx. 12 palavras),
coloquial, child-safe. `language: "Português do Brasil"`.
`prompt_template: agent-base-prompt.txt` (template do xiaozhi com
`{{base_prompt}}`, `{{language}}`, `{{dynamic_context}}`).

### Exit commands e wakeup words

- `exit_commands: ["tchau", "até mais", "até logo"]` (pt-BR).
- `wakeup_words: ["Felipe", "ei Felipe"]` (preparação para Fase 2 com
  wake word treinada; no MVP push-to-talk, não afeta o fluxo).
- `enable_greeting: false` (não sauda ao detectar wake word — não há wake
  word ativa no MVP).

### CORE_HTTP_URL

`http://host.docker.internal:3000` — acessa o Core rodando no host a partir
do container Docker. `host.docker.internal` resolve para o IP do host no
Docker Desktop (Linux/Mac/Windows).

### API keys (em `data/.config.yaml`, não tracked)

- OpenAI (gpt-4o-mini LLM): `SUBSTITUA_POR_OPENAI_API_KEY`.
- Groq (GroqASR): `SUBSTITUA_POR_GROQ_API_KEY`.
- ChatGLM (ChatGLMVLLM visão): `SUBSTITUA_POR_CHATGLM_API_KEY`.
- EdgeTTS e SileroVAD: free, sem API key.

O config.yaml tracked usa placeholders; `data/.config.yaml` (gitignored)
contém as chaves reais. O servidor lê `.config.yaml` com prioridade sobre
`config.yaml`.

### Docker Compose

`esp32-server/docker-compose.override.yml` sobe o xiaozhi-server. Portas:
8000 (WSS voz), 8003 (HTTP visão+OTA), 8004 (MCP endpoint). Volume: config/
montado em `/opt/xiaozhi-esp32-server/config`. `host.docker.internal`
configurado para acesso ao Core no host.

### server.auth

`server.auth.enabled: false` para MVP em LAN. Se VPS futuro (ADR-022),
habilitar com whitelist de tokens de dispositivo.

### Handshake WSS

- Voz: `ws://localhost:8000/xiaozhi/v1/` — WebSocket para streaming de áudio
  Opus bidirecional.
- Visão: `http://localhost:8003/mcp/vision/explain` — POST multipart com foto
  + pergunta (Fase 2, ADR-017/022).
- OTA: `http://localhost:8003/xiaozhi/ota/` — endpoint de checagem de
  firmware (não usado no MVP Android, mas disponível para Fase 2 CoreS3).

### Logs de startup

O servidor deve logar as URLs ativas ao iniciar:
```
OTA接口是          http://localhost:8003/xiaozhi/ota/
视觉分析接口是     http://localhost:8003/mcp/vision/explain
mcp接入点是        ws://localhost:8004/mcp_endpoint/mcp/?token=...
Websocket地址是    ws://localhost:8000/xiaozhi/v1/
```

## Testing Decisions

### O que faz um bom teste

Config de servidor não tem teste unitário tradicional. A validação é:
(1) `docker compose config` valida o YAML; (2) startup sem erros valida
que dependências e config estão corretas; (3) `wscat` manual valida o
handshake WSS.

### Seams de teste

1. **`docker compose config`** — valida o `docker-compose.override.yml`
   sem subir containers. Deve retornar 0 erros.
2. **Startup logs** — após `docker compose up`, os logs devem mostrar as
   URLs ativas sem erros de config. Verificar: `selected_module` carregado,
   persona Robô Felipe ativa, `exit_commands` pt-BR, `CORE_HTTP_URL`
   acessível.
3. **`wscat` handshake manual** — conectar a
   `ws://localhost:8000/xiaozhi/v1/` com headers `Device-Id`, `Client-Id`,
   `Protocol-Version: 1`, enviar `{"type":"hello","version":1,"transport":
   "websocket","audio_params":{"format":"opus","sample_rate":16000,
   "channels":1,"frame_duration":60}}`, receber resposta `{"type":"hello",
   "transport":"websocket","session_id":"..."}`.

### Prior art

- `esp32-server/docker-compose.override.yml` já existe no scaffold.
- `esp32-server/config/config.yaml` já existe (120 linhas, config pt-BR
  completa com placeholders de API keys).

## Out of Scope

- Adapter Python (pet_tools.py) — Spec 04.
- App Android (WSS client) — Spec 05.
- OTA via xiaozhi-server (não usado no MVP Android; Fase 2 CoreS3 usa
  esp32FOTA, ADR-020).
- Visão funcional (câmera é Fase 2, ADR-017; endpoint existe mas não é
  exercitado no MVP Android).
- `server.auth.enabled = true` (MVP em LAN; VPS futuro).
- HA / replicação do xiaozhi-server (MVP single-instance).

## Further Notes

- **Paralelo com Spec 01** — não depende do Core; pode ser configurado e
  testado independentemente (embora o adapter Python precise do Core para
  testar function calling).
- **Config.yaml já existe** no scaffold (`esp32-server/config/config.yaml`,
  120 linhas) com config pt-BR completa e placeholders de API keys. Esta
  spec valida que está correto e documenta as decisões.
- **EdgeTTS** é uso não-oficial do serviço TTS do Microsoft Edge (free, sem
  SLA). Fallback: Azure TTS (mesmas vozes Neural pt-BR, pago, ADR-022).
- **Groq free tier** tem limites de rate. Se exceder, troca para OpenaiASR
  (`gpt-4o-mini-transcribe`, $0.003/min, ADR-022).
- **Referências:** ADR-022 (Nuvem xiaozhi-server + provedores pt-BR),
  ADR-021 (push-to-talk, wakeup_words), ticket 03.