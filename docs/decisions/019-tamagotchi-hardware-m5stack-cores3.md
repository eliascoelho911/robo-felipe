# ADR-019: Hardware do Tamagotchi — M5Stack CoreS3 como plataforma

## Status
Accepted

## Date
2026-08-31

## Context

O ADR-016 estabeleceu a **restrição decisiva** de hardware do Tamagotchi:
sem relay de smartphone, o ESP32 precisa **terminar TLS ele mesmo**
(~40–50 KB de RAM por sessão), o que torna **PSRAM obrigatória** —
eliminando o WROOM-32E-N4 (ADR-001) e o StampS3 do Cardputer (ambos sem
PSRAM). Naquela ADR o **M5Stack CoreS3** (ESP32-S3, 16 MB flash, 8 MB
PSRAM) foi nomeado apenas como *candidato preferido*, com a escolha final
remetida a "uma futura ADR de hardware do Tamagotchi".

O ADR-017 ampliou o escopo para incluir a **câmera GC0308** e o sensor de
proximidade **LTR-553ALS-WA** (que o CoreS3 traz na mesma fita ribbon),
supersedendo a menção "câmera indesejada" do ADR-016.

O ADR-018 fixou a divisão funcional: o **comportamento** mora num Core
em TypeScript auto-hospedado; a **Plataforma** (o CoreS3, agora) só
detecta Triggers, envia Batches e executa Planos de Ações. Ou seja, o
hardware só precisa ser bom em **detectar e executar**, não em *decidir*.

A documentação de referência `hardware/cores3/CoreS3-capacidades.md`
(especificações extraídas dos datasheets oficiais via LCSC) confirma que
o CoreS3 entrega, num só objeto, quase todo o subsistema de hardware que
o Tamagotchi exige — áudio, display touch, bateria+RTC, IMU, câmera,
proximidade. Restava apenas **registrar a decisão de placa** e fechar a
avaliação de alternativas.

O usuário decide: **CoreS3 é a decisão final de hardware do Tamagotchi**;
as demais opções (PCB custom, devkits genéricos, placas sem PSRAM) ficam
**encerradas** e não serão reavaliadas a menos que uma premissa mude.

## Decision

**A plataforma de hardware do Tamagotchi é o M5Stack CoreS3 (SKU K128).**
A avaliação de alternativas está encerrada — esta é a decisão final de
hardware para a variante.

### A placa

ESP32-S3 (FN8) dual-core 240 MHz · **8 MB PSRAM octal** · 16 MB flash ·
WiFi b/g/n + BLE 5 · crypto HW (AES/SHA/RSA) — satisfaz integralmente o
ADR-016 (TLS sem relay). Onboard:

- **Áudio** — codec **ES7210** (2 mics, SNR 102 dB, 16 kHz nativo) + amp
  **AW88298** (I²S, 5,2 W, I²C 0x36). **Substitui o BOM de breakout**
  (`SPH0645LM4H` + `MAX98357A`, documentado em `hardware/audio/`) — que
  passa a ser referência histórica do quadrupede, não o caminho do
  Tamagotchi. O **16 kHz nativo elimina a decimação FIR** prevista no
  ADR-005; o SNR superior (102 vs 65 dB) torna a KWS mais robusta em
  ruído.
- **Display + touch** — ILI9342C IPS 320×240 (SPI) + FT6336U touch
  capacitivo (I²C 0x38, wake-on-touch ~220 µA) — a UI do pet de fábrica.
- **Energia** — AXP2101 PMU (I²C 0x34, fuel gauge, botão POWER em HW) +
  BM8563 RTC (I²C 0x51, alarme/timer → wake) + LiPo 500 mAh. Autonomia
  estimada ~35 dias despertando 1×/h por 10 s de voz (I_deep ≈ 49 µA).
- **IMU** — BMI270 (I²C 0x69, any-motion/step/activity em HW ~10 µA) +
  BMM150 (magnetômetro via aux).
- **IO expander** — AW9523B (I²C 0x58) detém resets/enables, poupa GPIO.
- **Câmera + proximidade** (ADR-017) — GC0308 (DVP, SCCB 0x21) +
  LTR-553ALS-WA (I²C 0x23), na fita ribbon.

### Pinout — zero portabilidade do firmware de referência

