# Hardware — Robô Felipe

Documentação de hardware da variante atual do robô Felipe: o
**Tamagotchi** (pet de bolso com display, microfone e alto-falante, em
ESP32-S3 com PSRAM — ver ADR-016).

> As variantes de corpo anteriores (bípede ACEBOTT e quadrúpede ESP-HI,
> ambas em ESP32-WROOM-32E-N4) estão arquivadas nos branches `main` e
> `quadrupede`. Pinouts, datasheets do WROOM-32E e do ESP32-CAM, e a foto
> da placa ACEBOTT foram removidos deste branch.

## Índice

| Caminho | Conteúdo |
|:---|:---|
| [`audio/`](audio/) | BOM e esquema do subsistema de áudio (SPH0645LM4H + MAX98357A) — reusado da arquitetura anterior; pinout de referência para o WROOM-32E, remapeável para o ESP32-S3 |
| [`cores3/`](cores3/) | Placeholder para material de referência da variante Tamagotchi/M5Stack CoreS3 (ADR-016) |

## Referências cruzadas

- ADRs que citam o subsistema de áudio: `docs/decisions/001`, `005`, `016`
- BOM de áudio: [`audio/BOM-audio.md`](audio/BOM-audio.md)
- Esquema de áudio: [`audio/esquema-audio.md`](audio/esquema-audio.md)
- Variante Tamagotchi/CoreS3: [`cores3/`](cores3/) (placeholder — ADR-016)
