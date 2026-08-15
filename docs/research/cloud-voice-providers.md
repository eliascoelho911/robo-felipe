# Provedores de Voz em Nuvem para o Robô Felipe

> **Ticket de pesquisa** que subsidia a decisão de produto adiada em
> [ADR-006](../decisions/006-asr-e-tts-remotos-via-nuvem.md) (escolha concreta
> de ASR / NLP / TTS). A arquitetura (relay smartphone, PCM 16k/16-bit/mono,
> KWS local) já está decidida; este documento cobre **qual provedor de nuvem
> usar em cada etapa da pipeline**.

| | |
|---|---|
| **Data da pesquisa** | 2026-08-12 |
| **Status** | Concluída — aguardando decisão de produto |
| **Alimenta** | ADR-006 (provedor), ADR-002 (relay) |
| **Confiança de preço** | Alta para ASR/TTS (páginas oficiais fetchadas em 2026-08-12). **Preços de nuvem mudam — re-verificar antes de amarrar decisão.** Gemini LLM não foi fetchado (timeout); ver seção de verificação. |

## Metodologia e fontes

Preços e APIs verificados em **fontes primárias oficiais** (fetchadas em
2026-08-12): páginas de pricing de Google Cloud STT/TTS, Azure Speech,
Deepgram, ElevenLabs, OpenAI platform e Anthropic; e docs de API via
Context7 (OpenAI, Deepgram, ElevenLabs). Onde houve timeout/JS-render (Azure
pay-go, Gemini), marco explicitamente como **estimado — verificar**.

## Perfil de uso (premissas)

- **~50 comandos/dia**, uso intermitente (hobby), 30 dias → **1.500
  comandos/mês**.
- **ASR:** enunciado ~4 s → **~100 min de áudio/mês**.
- **LLM:** prompt ~150 tokens (system + transcrição) + resposta ~60 tokens
  (intent + fala PT-BR curta) → **225k tokens in / 90k tokens out por mês**.
- **TTS:** resposta ~50 palavras ≈ **280 chars** → **~420k chars/mês**
  (arredondado p/ 0,45 M).
- **Alvo de latência:** < 4 s ponta-a-ponta ("Hey Felipe, [comando]" → voz).
- **Idioma:** PT-BR (não PT-PT). Usuário: criança de 8 anos (voz aguda,
  vocabulário simples, sotaque possivelmente irregular).

---

## 1. ASR (Speech-to-Text)

### Tabela comparativa

| Provedor / modelo | PT-BR | Latência | Custo (PAYG) | Streaming | Integração Android/Kotlin | Free tier |
|---|---|---|---|---|---|---|
| **Deepgram Nova-3** | ✅ `language=pt-BR`, 45+ idiomas; keyterm boost ("Felipe") | **Sub-300 ms** (streaming) | **$0.0048/min** (mono); multilíngue $0.0058/min; keyterm +$0.0013/min; smart format incl. | ✅ **WebSocket** `wss://api.deepgram.com/v1/listen`; aceita **`linear16` 16 kHz** direto (zero transcode do PCM do relay) | 🟢 **Mínima**: WebSocket cru, OkHttp. Repassa PCM direto. | **$200 crédito**, sem cartão, sem expiração |
| **Google Cloud STT** (Chirp / V2) | ✅ 125+ idiomas, `pt-BR`; `speechContext` p/ vocabulário | Streaming bidi (~300–500 ms); batch dinâmico mais lento | $0.016/min (standard, até 500k min); **dynamic batch $0.003/min** | ✅ gRPC bidi streaming + REST | 🟡 Média: gRPC/REST, auth por API key/SA, conversão p/ Opus/FLAC | **60 min/mês free** (V1, c/ data-logging); V2 sem free tier explícito |
| **Azure Speech STT** | ✅ **Excelente pt-BR**; **Custom Speech** adaptável a voz de criança (acústico + LM) | Streaming (~300–500 ms) | ~**$1/h ≈ $0.0167/min** (pay-go, JS-rendered — verificar) | ✅ **SDK Android** nativo streaming (push stream) | 🟡 SDK nativo robusto (`com.microsoft.cognitiveservices.speech`), mas +~30 MB; lida c/ formato | **5 h/mês free** (300 min) — cobre o uso hobby com folga |
| **OpenAI Whisper** (`whisper-1` / `gpt-4o-mini-transcribe`) | ✅ Multilíngue (99 idiomas, detecta `pt`); tende a PT-BR via prompt | **Batch**: ~1–2 s p/ enunciado de 3 s; `stream=true` p/ deltas parciais | **Whisper $0.006/min**; **gpt-4o-mini-transcribe $0.003/min** (mais barato); live-transcribe $0.017/min | ⚠️ REST multipart (batch); `stream=true` em `/audio/transcriptions` p/ deltas; **Realtime API** WS p/ live | 🟡 Fácil: REST multipart, OkHttp. Relay envia arquivo após fim da fala (latência maior) | **Sem free tier** de API |

