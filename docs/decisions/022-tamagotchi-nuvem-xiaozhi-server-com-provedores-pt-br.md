# ADR-022: Nuvem — xiaozhi-esp32-server com provedores pt-BR e Core como MCP tool provider

## Status

Accepted

## Date

2026-08-31

## Context

As decisões anteriores fixaram o contorno completo do Robô Felipe: o
hardware é o M5Stack CoreS3 ([ADR-019](019-tamagotchi-hardware-m5stack-cores3.md));
o dispositivo é autocontido, sem relay, terminando TLS ele mesmo
([ADR-016](016-tamagotchi-processa-voz-sem-relay-de-smartphone.md)); a
câmera GC0308 e o LTR-553 estão no escopo
([ADR-017](017-tamagotchi-inclui-camera-gc0308.md)); o comportamento mora
num Core em TypeScript auto-hospedado
([ADR-018](018-tamagotchi-comportamento-mora-no-core-typescript.md)); o OTA
é pull com esp32FOTA
([ADR-020](020-tamagotchi-ota-pull-com-esp32fota.md)); o firmware-base é o
`78/xiaozhi-esp32` com customizações
([ADR-021](021-tamagotchi-firmware-xiaozhi-esp32-com-customizacoes.md)).

Resta decidir **qual é a Nuvem** — i.e., qual servidor orquestra
ASR→LLM→TTS para o dispositivo, quais provedores de voz pt-BR usar, como o
Core se integra ao pipeline de voz, e quem hospeda o endpoint de visão.

O research [`../research/tamagotchi-firmware-voz.md`](../research/tamagotchi-firmware-voz.md)
recomenda o `xinnan-tech/xiaozhi-esp32-server` (MIT, README pt-BR) como
Nuvem auto-hospedada e plugável. Os research de provedores de voz
([`cloud-voice-providers.md`](../research/cloud-voice-providers.md) e
[`selfhosted-voice-providers.md`](../research/selfhosted-voice-providers.md))
avaliaram dezenas de provedores ASR/LLM/TTS para pt-BR. O research de
visão ([`tamagotchi-visao-cam.md`](../research/tamagotchi-visao-cam.md))
deixou em aberto quem hospeda o `vision.url` multimodal — esta ADR fecha
essa lacuna.

### Achados da inspeção do xiaozhi-esp32-server (2026-08-31)

O `config.yaml` do servidor (inspecionado via GitHub API + `webfetch`,
~16KB) usa um padrão `selected_module` com slots:
`VAD`/`ASR`/`LLM`/`VLLM`/`TTS`/`Memory`/`Intent`. Cada slot aponta para
um provedor nomeado, configurável em `data/.config.yaml`. Achados
decisivos:

1. **GroqASR é nativo** — `type: openai`, `base_url: api.groq.com`,
   modelo `whisper-large-v3-turbo`. Free tier do Groq LPU. Whisper é
   multilíngue — transcreve pt-BR sem configuração de idioma.
   **Deepgram NÃO é nativo** (protocolo WebSocket diferente, exigiria
   adapter custom).
2. **EdgeTTS é nativo e gratuito** — `type: edge`, usa as mesmas vozes
   Neural da Microsoft que o Azure Cognitive Services (incluindo
   `pt-BR-FranciscaNeural`, `pt-BR-AntonioNeural`, etc.), **sem Azure
   account ou API key**. O servidor converte o áudio MP3/WAV para Opus
   24kHz internamente (formato do protocolo device↔server). **Azure TTS
   NÃO é nativo** (mas EdgeTTS entrega a mesma qualidade gratuitamente).
3. **LLM via `type: openai`** — qualquer endpoint OpenAI-compatível.
   `gpt-4o-mini` funciona diretamente. ChatGLMLLM (`glm-4-flash`, free)
   também disponível, mas qualidade de pt-BR incerta (LLM chinês).
4. **VLLM nativo** — `ChatGLMVLLM` (`glm-4v-flash`, **FREE**) e
   `QwenVLVLLM`. Qualquer endpoint OpenAI-compatível. O servidor hospeda
   o endpoint de visão (`/mcp/vision/explain`) que o dispositivo chama
   via POST multipart — **fecha a lacuna do `vision.url`** do research
   de visão.
