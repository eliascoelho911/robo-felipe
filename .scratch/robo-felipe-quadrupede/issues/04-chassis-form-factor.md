# 04 — Fator de forma do chassi: dev board ou PCB custom?

## Type
grilling

## Status
open

## Assignee
unclaimed

## Blocked by
none

## Question

O chassi 3D do ESP-HI foi desenhado para a ESP-HI MainBoard (PCB
custom ESP32-C3, 1.2mm de espessura, footprint específico). O Robô
Felipe usa o **ESP32-WROOM-32E-N4 dev board** (módulo + breakout com
headers, ~51×28mm, bem maior que a MainBoard do ESP-HI).

Opções:
1. **Adaptar o STL do ESP-HI para o dev board WROOM** — refile de
   furos de montagem, espaço extra para headers. O dev board é maior
   e mais alto que a MainBoard do ESP-HI — o chassi cresce
   significativamente. Pró: zero trabalho de PCB. Contras: cão maior,
   mais pesado, fiação exposta, menos robusto para manuseio de niño.
2. **Design de PCB minimalista para o WROOM** — footprint do módulo
   WROOM + conectores para áudio/servos/display/IMU/ultrassom/câmara,
   tamanho próximo ao do ESP-HI. Pró: cão compacto, robusto, profissional.
   Contras: design + fabricação de PCB (~$5 JLCPCB, 2-3 sem lead time),
   precisa esquema + layout.
3. **Usar uma placa protoboard/perfboard** — soldar tudo em perfboard
   no tamanho do ESP-HI. Pró: rápido, sem PCB fabric. Contras: frágil,
   fiação exposta, não reproduzível.

Recomendação preliminar: **(2) PCB minimalista** — o dev board é
grande demais para um cão pequeno e os headers soltam com manuseio.
Um PCB de ~50×50mm com o módulo WROOM + conectores JST para servos/
áudio/display é robusto, compacto, e fabricável na JLCPCB por ~$5.
O esquema de áudio já está desenhado (`esquema-audio-esp32-sph0645-max98357.md`).
