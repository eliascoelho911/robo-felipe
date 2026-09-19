# ADR-022: Nuvem — xiaozhi-esp32-server com provedores pt-BR e comportamento do pet

## Status

Accepted

## Date

2026-08-31 (ajustada em 2026-09-19: o comportamento do pet passou a morar no próprio servidor — ver ADR-018)

## Context

As decisões anteriores fixaram o contorno completo do Robô Felipe: o
hardware é o M5Stack CoreS3 ([ADR-019](019-tamagotchi-hardware-m5stack-cores3.md));
o dispositivo é autocontido, sem relay, terminando TLS ele mesmo
([ADR-016](016-tamagotchi-processa-voz-sem-relay-de-smartphone.md)); a
câmera GC0308 e o LTR-553 estão no escopo
([ADR-017](017-tamagotchi-inclui-camera-gc0308.md)); o comportamento mora
no próprio xiaozhi-server
([ADR-018](018-tamagotchi-comportamento-mora-no-xiaozhi-server.md)); o OTA
é pull com esp32FOTA
([ADR-020](020-tamagotchi-ota-pull-com-esp32fota.md)); o firmware-base é o
`78/xiaozhi-esp32` com customizações
([ADR-021](021-tamagotchi-firmware-xiaozhi-esp32-com-customizacoes.md)).

Resta decidir **qual é a Nuvem** — i.e., qual servidor orquestra
ASR→LLM→TTS para o dispositivo, quais provedores de voz pt-BR usar, como
o comportamento do pet se integra ao pipeline de voz, e quem hospeda o
endpoint de visão.

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
5. **Plugins internos recebem `conn`; MCP externo não.** O servidor tem
   dois mecanismos de tools: plugins Python internos
   (`plugins_func/functions/*.py`, registrados via `@register_function`)
   que, quando `ToolType.SYSTEM_CTL`, recebem `conn: ConnectionHandler`
   — acesso ao WebSocket do device; e servidores MCP externos, que
   **não** recebem `conn` (ver emenda). Como as ações do pet não-verbais
   (dançar, expressar emoção, tontear) precisam do `conn`, o
   comportamento mora nos **plugins internos** — ver ADR-018.
6. **Memory** — `nomem` (default, sem memória de conversa). O estado do
   pet (stats/decay/estágios) mora no próprio servidor (ADR-018, ADR-023).
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

**Adotar o `xinnan-tech/xiaozhi-esp32-server` como Nuvem do Robô Felipe —
orquestrador de voz com provedores cloud pt-BR (GroqASR + gpt-4o-mini +
EdgeTTS + ChatGLMVLLM) e host do comportamento do pet (tools nativas +
handlers de trigger).** A Nuvem é auto-hospedada (PC do autor → VPS),
sempre-on, e fala HTTPS/WSS direto com o dispositivo (sem relay,
ADR-016).

### 1. Provedores — stack cloud pt-BR (~$0.09/mês)

| Slot | Provedor | Modelo | Custo | pt-BR | Notas |
|:--|:--|:--|:--|:--|:--|
| ASR | GroqASR | whisper-large-v3-turbo | free tier | multilíngue nativo | Groq LPU, baixa latência; fallback OpenaiASR ($0.003/min) |
| LLM | OpenAILLM | gpt-4o-mini | ~$0.09/mês | garantido | function calling nativo; 225k tokens in + 90k out/mês (premissa research) |
| TTS | EdgeTTS | pt-BR-FranciscaNeural | free | vozes Neural Microsoft | mesmas vozes do Azure, sem account/key; `voice` field configurável |
| VLLM | ChatGLMVLLM | glm-4v-flash | free | multimodal | endpoint `/mcp/vision/explain` no servidor; fallback Gemini/Qwen-VL/GPT-4o |
| VAD | SileroVAD | — | local | — | VAD no servidor, não no device |
| Memory | nomem | — | — | — | estado do pet mora no próprio servidor (ADR-023) |
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

### 2. Comportamento do pet — nativo no servidor

O comportamento do pet (estado, stats, decay, estágios, moods, tools)
é **código Python nativo do fork** (ADR-018), em dois pontos de
integração:

