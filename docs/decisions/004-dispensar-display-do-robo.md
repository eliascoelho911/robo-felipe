# ADR-004: Dispensar display do robô

## Status
Accepted

## Date
2026-07-12

## Context

O ADR-001, em seu contexto, listava como adição planejada:

> **Display** para status/expressões (OLED SSD1306 via I2C, dado o
> orçamento de RAM — TFT framebuffer consumiria ~115 KB dos 333 KB
> disponíveis).

Na época, um display parecia desejável para: mostrar estado do robô
(andando, ouvindo, falando), ícones de expressão, nível de bateria,
feedback de comandos.

Após a decisão de usar o **smartphone como relay local + UI unificada**
(ADR-002), o app mobile passa a concentrar **toda a interface com o
usuário**. Isso muda o cálculo de valor do display embarcado:

- O usuário já olha para o celular para falar com o robô (KWS dispara,
  app mostra transcrição, TTS é roteado).
- O feedback visual de estado (andando, ouvindo, falando) pode morar no
  app, em tela cheia e colorida — superior a um OLED 128×64.
- Um display no robô exigiria: 2 pinos I2C (GPIO 21/22), ~1–2 KB de
  framebuffer, uma task de render (~5% de um core), e fiação extra no
  chassi já apertado do bípede.

O orçamento de RAM do ESP32-WROOM-32E-N4 (~333 KB, ver ADR-001) comporta
o OLED sem estresse (~1 KB de framebuffer). A questão aqui **não é RAM** —
é **valor**: o display deixou de agregar porque a UI migrou para o
celular.

## Decision

**Dispensar o display do robô.** Sem OLED, sem TFT, sem fiação de
display. O feedback visual do estado do robô (andando, ouvindo, falando,
bateria, erros) é exibido no **app mobile** (relay/UI, ver ADR-002). O
robô mantém os LEDs de bateria já presentes na placa-mãe ACEBOTT
original (4 LEDs azuis de carga + 1 LED vermelho de carga em andamento).

Esta decisão **refina o contexto do ADR-001**: a adição de display
prevista lá **não será implementada**. O ADR-001 permanece aceito quanto
à escolha de C/C++ e FreeRTOS; apenas o item "display" da lista de
adições planejadas fica obsoleto.

## Alternatives Considered

### OLED SSD1306 via I2C (como planejado no ADR-001)

- **Prós:**
  - ~1 KB de framebuffer, baixo custo de RAM e CPU.
  - Feedback local no robô, útil para debug sem o app conectado.
  - Bibliotecas maduras (Adafruit_SSD1306, u8g2).
- **Contras:**
  - **Redundante com a UI do app** (ADR-002) — dois lugares mostrando
    o mesmo estado.
  - **2 pinos I2C** (GPIO 21/22) que poderiam servir a outros fins.
  - **Fiação e montagem** no chassi apertado do bípede.
  - **Task de render** (~5% de um core) para 10 fps de status que quase
    ninguém olha (o usuário olha o celular).
- **Rejeitada:** o valor agregado é baixo dado que a UI primária está no
  app. A complexidade (fiação, pinos, task) não se justifica.

### TFT colorido (ST7789 / ILI9341) via SPI

- **Prós:**
  - Cores, maior resolução, capacidade de ícones animados.
- **Contras:**
  - **Framebuffer de 115 KB+** (240×240×16bit) consome ~35% da RAM
    livre — inviável sem PSRAM (ver ADR-001).
  - **4–5 pinos SPI** dedicados.
  - **CPU de render** significativa para SPI.
- **Rejeitada:** RAM insuficiente no N4 sem PSRAM; e mesmo a PSRAM não é
  DMA-capaz para SPI de display no ESP32 clássico. Inviável.

### LEDs RGB endereçáveis (WS2812) como "expressão"

- **Prós:**
  - Feedback visual mínimo (cor = estado: verde=ouvindo, azul=falando,
    vermelho=erro).
  - 1 pino GPIO.
- **Contras:**
  - **WS2812 exige timing em ns** — no ESP32 só é estável via RMT ou
    I2S-bitbang, não via `machine.Pin` direto. Biblioteca `Adafruit_NeoPixel`
    resolve mas adiciona dependência e ~2 KB.
  - **Ainda é redundante** com o app para feedback de estado principal.
- **Rejeitada:** adiciona complexidade por valor marginal. Os LEDs de
  bateria nativos da placa ACEBOTT já cobrem o feedback mínimo "robô
  ligado / bateria fraca".

## Consequences

### Positivas

- **2 pinos I2C liberados** (GPIO 21/22) — ficam disponíveis para
  expansão futura (ex.: sensor extra, segundo barramento).
- **Sem framebuffer, sem task de render** — +1–2 KB de RAM e +5% de CPU
  liberados para a pipeline de voz.
- **Montagem mais simples** — menos fiação, menos componentes no chassi
  apertado, menor peso.
- **Fonte de verdade única para UI** — o app mobile; sem risco de
  estado divergente entre robô e app.
- **Debug via app** — logs e estado aparecem no celular, mais legível
  que um OLED 128×64.

### Negativas

- **Robô sem feedback visual autônomo** — se o app não estiver
  conectado, o robô só indica estado pelos LEDs de bateria nativos.
  Para debug de bancada sem celular, é menos conveniente que um OLED.
- **Sem expressões/carinha no robô** — o robô não "olha" para o
  usuário; a personalidade visual fica toda no app.
- **Se o app falhar**, não há fallback de UI no robô.

### Notas

- Os **LEDs de bateria nativos** da placa ACEBOTT (4 azuis + 1
  vermelho) permanecem e cobrem feedback essencial de energia/carga.
- Para debug de bancada sem o app, pode-se usar **Serial.println()** via
  USB (estilo tutorial original) como canal de diagnóstico secundário.
- Se no futuro for desejável um display para modo autônomo (robô
  operando sem celular), revisitar OLED via I2C — a pinagem e a RAM
  continuam compatíveis. Esta ADR só diz que **não será incluído no
  escopo atual**.
- Esta ADR **não invalida** ADR-001; apenas refina o item "display" da
  lista de adições planejadas no contexto do ADR-001.
