# ADR-006: ASR e TTS remotos via nuvem com stream de áudio PCM

## Status
Accepted

## Date
2026-07-12

## Context

Após o ESP32 detectar a palavra-chave localmente (ADR-005), ele precisa
**compreender o comando falado** e **responder com voz sintetizada**.
Ambas as funções — ASR (Automatic Speech Recognition) e TTS (Text-to-
Speech) — exigem modelos grandes demais para o ESP32-WROOM-32E-N4 (sem
PSRAM, ~333 KB de SRAM livre):

- ASR de vocabulário livre (português contínuo): centenas de MB de
  modelo. Inviável no robô.
- TTS neural de qualidade natural: idem.
- Mesmo modelos "edge" pequenos (Whisper-tiny, VITS-tiny) exigem
  ~50–150 MB de RAM — ordens de grandeza acima do disponível.

Portanto, ASR e TTS **rodam em nuvem** (Google Speech-to-Text, Azure,
OpenAI Whisper API, ElevenLabs, etc.). O ESP32 atua como **terminal de
áudio**: captura o comando falado, envia para o relay (smartphone,
ADR-002), que repassa para a nuvem; recebe o áudio sintetizado de volta
e reproduz no speaker.

O fluxo end-to-end:

```
ESP32                         Smartphone (relay)            Nuvem
  │                                  │                       │
  │  KWS detecta "Hey Felipe"        │                       │
  │  (ADR-005)                       │                       │
  ├── WebSocket connect ────────────►│                       │
  │                                  │                       │
  │  stream PCM 16k/16bit mono ─────►│  HTTPS POST audio ───►│  ASR
  │  (comando, ~1–3 s)               │                       │  → texto
  │                                  │◄── JSON: texto ───────│
  │                                  │                       │
  │                                  │  HTTPS POST texto ───►│  NLP/LLM
  │                                  │                       │  → decisão
  │                                  │◄── JSON: ação+resposta│
  │                                  │                       │
  │                                  │  HTTPS POST texto ───►│  TTS
  │◄── stream PCM 16k/16bit mono ────│◄── audio blob ────────│  → voz
  │  (resposta, playback I2S)        │                       │
  │                                  │                       │
  │  executa ação local              │                       │
  │  (servos, etc.)                  │                       │
```

## Decision

**Implementar ASR e TTS como serviços remotos em nuvem, acessados via
relay smartphone (ADR-002), com áudio transmitido como PCM cru
16 kHz / 16-bit / mono entre ESP32 e relay.**

Decisões específicas:

### Formato de áudio no link ESP32 ↔ relay

**PCM cru (sem codec)** — 16 kHz, 16-bit, mono, little-endian.
- Custo no ESP32: **zero** (não há decoder/encoder; o I2S já produz PCM).
- Banda: 32 KB/s. Em WiFi LAN, trivial.
- O relay (smartphone) converte para o formato que a nuvem exige
  (ex.: Opus/FLAC para Google, µ-law para Azure, etc.), usando bibliotecas
  nativas do SO mobile.

### Protocolo de transporte ESP32 ↔ relay

**WebSocket** sobre HTTP (sem TLS — ver ADR-002, o TLS está no relay).
- Vantagem: canal bidirecional persistente, ideal para stream de áudio
  up + down com baixa latência.
- Mensagens binárias para frames de áudio; mensagens textuais para
  controle (eventos, JSON de estado).
- Fallback: HTTP chunked em dois endpoints (`/audio/up`, `/audio/down`)
  se WebSocket apresentar instabilidade.

### Papel do relay (smartphone)

1. **Terminar TLS** com a nuvem (stack nativo do SO).
2. **Converter formato** de PCM cru para o formato do provedor (Opus,
   µ-law, FLAC, etc.).
3. **Orquestrar** ASR → NLP/LLM → TTS, chamando as APIs na ordem certa.
4. **Roteamento** do áudio de TTS de volta para o ESP32.
5. **UI** — mostrar transcrição, decisão, e botões de fallback.

### Provedores de nuvem (escolha adiada para implementação)

Não amarrar a um provedor específico nesta ADR. O relay abstrai o
provedor; o ESP32 não sabe (nem precisa saber) quem faz ASR/TTS.
Candidatos:
- **ASR:** Google Speech-to-Text (streaming), Azure Speech, OpenAI
  Whisper API (batch), Deepgram (streaming, baixa latência).
- **NLP/decisão:** LLM (OpenAI, Claude, Gemini) ou regra simples por
  intents. Ação pode ser executada no ESP32 (servos) ou respondida em voz.
- **TTS:** Google TTS, Azure Neural TTS, ElevenLabs, OpenAI TTS.
A escolha concreta depende de custo, latência e qualidade desejada —
decisão de produto, não de arquitetura.

## Alternatives Considered

### ASR e TTS locais no ESP32 (edge)

- **Prós:**
  - Zero dependência de rede; funciona offline.
  - Zero latência de round-trip.
  - Privacidade total.
- **Contras:**
  - **Modelos de ASR de vocabulário livre não cabem** no ESP32-WROOM-32E-N4
    — mesmo Whisper-tiny precisa de ~50–150 MB de RAM. Sem PSRAM é
    impossível.
  - **TTS neural de qualidade natural idem**.
  - Modelos "edge" só funcionam para vocabulário fechado (comandos
    pré-definidos), o que limita a interação a frases fixas — não atende
    o requisito de compreensão de linguagem natural.
- **Rejeitada:** o requisito é linguagem natural (não comandos fixos),
  e o hardware não comporta os modelos necessários.

