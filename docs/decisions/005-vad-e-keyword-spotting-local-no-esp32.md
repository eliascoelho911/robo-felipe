# ADR-005: VAD + keyword spotting local no ESP32 via TinyML

## Status
Accepted

## Date
2026-07-12

## Context

O robô-felipe deve responder a comandos de voz do usuário. Antes de
qualquer processamento remoto (ASR/TTS, ver ADR-006), o ESP32 precisa
**detectar quando o usuário está falando** e **reconhecer uma palavra de
ativação** (ex.: "Hey Felipe") para iniciar a captura e o stream.

Duas funções distintas, ambas locais no ESP32:

1. **VAD (Voice Activity Detection):** distinguir fala de silêncio/ruído
   de fundo. Evita enviar áudio silencioso para a nuvem (economiza
   banda, bateria do relay, custo de API). Roda **sempre** (always-on).
2. **KWS (Keyword Spotting):** detectar a palavra-chave ("Hey Felipe")
   dentro do fluxo de áudio onde o VAD confirmou fala. Ao detectar,
   dispara o modo de escuta ativa (grava comando e envia para ASR
   remoto).

Por que **local** e não remoto:
- Enviar áudio continuamente para a nuvem para detectar a palavra-chave
  seria caro (banda, latência, privacidade, bateria do relay).
- O modelo KWS é pequeno (~20–40 KB) e roda em ~15–25% de um core do
  ESP32 — perfeitamente viável.
- VAD por energia espectral é trivial (~2 KB).

Restrições do hardware (ESP32-WROOM-32E-N4, sem PSRAM, ver ADR-001):
- **RAM:** o modelo KWS (pesos quantizados int8) ocupa ~20–40 KB. VAD
  ~2 KB. Buffers de janela de inferência (janela 1 s a 16 kHz = 32 KB de
  amostras, mas reutilizado) ~2–4 KB. Total ~40 KB — cabe com folga.
- **CPU:** inferência de KWS a cada ~200–500 ms consome ~15–25% de um
  core. Aceitável.
- **DMA:** a captura de áudio via I2S0 com DMA enche ring buffers sem
  participação da CPU; a task de VAD/KWS consome do ring, não do I2S
  diretamente. **A captura I2S roda a 48 kHz** (requisito do
  SPH0645LM4H, que suporta 32–64 kHz nativamente — ver
  `hardware/audio/BOM-audio.md`); uma etapa de **decimação FIR por 3**
  (48 → 16 kHz) roda entre o ring do I2S e o ring de inferência,
  consumindo ~1–2% de um core. A pipeline de VAD/KWS em si opera a
  16 kHz, como especificado abaixo.

## Decision

**Implementar VAD + KWS localmente no ESP32 usando TensorFlow Lite
Micro (TFLM) com modelo int8 quantizado.** Pipeline:

```
I2S0 (DMA, 48 kHz)  ──►  ring buffer I2S (12 KB)  ──►  decimação FIR ×3
                                                              │
                                                              ▼
                                          ring 16 kHz (4 KB) ──► task_vad_kws (Core 1)
                                                                       │
                                             ┌─────────────────────────┤
                                             │                         │
                                          VAD                    KWS (TFLM)
                                       (energia              (modelo "Hey Felipe",
                                        espectral)             int8, ~30 KB)
                                             │                         │
                                             └────────┬────────────────┘
                                                      ▼
                                             evento "keyword detectada"
                                                      │
                                                      ▼
                         dispara task_audio_net → stream para relay (ADR-006)
```

**VAD** por energia de janela (RMS / threshold adaptativo) na janela de
inferência — simples, ~2 KB, descarta silêncio antes de alimentar o KWS.

**KWS** com modelo TFLM treinado para a palavra-chave ("Hey Felipe").
Opções de obtenção do modelo:
- **Edge Impulse** — treina e exporta diretamente para ESP32 (C++).
- **ML Commons / TFLite Micro examples** — modelos de referência
  ("yes/no") adaptáveis.
- **Treinamento próprio** com dataset de áudio da palavra-chave
  (data augmentation com ruído de ambiente e do robô).

Especificações do modelo-alvo:
- Taxa de amostragem da inferência: 16 kHz, 16-bit, mono (após
  decimação da captura I2S de 48 kHz — ver Contexto acima).
- Janela de inferência: ~1 s (com hop de 200–500 ms).
- Features: MFCC ou log-mel spectrogram (24–26 filtros).
- Arquitetura: CNN pequena ou DS-CNN (Depthwise Separable CNN),
  ~20–40 KB de pesos int8.
- Latência de detecção: < 300 ms após o fim da palavra.

## Alternatives Considered

### VAD + KWS remotos (tudo na nuvem)

- **Prós:**
  - Zero de ML no ESP32 — só captura e stream.
  - Modelo pode ser grande (precisão alta).
- **Contras:**
  - **Always-on streaming para a nuvem** — banda contínua, bateria do
    relay, custo de API, latência de round-trip para cada decisão.
  - **Privacidade** — áudio contínuo enviado para servidores.
  - **Sem fallback offline** — se a conexão cair, robô não ouve nada.
  - **Dependência total do relay/app ativo** só para detectar a
    palavra-chave.