### Notas por provedor (ASR)

- **Deepgram** é o melhor encaixe técnico para a pipeline do Robô Felipe: o
  relay já tem **PCM 16 kHz/16-bit/mono** (ADR-006) e Deepgram aceita
  `encoding=linear16&sample_rate=16000` **diretamente** — **zero conversão
  de formato** no relay. `endpointing` + `interim_results=true` + `vad_events`
  dão detecção de fim-de-fala para disparar o LLM cedo. `keywords`/`keyterm`
  aumenta precisão de "Felipe" e comandos de movimento.
- **Azure** tem o melhor catálogo de PT-BR e permite treinar um **Custom Speech
  Model** com áudio de criança PT-BR — a rota de maior precisão para voz
  infantil, ao custo de coletar um dataset. Tem **SDK Android oficial**, o
  que reduz código de streaming/formato mas adiciona peso ao APK.
- **Google** é sólido e barato em dynamic-batch, mas batch penaliza latência;
  o streaming bidi exige gRPC e conversão p/ Opus/FLAC no relay.
- **Whisper** é multilíngue robusto e o `gpt-4o-mini-transcribe` é o **mais
  barato** ($0.003/min), mas é **batch-centric**: o relay precisa esperar a
  fala terminar, subir o arquivo e esperar a transcrição inteira — **risca o
  alvo de < 4 s**. Whisper também tem um problema conhecido de **alucinação
  em silêncio** (inventa "Obrigado.", "Tchau." etc.) — mitigável com VAD local
  (já existe, ADR-005) e `condition_on_previous_text=false`.

---

## 2. NLP / LLM (intenção + resposta PT-BR)

### Tabela comparativa

| Provedor / modelo | PT-BR | Latência (TTFT) | Custo (por 1M tokens in/out) | Streaming | Integração | Free tier |
|---|---|---|---|---|---|---|
| **Classificador de intents por regra** (local no relay) | ✅ Totalmente controlável | **< 10 ms** | **$0** (roda no app) | — (síncrono) | 🟢 Regex/keywords em Kotlin | **Ilimitado** (gratuito) |
| **OpenAI gpt-4o-mini** | ✅ Excelente PT-BR | ~300–500 ms | **$0.15 in / $0.60 out** (= **~$0.09/mês** neste volume) | ✅ `stream=true` (SSE) | 🟢 REST/SSE, OkHttp | Sem free tier; **moderation API grátis** (`omni-moderation-latest`) |
| **OpenAI gpt-5-nano** (atual mais barato) | ✅ Bom PT-BR | ~300–500 ms | **$0.05 in / $0.40 out** (~$0.05/mês) | ✅ | 🟢 | Sem free tier |
| **Google Gemini Flash** | ✅ Bom PT-BR | ~300–500 ms | **Muito baixo** (estimado; **página de pricing deu timeout — verificar**) | ✅ | 🟡 REST/gRPC; auth Google | **Free tier historicamente generoso** (RPM/dia p/ Flash) — verificar |
| **Anthropic Claude Haiku 4.5** | ✅ Excelente PT-BR; **mais safety-tuned** (bom p/ criança) | ~400–600 ms | **$1 in / $5 out** (~$0.68/mês) — 7× o gpt-4o-mini, porém ainda barato | ✅ `stream=true` | 🟡 REST/SSE | Sem free tier recorrente (crédito de boas-vindas; verificar) |