### ASR local (vocabulário pequeno) + TTS remoto

- **Prós:**
  - Reduz tráfego de rede — só envia texto da intenção.
  - Latência de comando curto é baixa.
- **Contras:**
  - **Limita a vocabulário fechado** — usuário teria que falar frases
    exatas ("ande para frente", "vire à esquerda"). Não é linguagem
    natural.
  - **Não escala** para adicionar comandos novos sem retraining.
  - **TTS ainda remoto** — não resolve metade do problema.
- **Rejeitada:** o produto quer compreensão flexível de linguagem
  natural ("Felipe, dá um passinho pra frente e depois dança"), o que
  exige ASR de vocabulário livre, que não cabe no edge.

### ASR no relay (smartphone) usando API nativa do SO

(ex.: Speech framework do iOS, Google Speech no Android)

- **Prós:**
  - Zero nuvem para ASR — latência baixíssima, funciona offline em
    alguns SOs (iOS on-device).
  - Sem custo de API de ASR.
- **Contras:**
  - **Vendor lock-in no SO** — comportamento diferente entre iOS e
    Android; qualidade variável.
  - **TTS ainda precisa de nuvem** para qualidade natural — não resolve
    o problema todo.
  - **Vocabulário/contexto** limitado ao que o SO reconhece; menos
    controlável que um provedor dedicado.
- **Não rejeitada como opção futura** — pode ser uma otimização de
  latência/custo depois. Para o escopo inicial, centralizar no provedor
  de nuvem via relay mantém a abstração limpa e o código do relay
  independente de SO.

### Streaming de áudio codificado (Opus/MP3) no link ESP32 ↔ relay

- **Prós:**
  - Banda muito menor (Opus ~1–2 KB/s vs PCM 32 KB/s).
- **Contras:**
  - **Decoder/encoder no ESP32** — libopus ou libmp3 ocupa ~25–35 KB de
    flash e RAM, e consome CPU extra.
  - **Complexidade de build** — portar libopus para ESP32/Arduino.
  - **Latência de codec** — Opus adiciona ~20–40 ms de algoritmo.
  - Na LAN, 32 KB/s de PCM não é gargalo — a complexidade de codec não
    se justifica.
- **Rejeitada para o link LAN.** Pode ser reconsiderada se o robô
  operar via rede celular (4G) onde banda é cara — mas aí o relay
  (celular) faz a conversão, não o ESP32.

## Consequences

### Positivas

- **Linguagem natural** — o robô compreende frases flexíveis (não só
  comandos fixos), graças ao ASR de vocabulário livre na nuvem e ao
  NLP/LLM para interpretar intenções.
- **Voz natural** — TTS neural de alta qualidade, impossível localmente.
- **Zero ML pesado no ESP32** — só VAD+KWS leve (ADR-005). O robô não
  precisa de PSRAM para a pipeline de voz.
- **Provedor de nuvem é trocável** — o relay abstrai o provedor; o
  ESP32 não sabe quem faz ASR/TTS. Pode migrar de Google para Azure ou
  OpenAI sem mudar o firmware do robô.
- **Formato de áudio simples** — PCM cru no link LAN, sem codec no
  ESP32; o relay faz a conversão para o provedor.

### Negativas

- **Dependência de internet** — sem conexão à nuvem, o robô não
  compreende comandos de linguagem natural nem sintetiza voz (embora
  ainda detecte a palavra-chave localmente — ADR-005).
- **Latência de round-trip** — 2.5–4 s típicos para "Hey Felipe, [comando]"
  → resposta falada. Inerente a assistentes de voz na nuvem (Alexa e
  Google Assistant têm latência similar).
- **Custo de API** — ASR, LLM e TTS têm custo por uso. Mitigado por
  caching de intenções comuns e por responder comandos locais (servos)
  sem chamar a nuvem quando a intenção é clara.
- **Privacidade** — áudio do comando é enviado à nuvem (após a palavra-
  chave, não continuamente). O VAD+KWS local (ADR-005) garante que só
  há stream após o wake word.
- **Complexidade do relay** — o app mobile precisa orquestrar ASR →
  NLP → TTS, gerenciar sessões, converter formatos. É a parte mais
  complexa do sistema após o firmware.

### Notas

- O **NLP/decisão** pode ser um LLM (para linguagem natural flexível) ou
  um classificador de intents simples (para comandos conhecidos, mais
  barato e rápido). A escolha é de produto e pode evoluir; o firmware do
  ESP32 não depende disso — ele só executa ações (servos, etc.) que o
  relay/comando determinar, ou reproduz o áudio de TTS recebido.
- Ação local (servos) e resposta em voz podem ser **simultâneas**: o
  relay envia o áudio de TTS enquanto já comandou o ESP32 a mover os
  servos. Ex.: "Hey Felipe, dá um passinho" → relay diz ao ESP32
  "MOTION:FORWARD_STEP" (servos andam) e ao mesmo tempo envia TTS
  "Ok, andando!" (speaker toca).
- O **buffer de playback** no ESP32 deve ser de 8 KB (vs 4 KB do mic)
  para amortizar jitter do WiFi e da nuvem — ver ADR-002 sobre glitches.
- Tamanho do comando: janela de captura de ~3–5 s após o wake word
  (ou até silêncio detectado pelo VAD) — evita capturar ruído infinito.
- Esta ADR depende de ADR-002 (relay) e ADR-005 (KWS local) — juntas
  definem a pipeline de voz completa.