- **Rejeitada:** custo operacional e privacidade inviabilizam. KWS
  local é o padrão da indústria (Alexa, Google Assistant fazem exatamente
  assim).

### KWS por matching direto de padrões (sem ML)

(ex.: correlação cruzada com template de áudio da palavra)

- **Prós:**
  - Implementação trivial, sem TFLM, ~1 KB.
- **Contras:**
  - **Baixíssima robustez** — variações de tom, velocidade, ruído de
    fundo e voz diferente do template quebram a detecção.
  - Falsos positivos e falsos negativos altos — experiência frustrante.
- **Rejeitada:** qualidade insuficiente para um produto usável.

### Chip dedicado de KWS (ex.: SU-03T, LD3320)

- **Prós:**
  - Offload completo do ESP32 — zero CPU, zero RAM para KWS.
  - Modelos prontos para comandos em português/chinês.
- **Contras:**
  - **Hardware extra** — chip, fiação, fonte, espaço no chassi.
  - **Palavra-chave fixa** pelo modelo do fabricante — não customizável
    para "Hey Felipe" sem regravar/recomprar.
  - **Mais um nó** de comunicação (UART) no sistema.
  - **Foge do escopo** — o ESP32 dá conta do KWS local sem hardware
    extra.
- **Rejeitada:** o orçamento de CPU/RAM do ESP32 comporta o KWS sem
  adicionar hardware. Um chip dedicado seria overkill.

### Wake word por toque no app (tap-to-talk, sem KWS local)

- **Prós:**
  - Zero ML no robô — usuário aperta botão no app para falar.
  - Implementação trivial.
- **Contras:**
  - **Robô não é "hands-free"** — usuário precisa pegar o celular para
    cada comando, o que derrota o propósito de um assistente de voz.
  - **Sem always-on** — não responde a "Hey Felipe" sem toque.
- **Rejeitada como solução principal.** Pode ser mantida como **fallback
  opcional** no app para casos em que o KWS falhe ou o ambiente esteja
    muito ruidoso.

## Consequences

### Positivas

- **Always-on sem custo de banda** — VAD+KWS rodam localmente; só há
  tráfego de rede quando a palavra-chave é detectada.
- **Privacidade** — áudio só sai do robô depois do wake word.
- **Latência de detecção baixa** — < 300 ms, pois não há round-trip.
- **Funciona parcialmente offline** — a detecção da palavra-chave
  independe da nuvem; só a compreensão do comando (ASR) precisa de rede.
- **CPU e RAM dentro do orçamento** — ~40 KB de RAM e ~20% de um core;
  a folga geral (~215 KB, ver ADR-002) permanece ampla.

### Negativas

- **Modelo precisa ser treinado** para "Hey Felipe" — exige dataset de
  áudio da palavra (com data augmentation para ruído de ambiente e dos
  atuadores do robô). Edge Impulse acelera isso, mas não é zero trabalho.
- **Precisão limitada** do modelo pequeno (~30 KB) — pode haver falsos
  positivos (palavras parecidas) ou falsos negativos (sotaque diferente).
  Tuning de threshold e dataset diverso mitigam, mas não eliminam.
- **Dependência de TFLM** — biblioteca C++ adicionada ao build; binary
  size cresce ~50–80 KB de flash (cabe nos 4 MB com folga).
- **Calibração por ambiente** — VAD threshold pode precisar de ajuste
  conforme ruído de fundo; considerar threshold adaptativo.

### Notas

- **Microfone: SPH0645LM4H** (Adafruit #3421). SNR 65 dB(A), I2S
  24-bit, suporta 32–64 kHz nativamente. Não faz 16 kHz direto (BCLK
  mínimo 2.048 MHz), por isso a captura roda a 48 kHz com decimação
  FIR por 3 para 16 kHz. Análise completa em `hardware/audio/BOM-audio.md`.
  Alternativa de orçamento: INMP441 (faz 16 kHz nativo, SNR ~61 dB(A),
  risco de clone no AliExpress).
- **Decimação 48 → 16 kHz**: filtro FIR anti-alias simples (taps
  ímpares, cutoff ~7 kHz, ganho unitário). O ESP32 tem CPU de sobra
  para isto (~1–2% de um core). O filtro pode ser fixo em ROM/flash
  (coeficientes PROGMEM) — sem alocação dinâmica. O ring buffer do
  I2S (48 kHz) é maior (12 KB vs 4 KB do ring de inferência), mas
  ainda trivial para a RAM disponível.
- A janela de áudio do KWS deve reutilizar o ring de inferência de
  16 kHz (não o ring do I2S de 48 kHz) para economizar RAM.
- O hop de inferência (200–500 ms) define a latência vs o custo de CPU
  — ajustar empiricamente.
- Para gerar o dataset de "Hey Felipe", gravar ~500–1000 amostras com
  vozes diferentes + ruído de ambiente + ruído dos atuadores do robô;
  usar Edge Impulse para augmentation e treino.
- O evento "keyword detectada" sinaliza a `task_audio_net` via queue do
  FreeRTOS (ver ADR-006 para o protocolo de stream).
- Manter **tap-to-talk** no app como fallback de UX (ver ADR-005,
  Alternatives, "Wake word por toque").