### Notas (NLP/LLM)

- **Recomendado: híbrido.** Um **classificador por regra** (regex/keywords sobre
  a transcrição) resolve os intents de movimento conhecidos — "ande/anda",
  "dança/dançar", "senta/sentar", "vira/vire", "pula", "late/latir",
  "brinca/brincar" — em **< 10 ms e de graça**, emitindo o comando de servo
  direto ao ESP32 (sem LLM). Só chama o LLM para **chit-chat e intents não
  reconhecidos** ("Felipe, por que você é tão fofinho?"). Isso corta custo e
  latência e torna o robô robusto offline para comandos fixos.
- O LLM emite **JSON estruturado** `{intent, action, response}`:
  `action` → comando de servo p/ o ESP32; `response` → texto p/ TTS. Ex. de
  prompt-system em PT-BR: *"Você é o Felipe, um cachorro-robô brincalhão.
  Responda em português do Brasil, coloquial, frases curtas (máx. 12
  palavras), adequado a criança de 8 anos. Sempre devolva JSON
  {"action": "...", "response": "..."}."*.
- **Para uma criança de 8 anos, segurança de conteúdo pesa.** Claude
  (Constitutional AI) e Gemini (filtros de segurança) são os mais
  conservadores; OpenAI tem a **moderation API gratuita** para pós-filtrar.
  Um system prompt rígido + moderation é suficiente; não justifica sozinho o
  custo 7× maior do Claude, mas é um argumento legítimo.
- Todos os LLMs custam **<$1/mês** neste volume — **qualidade/segurança > custo
  aqui.** A decisão é por ecossistema e segurança, não por preço.

---

## 3. TTS (Text-to-Speech)

### Tabela comparativa

| Provedor / modelo | PT-BR | Latência | Custo (PAYG) | Streaming | Integração | Free tier |
|---|---|---|---|---|---|---|
| **Azure Neural TTS** | ✅ **Maior catálogo pt-BR** (Francisca, Antonio, Brenda, Donato, Elza, Fábio, Giovanna, Humberto, Júlio, Manuela, Paulo, Rafael, Thalita, Valério + estilo *casual*/amigável) | Streaming via SDK (~300–500 ms p/ 1º áudio) | ~**$16/1M chars** (Neural; pay-go JS-rendered — verificar) | ✅ SDK Android (streaming nativo) | 🟡 SDK nativo; lida c/ formato | **500k chars/mês free** — **cobre o uso (~420k)** ⚠️ |
| **Google Cloud TTS** | ✅ pt-BR (WaveNet-C/D, Neural2, Studio, Chirp3-HD) | Streaming (REST `synthesize`); Gemini-TTS token-stream | **Standard/WaveNet $4/1M** (4M chars free); Neural2 $16/1M (1M free); Chirp3-HD $30/1M (sem free); Gemini-2.5-Flash-TTS $0.50/$10 por 1M tokens (25 tok/s) | ✅ REST; Gemini-TTS streaming | 🟡 REST, auth Google, conversão de formato | **4M chars/mês free (Standard/WaveNet)**; 1M free (Neural2) |
| **OpenAI TTS** (`tts-1`, `tts-1-hd`, `gpt-4o-mini-tts`) | ✅ Multilíngue, PT-BR; ~6–8 vozes fixas (alloy/nova/shimmer/echo/onyx/fable/coral/sage) — **sem voz "de criança"** | Streaming (chunked HTTP) ~300–500 ms | **tts-1 $15/1M chars** ($0.015/1k); tts-1-hd $30/1M; gpt-4o-mini-tts p/ token (~$5–8/mês) | ✅ `stream=true` (chunked) | 🟢 REST simples, OkHttp | **Sem free tier** |
| **ElevenLabs** (multilingual_v2 / Flash v2.5) | ✅ Multilíngue (29+ idiomas, "pt"); **voz customizável / Voice Design** (pode criar voz de filhote/criança) — **mais expressivo/emocional** | **Flash/Turbo: ultra-baixa** (~100–250 ms); `optimizeStreamingLatency` | **Por crédito**: multilingual_v2 = 1 crédito/char; Flash v2.5 = 0.5–1/char. A este volume → **~$99/mês** (plano Pro, 600k créditos) | ✅ `stream()` + Speech Engine WS | 🟡 REST/WS, OkHttp; **sem SDK Android** | **10k chars/mês free** (~36 cmds/mês); **Startup Grants: 12 meses free, 33M chars** |