- **Voz (via LLM)** — as tools do pet são plugins internos
  (`plugins_func`, `ToolType.SYSTEM_CTL`). Quando o Sobrinho fala uma
  intent de ação, o LLM chama a tool via function calling; a tool muta
  o estado do pet localmente (sem HTTP para fora) e envia ações
  não-verbais ao device via `conn`
  (`conn.websocket.send({"type":"pet_action",...})`), retornando
  `ActionResponse(Action.REQLLM, context)` para o LLM compor a resposta
  textual que o EdgeTTS sintetiza.
- **Triggers não-vozeados (sem LLM)** — sacudida, botão, proximidade e
  RTC wake chegam como mensagens no protocolo WebSocket do xiaozhi,
  tratadas por um handler interno do fork: aplica decay on-demand,
  mapeia Trigger → Ação e devolve `pet_action` ao device.

O catálogo de tools, o modelo de stats e o mapeamento de triggers são
definidos na [ADR-023](023-tamagotchi-pet-vivo-estado-stats-decay-mcp-tools.md).
**Separação de responsabilidades**: o device orquestra percepção e
execução (detecta Triggers, transporta voz, anima a UI); o servidor
orquestra voz **e** comportamento; o LLM é a junção para o caminho
vozeado — recebe o transcript do ASR, decide se chama uma tool do pet,
e produz a resposta textual que o TTS sintetiza.

### 3. Visão — ChatGLMVLLM via xiaozhi-server

O `vision.url` (endpoint multimodal que o dispositivo chama via POST
multipart com a foto da GC0308, ADR-017) é **hospedado pelo
xiaozhi-server** em `/mcp/vision/explain`. O servidor usa o VLLM
configurado (`ChatGLMVLLM` / `glm-4v-flash`, free) para processar a
imagem e retornar uma descrição textual. O dispositivo recebe a URL do
endpoint via capabilities do servidor (handshake inicial WSS).

