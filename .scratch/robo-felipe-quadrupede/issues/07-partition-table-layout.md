# 07 — Esquema de partições para 4MB flash (Two OTA)

## Type
task

## Status
open

## Assignee
unclaimed

## Blocked by
none

## Question

Definir o esquema de partições exato para os 4MB de flash do
WROOM-32E-N4, com:
- Duas slots OTA A/B (ADR-007: factory + ota_0 + ota_1 + otadata + nvs).
- Modelo KWS embarcado no firmware (~30KB, não em partição separada —
  ver ADR-005/007).
- Rollback habilitado (`CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y`).
- Sem partição data/SPIFFS (display é I2C, sem assets; modelo KWS no
  firmware).

ADR-007 estima: factory 1MB + ota_0 1MB + ota_1 1MB + otadata + nvs
= ~3.3MB dos 4MB disponíveis. Confirmar e gerar o CSV de partições.

Tarefa mecânica: criar `partitions.csv` e adicionar ao `sdkconfig.defaults`.