5. **MCP endpoint nativo** — o servidor expõe
   `ws://host:8004/mcp_endpoint/mcp/?token=...`. Servidores MCP externos
   conectam aqui e registram **tools** que o LLM pode chamar via function
   calling (exemplos nativos: `get_time`, `play_music`, `calculator`,
   `get_weather`). **É o ponto natural de integração do Core (TS)** como
   provedor de tools de pet (`pet.dance()`, `pet.express_emotion()`,
   `pet.get_state()`, etc.).
6. **Memory** — `nomem` (default, sem memória de conversa). O estado do
   pet (stats/decay/estágios) mora no Core (ADR-018), não no servidor.
7. **Intent** — `function_call` (default, o próprio LLM decide quando
   chamar tools via function calling nativo). `gpt-4o-mini` suporta
   function calling.
8. **`prompt` field** — define a persona do LLM (default: garota
   taiwanesa "小智"). Substituível por persona Robô Felipe pt-BR.
   Template `agent-base-prompt.txt` injeta `{{base_prompt}}`,
   `{{language}}`, `{{current_time}}`, etc.
9. **`exit_commands`** — `["退出", "关闭"]` (chinês). Trocar para
   `["tchau", "até logo", "adeus"]`.
10. **`server.auth`** — `enabled: false` por default. Deve ser
    habilitado se o servidor for exposto à internet (VPS).
11. **Áudio** — `format: opus, sample_rate: 24000, channels: 1,
    frame_duration: 60`. Device↔server comunicam via Opus 24kHz sobre
    WSS/TLS.

## Decision

**Adotar o `xinnan-tech/xiaozhi-esp32-server` como orquestrador de voz da
Nuvem, com provedores cloud pt-BR (GroqASR + gpt-4o-mini + EdgeTTS +
ChatGLMVLLM), e o Core (TypeScript) integrado como MCP tool provider.**
A Nuvem é auto-hospedada (PC do autor → VPS), sempre-on, e fala HTTPS/WSS
direto com o dispositivo (sem relay, ADR-016).

### 1. Provedores — stack cloud pt-BR (~$0.09/mês)

| Slot | Provedor | Modelo | Custo | pt-BR | Notas |
|:--|:--|:--|:--|:--|:--|
| ASR | GroqASR | whisper-large-v3-turbo | free tier | multilíngue nativo | Groq LPU, baixa latência; fallback OpenaiASR ($0.003/min) |
| LLM | OpenAILLM | gpt-4o-mini | ~$0.09/mês | garantido | function calling nativo; 225k tokens in + 90k out/mês (premissa research) |
| TTS | EdgeTTS | pt-BR-FranciscaNeural | free | vozes Neural Microsoft | mesmas vozes do Azure, sem account/key; `voice` field configurável |
| VLLM | ChatGLMVLLM | glm-4v-flash | free | multimodal | endpoint `/mcp/vision/explain` no servidor; fallback Gemini/Qwen-VL/GPT-4o |
| VAD | SileroVAD | — | local | — | VAD no servidor, não no device |
| Memory | nomem | — | — | — | estado do pet mora no Core (ADR-018) |
| Intent | function_call | — | — | — | gpt-4o-mini decide quando chamar tools |

Custo total: **~$0.09/mês** (apenas gpt-4o-mini; demais free). Latência
alvo: ~1.5-2.5s (ASR+LLM+TTS), abaixo do alvo de 4s do research.

Configuração `selected_module` no `config.yaml`:

```yaml
selected_module:
  VAD: SileroVAD
  ASR: GroqASR
  LLM: OpenAILLM
  VLLM: ChatGLMVLLM
  TTS: EdgeTTS
  Memory: nomem
  Intent: function_call
```

### 2. Core como MCP tool provider

O Core (TypeScript, ADR-018) conecta-se ao **MCP endpoint** do
xiaozhi-server (`ws://host:8004/mcp_endpoint/mcp/?token=...`) e registra
tools de pet que o LLM chama via function calling quando reconhece
intents de ação:

