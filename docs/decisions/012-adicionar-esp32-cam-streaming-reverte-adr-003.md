# ADR-012: Adicionar ESP32-CAM como nó de streaming de vídeo (reverte ADR-003)

## Status
Accepted

## Date
2026-08-09

## Context

ADR-003 dispensou a câmera do projeto porque o WROOM-32E-N4 não tem
PSRAM — não comporta buffer de imagem (~150 KB por frame). O pivot para
quadrúpede (ADR-008) não mudou esta constraint do MCU principal.

O autor tem um módulo **ESP32-CAM** (Ai-Thinker, OV2640, 4 MB PSRAM —
ver `hardware/esp32-cam-datasheet.md`) disponível. A CAM tem PSRAM
própria para buffers de frame e é um nó de imagem autônomo. A CAM tem
apenas 9 GPIO expostos (15 consumidos pela câmera/SD) — não pode rodar
servos, áudio ou sensores. É um nó de imagem puro.

A decisão é adicionar a câmera como **nó separado** do MCU principal,
fazendo apenas **streaming de vídeo** (sem CV embarcada). O stream é
enviado a um **serviço de backend** (arquitetura a definir separadamente)
que fará o processamento de imagem.

## Decision

**Adicionar o ESP32-CAM como segundo MCU, dedicado exclusivamente a
streaming de vídeo para um serviço de backend. Sem CV embarcada na CAM.**

- A CAM é um nó autônomo na LAN (WiFi STA, mesmo router do WROOM).
- A CAM roda um streaming server (HTTP MJPEG — padrão do esp32-camera).
- O stream vai a um serviço de backend que fará o processamento de
  imagem (CV, análise de cena, etc.).
- A CAM é **decoupled** do WROOM: se a câmera falhar, o cão continua
  funcionando por voz + servos + display + IMU.
- A CAM é fisicamente o "olho" do cão no chassi.

### Arquitetura de dois nós

```
  WROOM-32E-N4                ESP32-CAM (Ai-Thinker)
  ─────────────                ──────────────────────
  • Voz (KWS + I2S in/out)    • OV2640 câmera
  • Servos (4× SG90)          • Streaming server (MJPEG)
  • Display OLED (I2C)        • WiFi STA (router)
  • IMU MPU6050 (I2C)         • PSRAM 4 MB (própria)
  • Ultrassom                 • Sem CV, sem servos, sem áudio
  • WebSocket → app (relay)
       │                              │
       ▼                              ▼
  App (relay)                 Serviço de backend
  ────────────                ────────────────────
  • TLS termination           • Processamento de vídeo (TBD)
  • ASR → NLP → TTS            • Arquitetura a definir
  • Controle de servos
  • Orquestração
```

A CAM e o WROOM não se comunicam diretamente — são ilhas independentes
na LAN. O backend e o app, juntos, orquestram o resultado do
processamento de imagem em comandos para o robô.

### Por que streaming-only (sem CV na CAM)

- CV embarcada na CAM seria possível (PSRAM comporta), mas o autor
  optou por processar no backend — recursos maiores, CV mais pesado.
- A CAM como nó de streaming puro é firmware simples (esp32-camera +
  HTTP server), mais robusto que CV embarcada.

## Alternatives Considered

### CV embarcada na CAM (face detection, color tracking)
- Prós: sem latência de rede, funciona offline.
- Contras: ESP32-CAM faz face detect ~10fps QVGA, mas object detection
  geral é lento/frágil. Backend com GPU faz muito mais.
- **Rejeitada:** o autor prefere processar no backend.

### Sem câmera (manter ADR-003)
- Prós: menos escopo, menos firmware, menos power.
- Contras: cão sem visão perde canal de interação valioso.
- **Rejeitado pelo autor:** quer a câmera no MVP.

### Trocar MCU principal por ESP32-S3 com PSRAM + câmera integrada
- Prós: um só chip, pinagem unificada.
- Contras: contraria ADR-008, redesenho de hardware, TLS no robô.
- **Rejeitada:** o ESP32-CAM sobressalente resolve sem trocar MCU.

## Consequences

### Positivas

- ADR-003 é revertida — câmera agora é parte do escopo.
- Decoupling limpo: CAM falha → robô continua por voz.
- CAM usa PSRAM própria — não afeta orçamento de RAM do WROOM.
- Firmware da CAM é simples (streaming server, sem CV).
- CAM como "olho" do cão é temático, não improvisação.

### Negativas

- +1 firmware para manter (CAM streaming server).
- +1 conexão de rede na LAN (segundo ESP32 em 2.4 GHz).
- +150 mA médio, +310 mA pico no orçamento de potência (coberto pelo
  2P + capacitor — ver ADR-011).
- Programar a CAM exige adaptador FTDI/CP2102 (sem USB onboard).
- O serviço de backend é novo escopo (arquitetura a definir).

### Notas

- **Backend service:** arquitetura a definir separadamente. Questões
  em aberto: onde roda (cloud/local), como recebe o stream (TLS direto
  da CAM, ou via relay/app?), como retorna resultados para o robô (via
  app→WROOM WebSocket, ou direto). A CAM tem PSRAM — pode fazer TLS,
  diferentemente do WROOM — mas isto fica para a discussão do backend.
- **Power budget:** CAM adiciona ~150 mA médio, ~310 mA pico. Total com
  câmera: ~1.0 A médio, ~3.3 A pico. 2P 18650 + capacitor cobre (ADR-011).
- **Chassi:** CAM é 27×40.5×4.5 mm (`esp32-cam-datasheet.md:21`). Encaixa
  como "olho" na face do cão. Precisa adaptação do STL do ESP-HI.
- **WiFi congestion:** dois ESP32 em 2.4 GHz simultâneos. Monitorar
  dropout de áudio; se necessário, CAM em canal distante do WROOM.