Isso fecha a lacuna do research `tamagotchi-visao-cam.md` ("definir na
ADR de nuvem quem hospeda o `vision.url`"): **o próprio xiaozhi-server
hospeda**, usando o slot VLLM plugável.

### 4. Hosting — um único serviço

A Nuvem é **um único serviço** (xiaozhi-server, Python, Docker) — PC do
autor no protótipo, VPS depois. Always-on.

- **Porta WSS (ex: 8000)** — voz (Opus) + mensagens do pet com o device.
- **Porta HTTP (ex: 8003)** — endpoint de visão.
- **Auth**: `server.auth.enabled = true` se VPS (whitelist de tokens de
  dispositivo). No PC do autor (LAN), pode manter `false`.

O dispositivo precisa de uma única URL (a do xiaozhi-server),
entregue no WiFi provisioning ou hardcoded no build do firmware. O
estado do pet persiste na persistência própria do servidor
(ADR-023), nos volumes do compose.

### 5. System prompt — persona Robô Felipe pt-BR

O `prompt` field do `config.yaml` substitui a persona default (garota
taiwanesa "小智") pela persona do Robô Felipe. O `language` field é
definido como `"Português do Brasil"` (sem ele, o template
`agent-base-prompt.txt` defaulta para 中文 e o EdgeTTS pt-BR falha).
O template injeta a persona, o idioma, data/hora e o contexto dinâmico
do estado do pet (ADR-023). Esboço (a refinar na implementação):

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
  incerta. Self-hosted permanece como rota se privacidade/offline for
  crítica ou se os provedores cloud mudarem de pricing.

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
  reconsiderado como adapter custom.

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

### Serviço de comportamento separado (Core TS + contrato)

- **Prós:** comportamento desacoplado da Nuvem; contrato versionado
  sobreviveria a trocas de Plataforma.
- **Contras:** segundo serviço always-on; hop HTTP e dedupe/versionamento
  de envelope como peso permanente; adapter de ponte eterna; com o
  Android removido, o contrato perdeu consumidor.
- **Rejeitada** (análise completa na [ADR-018](018-tamagotchi-comportamento-mora-no-xiaozhi-server.md)):
  o `conn` e o function calling já moram no servidor — hospedar o
  comportamento ali elimina a ponte em vez de reforçá-la.

## Consequences

### Positivas

- **Custo trivial** — ~$0.09/mês (apenas gpt-4o-mini). ASR, TTS e VLLM
  são free. Sustentável para um hobby sem monetização.
- **pt-BR garantido** — Whisper (multilíngue), gpt-4o-mini (pt-BR
  excelente), EdgeTTS (vozes Neural pt-BR). Nenhuma adaptação de
  idioma necessária.
- **Um único serviço** — voz, visão e comportamento no mesmo processo
  Docker; uma URL no device, um conjunto de config, um runtime.
- **`conn` nativo** — as tools do pet enviam ações não-verbais pelo
  mesmo WebSocket da voz, sem bridge HTTP entre serviços nossos.
- **Visão resolvida** — o `vision.url` é hospedado pelo próprio
  xiaozhi-server (slot VLLM), fechando a lacuna do research de visão.
- **Troca de provedores sem firmware** — ASR/LLM/TTS são config do
  servidor (`data/.config.yaml`). Trocar Groq→OpenaiASR ou EdgeTTS→Azure
  é editar YAML e reiniciar o servidor, sem tocar no dispositivo.

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
- **gpt-4o-mini requer API key OpenAI** — segredo de longo prazo. Custo
  ~$0.09/mês dentro das premissas (225k in + 90k out tokens/mês). Se o
  uso crescer (Sobrinho fala muito), custo escala suavemente.
- **ChatGLMVLLM free tier pode ter limites** — `glm-4v-flash` é
  gratuito, mas se a cota se esgotar, troca para Gemini
  (`gemini-1.5-flash`, free tier generoso), Qwen-VL ou GPT-4o (pago).
- **Comportamento acoplado ao fork** — merges do upstream
  `xiaozhi-esp32-server` passam por cima do código do pet. Mitigação:
  comportamento isolado em módulo próprio dentro do fork.
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
- **Consistente com ADR-018** (comportamento no server): os plugins
  internos com `conn` são o padrão de integração; a versão anterior
  desta ADR (Core como MCP/HTTP provider externo) foi substituída por
  esse desenho.
- **Consistente com ADR-021** (firmware): o firmware-base xiaozhi-esp32
  já fala o protocolo WSS+Opus com o xiaozhi-server. Zero adaptação de
  protocolo para voz; as mensagens `pet_action`/trigger são extensões
  do protocolo, definidas no fork e espelhadas no firmware.
- **"PET vivo"** (estado/stats/decay/estágios, catálogo de tools,
  triggers não-vozeados) está fechado na
  [ADR-023](023-tamagotchi-pet-vivo-estado-stats-decay-mcp-tools.md).
- **Hosting transição PC→VPS**: quando migrar de PC para VPS, habilitar
  `server.auth.enabled` e provisionar certificado TLS para o WSS
  (o device termina TLS, mas o servidor precisa do certificado).
  Detalhes de infra ficam para a implementação.
- **Referências**: research
  [`../research/cloud-voice-providers.md`](../research/cloud-voice-providers.md),
  [`../research/selfhosted-voice-providers.md`](../research/selfhosted-voice-providers.md),
  [`../research/tamagotchi-firmware-voz.md`](../research/tamagotchi-firmware-voz.md),
  [`../research/tamagotchi-visao-cam.md`](../research/tamagotchi-visao-cam.md);
  config.yaml do xiaozhi-esp32-server verificado em 2026-08-31 via
  GitHub API.

## Emenda (2026-08-31, ajustada em 2026-09-19)

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

### Consequência: tools do pet são plugins internos

A decisão original desta ADR (Core externo como MCP tool provider) e a
primeira emenda (Core HTTP + adapter Python de ponte) foram substituídas
pelo desenho atual ([ADR-018](018-tamagotchi-comportamento-mora-no-xiaozhi-server.md)):
**as tools do pet são plugins internos Python** (`ToolType.SYSTEM_CTL`,
com `conn`), que mutam o estado do pet localmente (no próprio servidor,
ADR-023) e enviam `pet_action` ao device pelo mesmo WebSocket da voz.
Nada do pet depende de MCP externo nem de HTTP entre serviços nossos.