### Notas (TTS)

- **Azure Neural** é o **melhor custo-benefício para PT-BR**: maior catálogo de
  vozes neurais brasileiras, várias com **estilo *casual*/amigável** (perfeito
  para um filhote), **500k chars/mês grátis** que cobre o uso hobby, e **SDK
  Android** com streaming nativo. Risco: o uso (~420k) fica perto do teto
  free — se crescer, cruza para pago (~$16/1M).
- **Google** tem o **free tier mais farto** (4M chars/mês em Standard/WaveNet),
  ideal se "grátis" for o requisito dominante. Vozes pt-BR boas, mas menos
  variedade de estilo que Azure. Gemini-TTS (nova, expressiva, controlável
  por prompt) é pagapor-token e sem free tier — interessante futuramente.
- **ElevenLabs** tem a **melhor naturalidade/expressão** e permite **criar uma
  voz customizada de filhote/criança** — o mais engajador para o sobrinho.
  **Mas é caro p/ hobby**: ~$99/mês neste volume, ou free só p/ ~36 cmds/mês.
  **Exceção: ElevenLabs Startup Grants (12 meses free, 33M chars)** — um
  projeto novo pode se candidatar e sair de graça por 1 ano. Considerar como
  upgrade de qualidade após o MVP.
- **OpenAI TTS** é simples e soa natural, mas **só vozes fixas** (nenhuma
  tipicamente "de criança/filhote"), sem free tier, e ~$6,75/mês neste volume
  — não supera Azure/Google neste cenário.

---

## 4. Stack recomendada

### 🥇 Stack primária (melhor equilíbrio custo × latência × PT-BR)

| Etapa | Provedor | Por quê |
|---|---|---|
| **ASR** | **Deepgram Nova-3** (`language=pt-BR`, `encoding=linear16`, `sample_rate=16000`, `interim_results=true`, `endpointing`, `keyterm=Felipe`) | Streaming sub-300 ms; **aceita PCM linear16 16 kHz direto → zero transcode no relay** (simplifica ADR-006); $200 crédito free cobre anos de uso hobby; keyterm boost para "Felipe". |
| **NLP** | **Regras (1ª etapa) + gpt-4o-mini (fallback)** | Regras resolvem comandos de movimento em <10 ms e grátis; gpt-4o-mini cobre chit-chat natural em PT-BR por ~$0.09/mês. |
| **TTS** | **Azure Neural TTS** (voz pt-BR amigável, ex. estilo *casual*) | Melhor catálogo pt-BR; 500k chars/mês free cobre o uso; streaming via SDK Android. |

**Custo estimado: ~$0,10–0,60/mês** (ASR $0,48 sai do crédito free; LLM $0,09;
TTS $0 dentro do free tier). **Latência típica: ~1,5–2,5 s** (dentro do alvo
< 4 s com folga), pela pipeline toda em streaming.

### 🥈 Stack "grátis 100%" (hobby puro, sem cartão de crédito)