- `pet.dance()` → Core retorna Plano de Ações com `dançar{...}` →
  dispositivo executa a animação.
- `pet.express_emotion(emotion)` → Core retorna `expressar_emocao{...}`.
- `pet.get_state()` → Core retorna estado atual (mood, stats, estágio)
  como contexto para o LLM moldar a resposta.
- `pet.feed()`, `pet.play()`, `pet.sleep()` → atualizam stats no Core.

As tools específicas (nomes, parâmetros, schemas) serão definidas na ADR
"PET vivo" (próxima). Esta ADR estabelece o **padrão de integração**
(MCP), não o catálogo de tools.

**Separação de responsabilidades**: o xiaozhi-server orquestra voz
(ASR→LLM→TTS, streaming Opus, protocolo WSS com o device); o Core
orquesta comportamento (estado do pet, Planos de Ações, stats/decay). O
LLM é o ponto de junção — recebe o transcript do ASR, decide se chama
uma tool do Core (function calling), e produz a resposta textual que o
TTS sintetiza.

### 3. Arquitetura de duas fases

O Robô Felipe tem dois hosts de Plataforma (ADR-018): Android
(protótipo de laboratório, hoje) → CoreS3 (produto, futuro). A Nuvem
acompanha essa transição:

**Fase 1 — Android protótipo (hoje, sem CoreS3)**:

```
App Android ──HTTPS/Batch──▶ Core (TS) ──HTTPS──▶ GroqASR + gpt-4o-mini
                                     │
                                     └──Plano de Ações──▶ App Android
                                                            │
                                                            └──TTS nativo pt-BR
```

O Core orquestra a Nuvem diretamente: recebe o Batch (com áudio ou
transcript), chama GroqASR (se áudio) + gpt-4o-mini, retorna Plano de
Ações (`falar{texto}`). O app Android renderiza TTS nativo pt-BR
(ADR-018). **Sem xiaozhi-server** — o Core assume a orquestração de voz.
A divisão de trabalho entre app (faz ASR local?) e Core (faz ASR via
Groq?) fica para a implementação.

**Fase 2 — CoreS3 (futuro, com hardware em mãos)**:

```
CoreS3 ──WSS/Opus──▶ xiaozhi-server ──▶ GroqASR → gpt-4o-mini → EdgeTTS
  │                        │                        │
  │                        │                        └──chama tools──▶ Core (MCP)
  │                        │                        ◀──Plano/Ações───│
  │                        ◀──Opus (TTS)────────────│
  │
  └──HTTPS/Batch──▶ Core (triggers não-vozeados: sacudida, botão, proximidade)
```

O xiaozhi-server orquestra voz (WSS + Opus + ASR/LLM/TTS). O Core conecta
ao MCP endpoint do servidor e expõe tools de pet. Para triggers
não-vozeados (sacudida/botão/proximidade/RTC), o dispositivo envia o
Batch direto ao Core via HTTPS — não passa pelo pipeline de voz.

O código de ASR/LLM calling do Core (Fase 1) torna-se **deprecated** na
Fase 2 — o xiaozhi-server assume. Mas as **decisões de provedores**
(Groq, OpenAI, EdgeTTS) são as mesmas em ambas as fases.

### 4. Visão — ChatGLMVLLM via xiaozhi-server

O `vision.url` (endpoint multimodal que o dispositivo chama via POST
multipart com a foto da GC0308, ADR-017) é **hospedado pelo
xiaozhi-server** em `/mcp/vision/explain`. O servidor usa o VLLM
configurado (`ChatGLMVLLM` / `glm-4v-flash`, free) para processar a
imagem e retornar uma descrição textual. O dispositivo recebe a URL do
endpoint via capabilities do servidor (handshake inicial WSS).

