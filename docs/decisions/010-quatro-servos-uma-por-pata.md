# ADR-010: 4 servos — 1 por pata (approach ESP-HI)

## Status
Accepted

## Date
2026-08-09

## Context

ADR-008 pivotou o corpo de bípede para quadrúpede. A decisão de quantos
servos comandam as 4 patas define o gait, a expressividade, o orçamento
de GPIO, a bateria e a complexidade do firmware.

O ESP-HI (referência de design) usa 4 servos SG92R — 1 por pata — com
gait trot diagonal (pata DF+TE, depois DE+TF). É shuffling, não
caminhada real, mas éprovado e simples.

A alternativa seria 8 servos (2 por pata, 2-DOF), permitindo caminhada
com swing de perna, sentar, deitar, dar pata, acenar. Exige redesign
significativo do chassi e gait code do zero.

## Decision

**Usar 4 servos (1 por pata), approach ESP-HI.** Gait trot diagonal.
Capacidade (confirmada pela pesquisa do componente `servo_dog_ctrl`):
andar, recuar, girar esquerda/direita, deitar, curvar (bow), recostar
(lean back), balançar (sway), **dar a pata (shake_hand)**, pular
frente/trás, recolher pernas, poke. — 18 ações no total, não 5.

- **GPIO:** 5, 16, 17, 18 (passados em runtime ao componente — sem
  código hardcoded).
- **Servos:** SG90 ou SG92R (micro 9g, 180°, 4.8V spec, funcionam a
  3.7V LiPo como no ESP-HI).
- **Gait code:** componente `espfriends/servo_dog_ctrl` (v0.2.0,
  ESP Component Registry) — independente do framework xiaozhi, suporta
  `esp32` (WROOM), pins em runtime. Ver ADR-013.
- **Bateria:** 2P 18650 paralelo + capacitor de buffer (ver ADR-011).

## Alternatives Considered

### 8 servos (2 por pata, 2-DOF)
- Caminhada real, senta, dá pata, acena, shake hand.
- +4 GPIO (WROOM tem folga), +4 servos (~$8), bateria maior, chassi
  redesign, gait code do zero.
- **Rejeitado no momento:** o componente `servo_dog_ctrl` já fornece
  shake_hand, bow, sway, jump, poke etc. com 4 servos — o valor
  marginal dos 4 servos extras diminui. Upgrade path preservado.

## Consequences

- Pinagem de servo é a mesma do bípede — firmware de locomoção é
  adaptável.
- Gait é shuffling (trot diagonal), não caminhada real — aceitável para
  um cão que interage por voz, não para corrida.
- Chassi do ESP-HI é quase direto (mesma configuração de 4 servos).
- Upgrade para 8 servos permanece aberto sem invalidar nada.