**Azure Speech STT + Azure Neural TTS + Gemini Flash** → **~$0/mês** (free
tiers cobrem o uso com folga, exceto TTS que fica perto do teto). Único
trade-off: SDK Azure adiciona ~30 MB ao APK e exige configuração de streaming
de formato; latência um pouco maior que Deepgram. **Melhor ponto de partida
para o MVP** — depois troca ASR por Deepgram se a latência incomodar.

### 🥉 Stack "engajamento máximo" (voz de filhote/criança, premium)

Igual à primária, mas **TTS → ElevenLabs multilingual_v2 (voz customizada)**
ou Flash v2.5. Custo sobe para ~$99/mês — **a menos que** o projeto entre no
**Startup Grants da ElevenLabs (12 meses free, 33M chars)**. Recomendado só
após validar o MVP e se o engajamento do sobrinho justificar.

---

## 5. Estimativa de custo @ 50 comandos/dia (≈1.500 cmds/mês)

Premissas: ASR 100 min/mês; LLM 225k in + 90k out/mês; TTS 420k chars/mês.

| Stack | ASR/mês | LLM/mês | TTS/mês | **Total/mês** |
|---|---|---|---|---|
| **Primária** (Deepgram + gpt-4o-mini + Azure TTS) | $0,48 → **$0** (crédito) | $0,09 | $0 (free tier) | **~$0,10–0,60** |
| **Grátis 100%** (Azure STT + Gemini Flash + Azure TTS) | $0 (5h free) | ~$0 (Gemini free) | $0 (500k free) | **~$0** |
| OpenAI-only (Whisper-mini + gpt-4o-mini + tts-1) | $0,30 | $0,09 | $6,75 | ~$7,14 |
| Google (Chirp STT + Gemini Flash + Neural2 TTS) | $1,60 | ~$0 | $0 (1M free) | ~$1,60 |
| Premium TTS (Deepgram + gpt-4o-mini + ElevenLabs) | $0,48→$0 | $0,09 | ~$99,00 | **~$99** |
| Premium TTS c/ Startup Grant ElevenLabs | $0 | $0,09 | $0 (12 meses) | **~$0,09** |

> **Conclusão de custo:** para hobby, **Azure + Gemini/gpt-4o-mini é
> essencialmente grátis**; a stack primária (Deepgram + Azure TTS) custa
> centavos/mês e entrega melhor latência. ElevenLabs só vale com o grant.

---

## 6. Notas de integração (relay Android/Kotlin)

**Estado atual do relay** (`android/app/src/main/java/com/example/robofelipe/`):
app Kotlin + Jetpack Compose (minSdk 24, targetSdk 36), sem libs de rede além
de `java.net.HttpURLConnection` (`DataRepository.kt`). Sem WebSocket, sem
JSON de rede (plugin `kotlinx.serialization` já habilitado em
`build.gradle.kts`). **Toda a integração de voz é nova.**

### Dependências a adicionar

- **OkHttp** — cobre **tudo**: WebSocket do ESP32 ↔ relay, REST (OpenAI/Google),
  WebSocket (Deepgram/ElevenLabs) e SSE/streaming do LLM. Uma só lib.
- **kotlinx.serialization** — JSON (Transcrição, resposta LLM, eventos WS).
  Plugin já presente; faltam `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:...")`.
- **(Opcional) Azure Speech SDK for Android** — só na stack Azure: streaming
  de ASR+TTS, conversão de formato e auth nativos. Custa ~30 MB de APK.
- **(Opcional) MediaRecorder/AudioRecord** — só se o relay algum dia capturar
  áudio; conforme ADR-002/006, **o ESP32 captura** e envia PCM ao relay.

### Arquitetura de orquestração no relay

