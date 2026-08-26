# ADR-017: Tamagotchi inclui câmera GC0308 e sensor de proximidade

## Status
Accepted

## Date
2026-08-23

## Context

O ADR-016, ao definir a variante Tamagotchi, descreveu-a como "um pet de
bolso, com display, microfone e alto-falante, **sem câmera, sem pernas, sem
servos**" e, ao avaliar o M5Stack CoreS3 como plataforma, registrou que a
placa "traz câmera (indesejada)". Essa posição foi registrada na época da
decisão sobre o transporte de voz (sem relay) e não constitui uma decisão
explícita sobre a câmera — era uma consequência colateral de manter o
escopo mínimo.

O CoreS3 traz, de fábrica, na mesma fita ribbon:

- **GC0308** — câmera DVP de 0,3 MP, interface SCCB (I²C @ 0x21) + barramento
  paralelo de 8 bits (D0–D7) + PCLK/VSYNC/HREF, ligada ao controlador **DVP
  do ESP32-S3** (12 GPIO dedicados).
- **LTR-553ALS-WA** — sensor de luz ambiente + proximidade (I²C @ 0x23), que
  pode detectar a mão do Sobrinho se aproximando do vidro.

O usuário final (o Sobrinho, 8 anos) **quer a câmera** como parte da
interação com o bichinho. A câmera está fisicamente presente na placa
candidata (CoreS3) e não conflita com a premissa central do ADR-016 (ser
autocontido, sem relay de smartphone) — a câmera é um periférico local, não
um segundo dispositivo.

## Decision

**O Tamagotchi inclui a câmera GC0308 e o sensor de proximidade LTR-553ALS-WA
no seu escopo de hardware.** Esta ADR **supersede**, apenas para a variante
Tamagotchi, a menção "câmera (indesejada)" registrada no ADR-016 (Context e
Consequences). O ADR-016 permanece Accepted e inalterado como registro
histórico da decisão sobre transporte de voz; esta ADR registra a decisão
específica sobre a câmera, que na época do ADR-016 não havia sido tomada.

O subsistema de visão fica em escopo, com casos de uso a serem refinados:
interação visual com o Sobrinho, "olhos" do pet que reagem ao que vê,
detecção de presença/proximidade (mão perto do vidro) e eventuais
funcionalidades de visão local.

## Alternatives Considered

### Manter "sem câmera" (estado do ADR-016)

- **Prós:** escopo mínimo; GPIOs do barramento DVP (12 pinos) ficam livres
  para outro uso; menos um driver para manter; menos consumo de potência.
- **Contras:** **contraria o desejo explícito do usuário final** (o Sobrinho
  quer a câmera); desperdiça hardware já presente na placa candidata; perde
  a oportunidade de interação visual, que é natural num pet de bolso.
- **Rejeitada:** o usuário dono do projeto definiu que a câmera é desejada.

### Usar só o sensor de proximidade (LTR-553), sem a câmera

- **Prós:** detecção de mão aproximando → "pet acorda ao ver o Sobrinho";
  baixo consumo (I²C, sem barramento DVP); pouco código.
- **Contras:** descartaria a câmera que o Sobrinho pediu; perde visão real.
- **Rejeitada como decisão exclusiva:** o sensor de proximidade **entra
  também** (é parte da mesma fita), mas não substitui a câmera — vem como
  complemento.

## Consequences

### Positivas

- **Interação visual do pet** — o Tamagotchi pode "ver" e reagir ao Sobrinho,
  enriquecendo a sensação de bichinho vivo (a premissa central do form
  factor). Combina com a UI no display e com o IMU.
- **Detecção de proximidade** (LTR-553ALS-WA) → o pet pode "acordar" quando a
  mão se aproxima, complementando o wake-on-touch e o wake-on-motion.
- **Hardware reaproveitado** — câmera e sensor já estão no CoreS3; nenhum
  custo de BOM adicional, só firmware.
- **Alinhamento com a plataforma candidata** — o CoreS3 deixa de ser avaliado
  "apesar da câmera" e passa a ser avaliado "com a câmera", simplificando a
  futura ADR de hardware do Tamagotchi.

### Negativas

- **12 GPIO do barramento DVP ocupados** (D0–D7 + PCLK/VSYNC/HREF) — antes
  tratados como "liberáveis"; agora comprometidos com a câmera. Revisa a
  análise de pinout livre do ESP32-S3 no CoreS3.
- **Driver e memória** — captura de frames consome CPU e RAM (buffers na
  PSRAM); o pipeline de voz (TLS + áudio + KWS) já é o gargalo principal.
  Visão deve rodar em janelas curtas ou num core separado para não degradar
  a latência da conversa.
- **Consumo de potência** — a câmera acrescenta carga à bateria; deve ser
  desligada em deep-sleep (o AXP2101 corta o rail) e ligada só quando
  relevante à interação.
- **Escopo do firmware cresce** — mais um subsistema (driver DVP + SCCB +
  eventual pipeline de visão) para desenvolver e manter.

### Notas

- **Datasheets de referência:** GC0308 e LTR-553ALS-WA estão hospedados na
  documentação do M5Stack CoreS3 (ver `hardware/cores3/CoreS3-capacidades.md`
  e a [página oficial](https://docs.m5stack.com/en/core/CoreS3)).
- **Controle de reset/enable:** o reset da câmera (GC0308 RST) é controlado
  pelo expansor AW9523B (P1_0), igual ao LCD e ao touch — não exige GPIO
  novo do ESP32.
- **Imutabilidade do ADR-016:** esta ADR não reescreve o ADR-016; ele
  permanece como registro histórico da decisão sobre transporte de voz. A
  menção "câmera (indesejada)" lá é correta para o contexto da época — a
  exceção é registrada aqui, por escopo, no mesmo modelo que o ADR-016 usou
  para superseder o ADR-002 sem reescrevê-lo.
- **Casos de uso de visão** ficam para detalhamento em ADR futura ou no
  backlog; esta ADR decide apenas que a câmera entra no escopo de hardware.
