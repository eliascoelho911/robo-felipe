# ADR-009: Adicionar display OLED para expressões do cão (reverte ADR-004)

## Status
Accepted

## Date
2026-08-09

## Context

ADR-004 dispensou o display do robô bípede porque "o app mobile é a UI
unificada". O argumento era correto para o bípede: um bípede sem cara é
um andarilho, e o feedback visual mora no celular.

O pivot para quadrúpede (ADR-008) muda o cálculo de valor. Um cão sem
cara é um robô, não um cão. A face é o canal emocional primário de um
animal de estimação — um niño de 8 anos se conecta com os olhos do
cachorro, não com texto no celular. Olhos que piscam, arregalam, ficam
tristes ou felizes são a diferença entre um gadget e um bicho de
estimação.

## Decision

**Adicionar um display OLED SSD1306 128×64 monocromático via I2C como
"rosto" do cão.** O display ocupa a área dos olhos no chassi, mostrando
olhos animados (piscando, arregalados, triste, feliz, dormindo) e
status mínimo (bateria, modo). Reverte ADR-004.

- **GPIO 21 (SDA) + GPIO 22 (SCL)** — barramento I2C compartilhado com
  o IMU MPU6050 (multi-drop, sem GPIO extra).
- **Framebuffer ~1 KB** — sem PSRAM, sem impacto no orçamento de RAM.
- **Task de render ~5% de um core** a 10 fps — trivial.

## Alternatives Considered

### TFT 0.96" colorido (ST7789) via SPI
- Olhos coloridos, animation fluida.
- Framebuffer ~64 KB → exige PSRAM ou scrambling. +5 GPIO SPI.
- **Rejeitado:** custo de RAM/GPIO alto para benefício marginal. Olhos
  em silhueta monocromática são expressivos (estilo Pingu).

### Sem display (manter ADR-004)
- UI toda no app.
- **Rejeitado:** cão sem cara perde o canal emocional primário.

## Consequences

- ADR-004 é revertida — o display agora agrega valor no contexto do cão.
- 2 pinos I2C (GPIO 21/22) são reaproveitados, compartilhando o
  barramento com o IMU (multi-drop). Zero GPIO extra.
- +1 KB de RAM e +5% de CPU para a pipeline de voz — folga ampla.
- Montagem: display na face frontal do chassi, na área dos "olhos".