```
ESP32 (WS server, PCM 16k mono)
   │  PCM up (binary frames)             PCM down (binary frames)
   ▼                                       ▲
[VoiceOrchestrator] ── WS ──► ESP32  ... ◄─ WS ── (TTS PCM)
   │
   ├─ ASR: OkHttp WS → wss://api.deepgram.com/v1/listen?language=pt-BR
   │        &model=nova-3&encoding=linear16&sample_rate=16000
   │        &interim_results=true&endpointing=300&vad_events=true
   │        &keyterm=Felipe&smart_format=true
   │        envia frames PCM binários direto; consome {transcript, is_final}
   │
   ├─ NLP: 1) IntentRules.match(transcript) → action? se sim, despacha e (opc.) pula LLM
   │       2) senão, OkHttp POST https://api.openai.com/v1/chat/completions
   │          {model:"gpt-4o-mini", stream:true, response_format:{type:"json_object"}, ...}
   │          SSE → monta {intent, action, response}
   │
   └─ TTS: OkHttp POST .../cognitiveservices/v1/.../speak (Azure, SSML pt-BR)
            ou WS ElevenLabs → stream de áudio → re-encaminha PCM ao ESP32
```

### Pontos de integração por provedor

- **Deepgram**: WS puro, sem SDK. Header `Authorization: Token <KEY>` na query
  ou header. Frame binário = bloco de PCM cru do ring buffer. Eventos
  `Results` (com `is_final`) e `UtteranceEnd` (fim de fala) → dispara LLM.
  **Simplificação real de ADR-006**: a etapa "converter formato de PCM para o
  formato do provedor" **desaparece** com Deepgram.
- **OpenAI LLM**: REST `POST /v1/chat/completions`, `stream:true` (SSE). Usar
  `response_format: json_object` p/ forçar JSON `{intent, action, response}`.
  `gpt-4o-mini` (ou `gpt-5-nano` p/ economia máxima). Combinar com
  `omni-moderation-latest` (grátis) para filtrar conteúdo inadequado à criança.
- **Azure TTS**: via SDK (push stream, mais simples) **ou** REST `/speak` com
  SSML (`<voice name="pt-BR-<voz>Neural">`, `style="friendly"`). Retorno em
  áudio codificado → relay decodifica (Android `MediaCodec`) e re-envia PCM
  16k/16-bit ao ESP32 (formato que o speaker I2S espera, ADR-006).
- **WebSocket ESP32 ↔ relay**: OkHttp `WebSocket`; mensagens binárias = frames
  PCM (up do mic, down do playback), textuais = controle/JSON de estado.
  Mantém o buffer de playback de 8 KB no ESP32 (ADR-006).
- **Secrets**: chaves de API ficam **no relay** (não no ESP32), consistente com
  ADR-002 (TLS termination no app). Guardar via `BuildConfig`/`local.properties`
  + `keystore.properties`, **nunca** no firmware nem no git.

---

## 7. Gotchas — PT-BR e voz de criança

### ASR
- **Voz de criança = F0 alto**, enunciados curtos, articulação imprecisa e
  pausas no meio da palavra. Modelos treinados em voz adulta erram mais.
  Mitigações: `endpointing` paciente (300–600 ms) p/ tolerar pausas;
  `keywords`/`keyterm`/`speechContext` com vocabulário do robô
  (*Felipe, ande, dança, senta, vira, pula, late, brinca*); **Azure Custom
  Speech** treinado em áudio de criança PT-BR = maior precisão (custa esforço
  de dataset).
- **Whisper alucina em silêncio** — inventa "Obrigado.", "Tchau." em
  silêncio/ruído. O **VAD local do ESP32 (ADR-005)** já corta silêncio antes
  do envio, mitigando. Se usar Whisper, passar `condition_on_previous_text=false`.
- **PT-BR vs PT-PT**: todos suportam pt-BR explicitamente. No Whisper
  (multilíngue, sem código de região), passar `language="pt"` e **um prompt
  com ortografia/expressões brasileiras** para enviesar p/ PT-BR. No LLM,
  fixar no system prompt: *"português do Brasil (PT-BR), coloquial"*.

### NLP/LLM
- **Segurança p/ criança de 8 anos**: system prompt rígido (persona do
  cachorro-robô, respostas curtas, só conteúdo apropriado) + pós-filtro
  (moderation OpenAI grátis, ou filtros Gemini/Claude). Claude/Gemini são os
  mais conservadores "out-of-the-box".
