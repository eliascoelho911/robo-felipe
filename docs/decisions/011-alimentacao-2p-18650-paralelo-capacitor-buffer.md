# ADR-011: Alimentação 2P 18650 paralelo + capacitor de buffer

## Status
Accepted

## Date
2026-08-09

## Context

O robô precisa alimentar simultaneamente: WROOM-32E (WiFi STA, ~150mA
médio, ~500mA pico), MAX98357A (TTS, ~80mA), 4× SG90 (gait ativo ~600mA
médio, ~2.8A pico em stall), OLED+IMU+ultrassom (~35mA). Total: ~865mA
médio, ~3.0A pico.

O autor tem duas baterias 18650 etiquetadas "4.2V 9800mAh" (SD18650).
Células 18650 de fabricantes sérios (Samsung, LG, Panasonic) topam em
~3500mAh — o rótulo de 9800mAh excede o limite físico do formato em
~3×. A capacidade real é provavelmente 1000–2000mAh com resistência
interna alta. O risco principal é brownout do WROOM sob pico de stall
dos 4 servos (sag de tensão abaixo de ~3.0V).

## Decision

**Ligar as duas 18650 em paralelo (1S2P) + capacitor eletrolítico
2200–4700µF / 10V no trilho do servo como buffer de pico.**

- **Paralelo:** dobra a capacidade real (~2000–4000mAh) e halved a
  resistência interna — melhor entrega de pico que uma célula só.
- **3.7V nominal:** servos SG90/SG92R são spec'd 4.8V mas funcionam a
  3.7V (undervoltaged, um pouco fracos/lentos) — o ESP-HI faz exatamente
  isto e aceita o trade. O WROOM, amp, display, IMU aceitam 3.7V direto
  (LDO onboard baixa para 3.3V).
- **Capacitor de buffer:** absorve o spike de stall dos servos, evita
  brownout do WROOM. Custo ~$1. Prática padrão em projetos com servo.
- **Sem BMS extra, sem buck** — caminho simples, probado.
- **Runtime estimado:** ~2–4h (depende da capacidade real das células).

## Alternatives Considered

### Série (2S) + BMS 2S + buck → 5V
- Servos em full power (5V spec), melhor torque e velocidade.
- +BMS 2S (~$3) + buck converter (~$2) + complexidade.
- Risco de desbalanceamento com células de qualidade duvidosa.
- **Rejeitado:** complexidade extra para ganho marginal. O ESP-HI prova
  que 3.7V direto é funcional para 4 micro-servos.

### 3P (três células em paralelo)
- +50% runtime e resistência interna, mas +50% peso/volume.
- 3× 18650 não cabe no chassi do ESP-HI (desenhado para 702040) sem
  redesenhar.
- **Rejeitado:** diminishing returns. 2P já resolve o pico com capacitor.

### Comprar 2× Samsung 30Q (3500mAh real, 20A pico)
- Runtime ~6–8h, pico sem preocupação, ~$8.
- **Adiado:** o autor já tem as 2 células. Se a capacidade real não
  entregar, esta é a solução definitiva.

## Consequences

- Alimentação simples: 1S2P direto, sem reguladores extra.
- Servos undervoltaged (3.7V vs 4.8V spec) — funcionam mas com torque
  reduzido. Aceitável para um cão que shuffles, não corre.
- Capacitor de buffer é mandatório para evitar brownout.
- Runtime limitado pela capacidade real das células (a verificar).
- Se brownout persistir, comprar Samsung 30Q é o caminho de upgrade.