O `78/xiaozhi-esp32` traz a board `m5stack/core-s3` cujo `config.h` usa
os **mesmos GPIOs e codecs** do CoreS3 (I2S WS=33/BCLK=34/MCLK=0, I²C
SDA=12/SCL=11, AW88298+ES7210). A lacuna antes listada ("remapear I2S
WROOM→S3") fica **dissolvida**: para o CoreS3 não há remapeamento a fazer.

### Áudio embutido vs BOM de breakout

Confirmado o efeito apontado no ADR-016: ao adotar o CoreS3, **ES7210 +
AW88298 substituem o SPH0645 + MAX98357**. O subsistema `hardware/audio/`
(BOM-audio.md, esquema-audio.md) deixa de ser o caminho do Tamagotchi —
permanece como documentação histórica do quadrupede (WROOM-32E-N4).

## Alternatives Considered

> Por decisão do usuário, a avaliação de alternativas está **encerrada**.
> As opções abaixo foram consideradas e fechadas; não serão reavaliadas a
> menos que uma premissa mude.

### M5Stack StampS3 / Cardputer
- **Rejeitada (e encerrada) no ADR-016**: sem PSRAM → não termina TLS.
  Decisão final; não reabrir.

### ESP32-WROOM-32E-N4 (ADR-001)
- **Rejeitada (e encerrada) no ADR-016**: sem PSRAM → não termina TLS.
  Válido apenas para o quadrupede (com relay). Decisão final; não reabrir.

### PCB custom com ESP32-S3-WROOM-1-N16R8
- **Prós:** otimização de forma/custo/BOM sob medida; sem periféricos
  "a mais".
- **Contras:** reconstruir do zero display, touch, áudio, bateria, RTC,
  IMU, câmera, expansor — tudo o que o CoreS3 já entrega montado e
  validado; esforço de HW/layout/EUA que não agrega ao MVP de um pet de
  bolso; perde-se o suporte first-class do `xiaozhi-esp32` à board
  `core-s3` (pinout/codecs prontos).
- **Rejeitada e encerrada.** O ganho de otimização não compensa o custo
  de tempo para o protótipo. Reavaliável apenas se o produto evoluir
  para volume/fator-de-forma que exija PCB própria.

### Devkits ESP32-S3 com PSRAM (genéricos)
- **Contras:** não trazem display/touch/bateria/codecs/câmera/sensores
  onboard — somam BOM externo a um trabalho que o CoreS3 já resolve.
- **Rejeitada e encerrada.**

## Consequences

### Positivas

- **Subsistema de hardware quase pronto** — áudio, display touch,
  bateria+RTC, IMU, câmera e proximidade vêm montados e validados num só
  objeto; o esforço de HW do Tamagotchi encolhe drasticamente.
- **Firmware de referência sem portabilidade** — `xiaozhi-esp32`
  (board `m5stack/core-s3`) já usa o pinout e os codecs do CoreS3 (ver
  ADR de firmware futura).
- **Áudio superior ao BOM** — ES7210 (SNR 102 dB, 16 kHz nativo) elimina
  a decimação do ADR-005 e torna a KWS mais robusta; mics duplos habilitam
  AEC/BSS.
- **"PET vivo" viável em hardware** — RTC (timer) + wake-on-touch
  (FT6336U) + wake-on-motion (BMI270) + fuel gauge (AXP) + deep-sleep
  8 µA entregam o ciclo dormir/acordar/"fome" com autonomia de ~1 mês.
- **Câmera + proximidade inclusos** (ADR-017) sem BOM extra.
- **PSRAM octal de 8 MB** satisfaz a restrição central do ADR-016.

### Negativas

- **MCLK do ES7210 em GPIO0 (strapping pin)** — o path de áudio do BOM
  (SPH0645) não usava MCLK; o ES7210 exige 256×Fs em **GPIO0**, que é
  strapping de boot mode. Validar que o clock ativo no boot não impede
  SPI boot (nível alto no boot é compatível, mas clock ativo pode
  interferir) — verificar no esquema do CoreS3.
- **Sem AEC em chip nenhum** — num brinquedo onde o speaker toca TTS
  perto do mic, o eco acústico tem de vir do **firmware** (AFE do esp-sr,
  ou VAD que silencie o speaker ao captar).
- **Filtro de saída do AW88298** — não é filterless como o MAX98357A;
  confirmar ferrite/LC no esquema do CoreS3.
- **Câmera GC0308 ocupa ~12 GPIO DVP** + consome CPU/RAM (PSRAM) — visão
  deve rodar em janelas curtas/core separado para não degradar a latência
  de voz (ver ADR-017); desligar o rail no deep-sleep (AXP2101).
- **Fuel gauge é estimativa** (E-Gauge, não coulomb counter) — tratar
  "% de bateria" como aproximação na "barra de fome".
- **BMI270 exige config-file de 8 KB a cada boot** — embarcar no firmware.
- **ADC2 inutilizável com WiFi ligado** — sensar bateria no ADC1.
- **Touch FT6336U: só 2 toques + gestos** (sem 5/10-toque, sem
  water-rejection) — suficiente para o Sobrinho (8 anos), mas mão
  suja/molhada pode gerar toque espúrio.
- **Toolchain ainda não configurado** — `.vscode/arduino.json` aponta
  board WROOM (`PSRAM=disabled`). Para o CoreS3 é preciso board ESP32-S3
  com PSRAM habilitada (ou adotar o build ESP-IDF do `xiaozhi-esp32`).
  Placeholder em `hardware/cores3/`; configuração pendente.

### Notas

- **Esta ADR fecha a avaliação de hardware.** O usuário decidiu CoreS3
  como decisão final; as alternativas acima estão **encerradas** e só
  seriam reabertas se uma premissa mudar (ex.: necessitar fator de forma
  menor ou volume que justifique PCB própria).
- **Supersede parcial do ADR-016** apenas no trecho em que nomeava o
  CoreS3 como "candidato preferido" — aqui ele passa a ser **a** decisão
  de hardware. O ADR-016 permanece como a ADR que estabeleceu a restrição
  (PSRAM); esta ADR a resolve em placa concreta.
- **Câmera + proximidade**: escopo e consequências regidos pelo ADR-017.
  Visão (em nuvem, via VLLM): ver research `tamagotchi-visao-cam.md`.
- **"PET vivo"** (estado/decay/estágios, NVS vs cloud, `advanceStats` no
  wake do RTC) fica como tópico aberto — ADR futura, fora do escopo aqui.
- **Áudio**: `hardware/audio/` (SPH0645+MAX98357, pinout WROOM) torna-se
  referência histórica do quadrupede; o áudio do Tamagotchi é o embutido
  ES7210+AW88298, documentado em `hardware/cores3/CoreS3-capacidades.md`.
- **Próximas ADRs dependentes**: firmware (`xiaozhi-esp32` + `esp-sr` +
  `xiaozhi-esp32-server`, wake word "Felipe" em pt-BR), OTA pull, nuvem
  (provedores ASR/LLM/TTS pt-BR), "PET vivo".