- **Sotaque irregular da criança** pode gerar transcrições erradas → o LLM
  deve ser tolerante (instrução: "interprete a intenção mesmo com erros de
  fala") e o classificador de regras deve aceitar variantes
  ("anda/ande/andar", "dança/dançar/dansa").
- **Latência de criança**: criança repete e pausa; capturar janela de ~5 s
  após o wake word e cortar no silêncio do VAD (ADR-005) — evita captar ruído
  infinito e reduz tempo de ASR.

### TTS
- **Persona de filhote**: escolher voz pt-BR **aguda, amigável, jovem**. Azure
  tem estilos *casual*/amigável em pt-BR; ElevenLabs permite **Voice Design**
  (gerar/clonar uma voz de filhote). Evitar vozes graves de adulto — destoa
  de um "cachorro" para o sobrinho.
- **Frases curtas** no prompt do LLM (máx. ~12 palavras) → menos chars de TTS
  (barato) e resposta mais rápida (menos áudio p/ streamar) → latência menor.
- **Consistência**: fixar a `seed` (ElevenLabs) / voice ID para o robô ter
  sempre a mesma voz — parte da identidade do "Felipe".

---

## 8. Pontos em aberto / a verificar antes de amarrar

1. **Gemini Flash (LLM) pricing e free tier** — página `ai.google.dev/pricing`
   deu **timeout**. Confirmar limites do free tier (historicamente generoso
   p/ Flash) e preço pago antes de escolher Gemini como LLM "grátis".
2. **Azure pay-as-you-go (STT/TTS)** — valores renderizam por JS como "$-" na
   página; os números (~$1/h STT, ~$16/1M chars Neural TTS) são da
   documentação/curva histórica. Confirmar na calculadora do Azure para a
   região `brazilsouth`/`eastus`.
3. **Re-verificar todos os preços** perto da implementação — nuvem muda. Os
   promos da Deepgram ("Flux TTS free até 12/09/2026") e ElevenLabs
   ("Startup Grants") têm data limite.
4. **Validar empiricamente a precisão de ASR com voz real do sobrinho** em
   pelo menos Deepgram, Azure e Whisper — gravar ~30 enunciados e comparar
   WER; isso pesa mais que pequenas diferenças de preço.
5. **Decidir se o engajamento justifica ElevenLabs** (Startup Grant) —
   depende de feedback do sobrinho no MVP com voz Azure/Google.

---

## Apêndice — Fontes (verificadas em 2026-08-12)

- Deepgram pricing & docs: `deepgram.com/pricing`, `developers.deepgram.com`
  (Context7) — Nova-3, Flux, linear16/WS, multilíngue, endpointing.
- OpenAI platform pricing & API: `platform.openai.com/docs/pricing`,
  `developers.openai.com` (Context7) — Whisper/gpt-4o-mini-transcribe,
  gpt-4o-mini LLM, tts-1, gpt-4o-mini-tts, Realtime.
- Google Cloud STT/TTS pricing: `cloud.google.com/speech-to-text/pricing`,
  `cloud.google.com/text-to-speech/pricing` — Chirp, WaveNet, Neural2,
  Chirp3-HD, Gemini-TTS, free tiers.
- Azure Speech pricing: `azure.microsoft.com/.../cognitive-services/speech-services`
  — free F0 (5h STT, 500k chars TTS) confirmado; pay-go JS-rendered (estimado).
- ElevenLabs pricing: `elevenlabs.io/pricing` — créditos/assinatura, Startup
  Grants; docs ElevenLabs JS (Context7) — multilingual_v2/Flash/Turbo, stream.
- Anthropic pricing: `anthropic.com/pricing` — Haiku 4.5 $1/$5, Sonnet 5, etc.
- **Não fetchado (timeout):** `ai.google.dev/pricing` (Gemini Flash).