Isso fecha a lacuna do research `tamagotchi-visao-cam.md` ("definir na
ADR de nuvem quem hospeda o `vision.url`"): **o próprio xiaozhi-server
hospeda**, usando o slot VLLM plugável.

### 5. Hosting — mesmo box (Core + xiaozhi-server)

O Core (Node/Bun, TypeScript) e o xiaozhi-server (Python) rodam no
**mesmo host** — PC do autor no protótipo, VPS depois. Ambos always-on.

- **Core**: porta HTTPS (ex: 3000) para receber Batches do dispositivo.
- **xiaozhi-server**: porta WSS (ex: 8000) para voz + porta HTTP (ex:
  8003) para visão + porta WS (ex: 8004) para MCP endpoint.
- **Auth**: `server.auth.enabled = true` se VPS (whitelist de tokens de
  dispositivo). No PC do autor (LAN), pode manter `false`.

O dispositivo precisa de duas URLs configuradas (NVS ou build config):
(a) URL do xiaozhi-server (WSS, para voz) e (b) URL do Core (HTTPS, para
triggers não-vozeados). Ambas entregues durante o WiFi provisioning ou
hardcoded no build do firmware.

### 6. System prompt — persona Robô Felipe pt-BR

O `prompt` field do `config.yaml` substitui a persona default (garota
taiwanesa "小智") pela persona do Robô Felipe. O `language` field é
definido como `"Português do Brasil"`. O template
`agent-base-prompt.txt` injeta a persona, o idioma, data/hora e contexto
dinâmico. Esboço (a refinar na implementação):

```
Você é o Felipe, um cachorro-robô brincalhão e leal. Você é um
Tamagotchi — um pet de bolso conversacional para o Sobrinho (8 anos).
Responda em português do Brasil, coloquial, frases curtas (máx. 12
palavras). Você é curioso, divertido e um pouco trapalhão. Sempre gentil.
```

`exit_commands`: `["tchau", "até logo", "adeus"]` (encerra sessão de
voz). `wakeup_words`: vazio ou removido (push-to-talk, ADR-021 — não há
wake word para stripar do transcript). `enable_greeting`: `false` (não
falar "olá" ao detectar wake word, pois não há wake word).

## Alternatives Considered

### Cloud 100% grátis (ChatGLMLLM glm-4-flash em vez de gpt-4o-mini)

- **Prós:** $0/mês; function calling nativo; servidor chinês com README
  pt-BR.
- **Contras:** `glm-4-flash` é um LLM chinês — qualidade de pt-BR
  incerta (pode gerar texto em pt-PT ou misturar estruturas sintáticas
  do chinês). Para uma criança de 8 anos, a qualidade da linguagem é
  crítica.
- **Rejeitada:** ~$0.09/mês por gpt-4o-mini é trivial comparado ao
  risco de pt-BR de baixa qualidade. `glm-4v-flash` (VLLM) é mantido
  como free porque visão é secundária e multimodal é menos sensível ao
  idioma.

### Self-hosted (SherpaASR + Ollama Qwen2.5-3B + Piper TTS)

- **Prós:** $0/mês (se box já on); privacidade total; funciona offline.
- **Contras:** **Piper NÃO é nativo do xiaozhi-server** — exigiria
  adapter custom (TTS adapter interface). SherpaASR é nativo, mas
  Whisper local num i5/8GB adiciona ~1-2s de latência. Piper pt-BR
  (`pt_BR-cadu-medium`) é voz masculina adulta, não combina com pet.
  Qualidade de Qwen2.5-3B Q4 em pt-BR não validada.
- **Rejeitada para MVP:** mais trabalho (adapter Piper) e qualidade
  incerta. Self-hosted permanece como rota futura se privacidade/offline
  for crítica ou se os provedores cloud mudarem de pricing.

### Híbrido (cloud primária + self-hosted fallback)

- **Prós:** resiliência; offline fallback.
- **Contras:** manter duas stacks configuradas e testadas; complexidade
  operacional alta; fallback só relevante se cloud cair (raro).
- **Rejeitada para MVP:** adicionar complexidade antes de validar a stack
  primária. O modo degradado offline (ADR-016) já cobre o caso sem
  internet com comandos fixos + TTS baixa qualidade.

### Deepgram Nova-3 (ASR)

- **Prós:** melhor latência ASR do mercado (~300ms); keyterm boosting
  ("Felipe"); WS streaming.
- **Contras:** **NÃO é nativo do xiaozhi-server** — protocolo WebSocket
  diferente do esperado pelo servidor. Exigiria adapter custom (não há
  `type: deepgram`). Custo ~$0.0043/min (dentro do budget, mas não free).
- **Rejeitada:** GroqASR (Whisper, free, nativo) é alternativa superior
  para MVP. Se latência for problema na prática, Deepgram pode ser
  reconsiderado como adapter custom futuro.

### Azure Neural TTS (TTS)

- **Prós:** SLA empresarial; vozes Neural pt-BR (mesmas do Edge); SSML
  (prosódia, pitch).
- **Contras:** **NÃO é nativo do xiaozhi-server** (exigiria adapter
  custom). Requer Azure account + API key. Free tier 500k chars/mês
  (suficiente, mas com limite).
- **Rejeitada:** EdgeTTS entrega as **mesmas vozes Neural pt-BR**
  gratuitamente, sem account/key/limite. Se Microsoft bloquear o EdgeTTS
  (uso não-oficial), troca para Azure TTS (mesmas vozes, pago) — mas
  como fallback, não como primária.

### Core como LLM endpoint (Core recebe transcript, chama LLM, retorna texto)

- **Prós:** Core controla toda a lógica; uma única porta.
- **Contras:** Core só retorna texto (não Plano de Ações multi-ação);
  indireção extra (ASR→Core→LLM→Core→TTS em vez de ASR→LLM→TTS);
  latência adicional; Core precisa reimplementar function calling.
- **Rejeitada:** o padrão MCP tool provider (Core expõe tools, LLM chama
  quando precisa) é **separação limpa** — o xiaozhi-server orquestra voz,
  o Core orquestra comportamento, o LLM é a junção. Sem indireção.

## Consequences

### Positivas

- **Custo trivial** — ~$0.09/mês (apenas gpt-4o-mini). ASR, TTS e VLLM
  são free. Sustentável para um hobby sem monetização.
- **pt-BR garantido** — Whisper (multilíngue), gpt-4o-mini (pt-BR
  excelente), EdgeTTS (vozes Neural pt-BR). Nenhuma adaptação de
  idioma necessária.
- **Separação limpa** — xiaozhi-server orquestra voz; Core orquestra
  comportamento; LLM é a junção via function calling. Cada componente
  tem uma responsabilidade.
- **MCP nativo** — o xiaozhi-server já tem MCP endpoint; o Core só
  conecta e registra tools. Sem glue custom de protocolo.
- **Visão resolvida** — o `vision.url` é hospedado pelo próprio
  xiaozhi-server (slot VLLM), fechando a lacuna do research de visão.
- **Troca de provedores sem firmware** — ASR/LLM/TTS são config do
  servidor (`data/.config.yaml`). Trocar Groq→Deepgram ou EdgeTTS→Azure
  é editar YAML e reiniciar o servidor, sem tocar no dispositivo.
- **Stack idêntica nas duas fases** — GroqASR + gpt-4o-mini + EdgeTTS
  em Fase 1 (Core direto) e Fase 2 (xiaozhi-server). Só muda quem
  orquestra.

### Negativas

- **Requer internet** — sem conectividade, não há voz conversacional. O
  modo degradado offline (ADR-016) usa comandos fixos + TTS baixa
  qualidade + animações de display.
- **EdgeTTS é uso não-oficial** — o serviço TTS do Microsoft Edge é
  gratuito e estável há anos (a biblioteca `edge-tts` tem milhões de
  downloads), mas **sem SLA**. Se a Microsoft bloquear o uso não-oficial,
  troca para Azure TTS (mesmas vozes Neural pt-BR, pago, ~$0.16/1k chars
  — ~$0.67/mês nas premissas do research).
- **Groq free tier tem limites** — rate limits de requests/minuto e
  requests/dia. Se exceder (improvável com ~50 cmds/dia), troca para
  OpenaiASR (`gpt-4o-mini-transcribe`, $0.003/min — ~$0.30/mês).
- **gpt-4o-mini requer API key OpenAI** — secreto longo prazo. Custo
  ~$0.09/mês dentro das premissas (225k in + 90k out tokens/mês). Se o
  uso crescer (Sobrinho fala muito), custo escala suavemente.
- **ChatGLMVLLM free tier pode ter limites** — `glm-4v-flash` é
  gratuito, mas se a cota se esgotar, troca para Gemini
  (`gemini-1.5-flash`, free tier generoso), Qwen-VL ou GPT-4o (pago).
- **Dois serviços para hostear** — Core (Node/Bun) + xiaozhi-server
  (Python) no mesmo box. Complexidade operacional: dois runtimes, dois
  processos para monitorar, dois conjuntos de config. Mitigação: Docker
  Compose ou systemd units.
- **xiaozhi-server é Python** — mais um runtime além do Node/Bun do
  Core. Dependências Python (venv/pip), compatibilidade de versões.
- **Código throwaway na Fase 1** — o Core implementa chamadas a
  GroqASR + gpt-4o-mini (Fase 1) que se tornam desnecessárias quando o
  xiaozhi-server assume (Fase 2). Esforço de implementação que não
  sobrevive à transição. Mitigação: manter o código de Nuvem do Core
  isolado num módulo, facilitando a remoção.
- **MCP tools específicas ficam para a próxima ADR** — esta ADR
  estabelece o padrão (MCP), não o catálogo de tools. Os schemas de
  `pet.dance()`, `pet.get_state()`, etc. são definidos na ADR "PET vivo".
- **`server.auth.enabled` deve ser true em VPS** — se o servidor for
  exposto à internet, auth por token deve ser habilitada (whitelist de
  tokens de dispositivo). No PC do autor (LAN), pode manter desabilitado.

## Notas

- **Não supersede ADR-006** (ASR/TTS remotos via nuvem, histórico do
  bípede/quadrúpede). ADR-006 decidiu que ASR/TTS são remotos; esta ADR
  **concretiza** os provedores para o Tamagotchi (mesma arquitetura,
  provedores específicos).
- **Concretiza o research** `tamagotchi-firmware-voz.md` (ADOPT
  xiaozhi-server) e `tamagotchi-visao-cam.md` (ADOPT VLLM via servidor).
  Refina `cloud-voice-providers.md` (Deepgram→GroqASR, Azure
  TTS→EdgeTTS) — as recomendações de provedor do research são
  substituídas pelas escolhas nativas do xiaozhi-server.
- **Consistente com ADR-018** (Core em TS): o Core como MCP tool
  provider é o "caminho anotado" na Nota do ADR-018 ("o Core pode
  expor ferramentas MCP para integração com o xiaozhi-esp32-server").
  Esta ADR decide esse caminho.
- **Consistente com ADR-021** (firmware): o firmware-base xiaozhi-esp32
  já fala o protocolo WSS+Opus com o xiaozhi-server. Zero adaptação de
  protocolo no firmware.
- **"PET vivo"** (estado/stats/decay/estágios, persistência cloud vs
  NVS, `advanceStats` no wake do RTC, catálogo de MCP tools do pet) fica
  como tópico aberto — **próxima ADR pendente**.
- **Hosting transição PC→VPS**: quando migrar de PC para VPS, habilitar
  `server.auth.enabled`, configurar TLS no Core (Let's Encrypt) e no
  xiaozhi-server (WSS já termina TLS no dispositivo, mas o servidor
  precisa de certificado). Detalhes de infra ficam para a implementação.
- **Referências**: research
  [`../research/cloud-voice-providers.md`](../research/cloud-voice-providers.md),
  [`../research/selfhosted-voice-providers.md`](../research/selfhosted-voice-providers.md),
  [`../research/tamagotchi-firmware-voz.md`](../research/tamagotchi-firmware-voz.md),
  [`../research/tamagotchi-visao-cam.md`](../research/tamagotchi-visao-cam.md);
  config.yaml e docs/mcp-endpoint-integration.md do xiaozhi-esp32-server
  verificados em 2026-08-31 via GitHub API.

## Emenda (2026-08-31)

### Descoberta: MCP externo não tem `conn`

A inspeção do código do `xinnan-tech/xiaozhi-esp32-server` (realizada
durante o planejamento da implementação, 2026-08-31) revelou que o
servidor tem **dois tipos de tools**:

1. **Plugins internos Python** (`plugins_func/functions/*.py`):
   registrados via `@register_function(name, desc, ToolType)`. Quando o
   `ToolType` é `SYSTEM_CTL`, o plugin recebe `conn: ConnectionHandler`
   — acesso ao WebSocket do device (fila de TTS, device-id, config,
   logger, diálogo). É assim que plugins como `play_music` enviam TTS e
   `call_device` controlam o device.
2. **Servidores MCP externos** (`mcp_server_settings.json`): o
   xiaozhi-server é **cliente MCP** — conecta a servidores externos via
   stdio/SSE/streamable-http. **Servidores MCP externos NÃO recebem
   `conn`** — não têm acesso ao WebSocket do device.

A decisão original (§2) dizia que o Core (TS) se conectaria ao MCP
endpoint do xiaozhi-server como servidor MCP externo. Isso **não
funciona** para ações não-TTS (`dançar`, `expressar_emocao`,
`ficar_tonto`): o Core não conseguiria enviar essas ações ao device,
pois MCP externo não tem `conn`.

### Solução: Core HTTP + adapter Python interno

O Core é um **HTTP server** (Hono, REST), não um MCP server. Um
**adapter Python** interno no xiaozhi-server
(`plugins_func/functions/pet_tools.py`) registra cada tool de pet como
`ToolType.SYSTEM_CTL`:

- Chama o Core via HTTP (`httpx`) para lógica de estado (stats, decay,
  Plano de Ações).
- Usa `conn` para enviar ações não-verbais ao device via
  `conn.websocket.send({"type":"pet_action","action":"dance",...})`.
- Retorna `ActionResponse(Action.REQLLM, context, None)` — o LLM gera o
  texto da resposta, o EdgeTTS sintetiza.

O adapter Python é **permanente** (sobrevive Fase 1→Fase 2 — mesmo
xiaozhi-server, mesmo pattern). O Core é **permanente** (pet state +
lógica). Só o device muda (Android → CoreS3).

### O que muda em cada seção

- **Título**: "Core como MCP tool provider" → "Core como HTTP server +
  adapter Python interno".
- **§2**: O Core não conecta ao MCP endpoint. O Core é HTTP server; o
  adapter Python no xiaozhi-server bridgeia as tools ao LLM.
- **§3 (Arquitetura de duas fases)**: **Fase 1 = Fase 2** com Android
  como device. O xiaozhi-server é incluído desde o início (zero código
  throwaway). O app Android fala WSS+Opus com o xiaozhi-server e envia
  Batch via HTTPS ao Core para triggers não-vozeados. O diagrama Fase 1
  original (Core orquestra Nuvem direto, sem xiaozhi-server) é
  **descartado**.
- **§5 (Hosting)**: removida a menção a "porta WS (ex: 8004) para MCP
  endpoint". O Core expõe HTTP na porta 3000; o adapter Python chama
  `http://host.docker.internal:3000` de dentro do contêiner.
- **Consequências**: "MCP nativo" → "Adapter Python interno com `conn`".
- **Notas**: a consistência com ADR-018 ("o Core pode expor ferramentas
  MCP") é refinada — o Core expõe ferramentas via HTTP, bridgeadas pelo
  adapter Python.

### O que NÃO muda

- **Provedores** (§1): GroqASR + gpt-4o-mini + EdgeTTS + ChatGLMVLLM —
  idênticos.
- **Visão** (§4): `vision.url` hospedado pelo xiaozhi-server — idêntico.
- **System prompt** (§6): persona Robô Felipe pt-BR — idêntico.
- **selected_module**: VAD/ASR/LLM/VLLM/TTS/Memory/Intent — idênticos.
- **Triggers não-vozeados**: sempre foram Batch→Core via HTTPS,
  independente do padrão MCP/HTTP.
