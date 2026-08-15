# 03 — Schema do protocolo WebSocket (ESP32 ↔ relay)

## Type
grilling

## Status
open

## Assignee
unclaimed

## Blocked by
none

## Question

Qual o schema de mensagens do WebSocket entre o ESP32 e o app relay
(ADR-002/006/007)?

O WebSocket transporta três tipos de dados simultaneamente:
1. **Áudio up** (mic → relay) — frames binários PCM 16kHz/16-bit/mono.
2. **Áudio down** (relay → ESP32, TTS) — frames binários PCM 16kHz/16-bit/mono.
3. **Controle** — mensagens textuais (JSON?) para: handshake de versão,
   comandos de servo (DOG_STATE_*), eventos (keyword detectada, erro),
   chunks de OTA (ADR-007), status (bateria, IMU, ultrassom).

Decisões a tomar:
- **Formato de controle:** JSON textual? Protobuf? CBOR? Custom?
- **Multiplexação:** como distinguir áudio de controle no mesmo canal?
  (Tipos de mensagem binária com header? Ou canais separados?)
- **Comandos de servo:** enum numérico (DOG_STATE_FORWARD=2) vs string
  ("forward") vs JSON ({"cmd":"move","action":"forward","args":{}})?
- **Eventos do robô:** keyword detectada, bateria baixa, queda
  detectada (IMU), obstáculo (ultrassom) — como codificar?
- **Backpressure:** áudio up vs áudio down vs OTA competem por banda.
  Quem tem prioridade? (ADR-007 diz que OTA pausa áudio.)
- **Handshake inicial:** FIRMWARE_VERSION, capacidades, mDNS.

Recomendação preliminar: JSON para controle (legível, debug-friendly),
binário com header de 1 byte (tipo de mensagem) para áudio/OTA. Enum
numérico para comandos de servo (compatível com DOG_STATE_* do
servo_dog_ctrl — ver ADR-013).
