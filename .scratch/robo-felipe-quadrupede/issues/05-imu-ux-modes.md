# 05 — Modos de UX do IMU MPU6050

## Type
grilling

## Status
open

## Assignee
unclaimed

## Blocked by
none

## Question

Quais gestos e modos o IMU MPU6050 (I2C, GPIO 21/22) habilita no Robô
Felipe?

O MPU6050 fornece acelerômetro + giroscópio 6-DOF. Possíveis modos:

1. **Shake-to-wake** — sacudir o robô para acordar/iniciar conversa
   (equivalente ao mercury switch do ESP-HI, mas com IMU real).
2. **Fall detection** — detectar quando o cão cai/vira de lado →
   tentar se recuperar (gait de levantar) ou emitir som de "ai!".
3. **Tilt/pet detection** — detectar quando o sobrinho levanta a
   cabeça do cão ou o acaricia (mudança de orientação suave) →
   reação emocional no display (olhos felizes).
4. **Pickup detection** — detectar quando o robô é levantado do chão
   → parar gait, emitir "oi!", mostrar olhos curiosos.
5. **Orientation-aware gait** — ajustar gait conforme inclinação do
   terreno (detecção de subida/descida).

Recomendação preliminar: **1 (shake-to-wake) + 2 (fall detection) + 4
(pickup detection)** no MVP. São os mais valiosos para um cão de
estimação manipulado por uma criança. 3 e 5 são nice-to-have fase 2.

Decidir quais modos entrar no MVP e como mapear cada um ao
comportamento do robô (servos + display + TTS).
