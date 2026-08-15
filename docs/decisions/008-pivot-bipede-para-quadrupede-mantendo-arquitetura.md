# ADR-008: Pivot do corpo bípede para quadrúpede, mantendo a arquitetura de voz

## Status
Accepted

## Date
2026-08-09

## Context

O Robô Felipe nasceu como um bípede de 4 servos sobre o kit ACEBOTT
(tutorial, firmware de referência, placa-mãe dedicada). O autor encontrou
duas referências de cão robô que motivaram a mudança de corpo:

- **ESP-HI** (makerworld + oshwhub): cão robô baixo-custo em ESP32-**C3**,
  chassi 3D aberto, 4 servos SG92R, dezenas de ações pré-programadas.
- **xiaozhi-esp32** (GitHub `78/xiaozhi-esp32`): framework de robô
  conversacional em ESP32-**S3** com PSRAM, TLS direto com a nuvem.

A arquitetura de voz do Robô Felipe (ADRs 001–007) é agnóstica ao
formato do corpo: KWS local, ASR/TTS em nuvem via relay smartphone,
OTA assinado, C/C++ + FreeRTOS, ESP32-WROOM-32E-N4. O subsistema de
áudio (SPH0645LM4H + MAX98357A + speaker 8Ω) já está projetado e
validado por datasheet.

Duas decisões precisavam ser tomadas ao pivotar:
1. **Pivot vs projeto novo:** reusar a arquitetura existente ou adotar
   o stack xiaozhi/ESP-HI literalmente?
2. **MCU:** manter o WROOM-32E-N4 ou adotar o C3 do ESP-HI?

## Decision

**Pivotar o corpo de bípede para quadrúpede, mantendo todos os 7 ADRs
aceitos, o MCU ESP32-WROOM-32E-N4, o subsistema de áudio I2S já
projetado, o app Android e a arquitetura relay-smartphone.**

O chassi 3D do ESP-HI é usado como **referência de design** — suas peças
são adaptadas (refile de furos de montagem) para o footprint do dev board
WROOM-32E. O gait de quadrúpede do ESP-HI inspira a camada de locomoção,
que substitui a `ACB_Biped_Robot` do tutorial ACEBOTT.

O xiaozhi-esp32 inspira o **protocolo e a UX conversacional**, mas seu
firmware (S3 + PSRAM + TLS direto) **não** é portado — o Robô Felipe
mantém o relay-smartphone (ADR-002) e o WROOM sem PSRAM.

### Por que manter o WROOM-32E-N4 e não o C3

- O WROOM-32E é **dual-core** (Core 0: rede + áudio I2S; Core 1: servos
  + sensores + KWS) — o C3 é single-core RISC-V.
- O WROOM-32E tem **2 periféricos I2S** (I2S0 RX para mic, I2S1 TX para
  amp) — o C3 tem **1 só**, que é exatamente por que o ESP-HI abandonou
  o I2S MEMS e usou eletreto + ADC + PDM (SNR pior, sem os 65 dB do
  SPH0645LM4H).
- Os 7 ADRs, o hardware de áudio, o app Android e o diagnóstico de
  hardware confirmado (`hardware/esp32-wroom-32e-n4.md`) são todos
  reutilizáveis sem mudança.
- Adaptar o chassi 3D (refile de furos) é ordens de magnitude mais barato
  que re-arquitetar firmware + áudio + relay.

## Alternatives Considered

### Projeto novo do zero com stack xiaozhi + ESP32-C3

- **Prós:** chassi 3D e PCB do ESP-HI usados como-is; protocolo xiaozhi
  maduro; comunidade ativa.
- **Contras:** descarta 7 ADRs e ~3 meses de decisões documentadas; exige
  comprar ESP32-S3 (PSRAM) ou C3; reescrever subsistema de áudio (sem
  I2S MEMS no C3); reescrever app para o protocolo xiaozhi; TLS direto
  no robô (contraria ADR-002).
- **Rejeitada:** o custo de descartar o trabalho existente supera o
  benefício de usar o chassi literalmente.

### Substituir o MCU por ESP32-S3 com PSRAM (para rodar xiaozhi)

- **Prós:** um só chip, PSRAM para buffers, stack xiaozhi nativo.
- **Contras:** pinagem diferente do WROOM-32E; servos e áudio precisam
  remapeamento; redesenho de hardware; contraria ADR-002 (TLS no robô).
- **Rejeitada:** fora do escopo — o WROOM-32E já dá conta da pipeline.

## Consequences

### Positivas

- Reuso integral dos ADRs 001–007, do hardware de áudio, do app Android
  e do diagnóstico de hardware.
- Camada de locomoção é a única substituição significativa no firmware.
- Chassi 3D de referência (ESP-HI) reduz o trabalho mecânico.
- O sobrinho ganha um cão (mais expressivo e manuseável que o bípede).

### Negativas

- A `ACB_Biped_Robot` e o tutorial ACEBOTT ficam como referência morta
  (não deletados, mas não usados no firmware do cão).
- O chassi precisa adaptação (não é print-and-go).
- O gait de quadrúpede precisa ser escrito ou portado do ESP-HI.
- O app Android precisa evoluir de HTTP-control para WebSocket-relay
  (consequência já prevista no ADR-002).

### Notas

- Os ADRs 001–007 permanecem aceitos e válidos sem alteração.
- O tutorial ACEBOTT e a amostra `biped_robot_full.ino` permanecem no
  repo como referência de firmware e de ensino.
- Esta ADR é o ponto de bifurcação: tudo antes dela é contexto do bípede;
  tudo depois é o quadrúpede.
