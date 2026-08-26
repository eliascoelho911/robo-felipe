# Motor de Pet Virtual e UI Animada para o Tamagotchi (CoreS3)

> **Ticket de pesquisa** que subsidia a camada de "pet vivo" da
> variante Tamagotchi. O ADR-016 estabelece que o bichinho deve ser
> autocontido e persistir entre deep-sleeps; o
> [ADR-017](../decisions/017-tamagotchi-inclui-camera-gc0308.md)
> coloca a câmera no escopo. Este documento cobre **qual motor de
> Tamagotchi adotar** (stats, evolução, ciclo de vida) e **qual stack
> gráfica usar** para a UI animada (olhos/boca/barras) no display
> ILI9342C 320×240 do M5Stack CoreS3.

| | |
|---|---|
| **Data da pesquisa** | 2026-08-26 |
| **Status** | Concluída — recomendação clara (TamaFi base + catode32 design ref + M5GFX/LVGL) |
| **Alimenta** | futura ADR de firmware/pet do Tamagotchi |
| **Confiança** | Alta — métricas de repositório verificadas via GitHub API em 2026-08-26 |

## Metodologia e fontes

Busca via `gh search repos` / `gh search code` + `webfetch` de READMEs.
Métricas verificadas em 2026-08-26.

## Premissa do "pet vivo"

O Tamagotchi **dorme** em deep-sleep (~49 µA, ver
[`hardware/cores3/CoreS3-capacidades.md`](../../hardware/cores3/CoreS3-capacidades.md)
§6) e acorda por RTC horário, toque, movimento ou proximidade. Logo o
motor de pet precisa **avançar stats por tempo de relógio decorrido**
no wake, não por `millis()` de uptime. Este é o critério decisivo de
adequação — e é onde todos os candidatos falham.

---

## 1. Motores de Tamagotchi / pet-engine

### Tabela comparativa

| # | Repo | ★ | Último commit | Licença | Lang | O que modela | Persistência | Display | ESP32-S3? | RTC catch-up? | Compl. pet |
|---|---|---:|---|---|---|---|---|---|---|---|---:|
| 1 | `moonbench/catode32` | 96 | 2026-06-27 | **MIT (code) / CC-BY-NC-ND 4.0 (artwork)** | MicroPython | **18 stats** (4 decay tiers: daily/weekly/monthly/very-slow), **25 behaviors**, minigames, store, locations, weather, gardening, sickness, ESP-NOW/BLE playdates, **adoption rotation = death/rebirth** | flash `/save.json` + `/backup.json` | SSD1306 128×64, ESP32-**C6/C3** | ❌ (C6/C3, needs port to S3) | ⚠ closest design (time-based decay), but always-on loop | 9/10 pet · 4/10 drop-in fit |
| 2 | `MaliosDark/Sablina-Tamagotchi-ESP32` | 24 | 2026-05-04 | GPL-2.0 (copyleft) | C (Arduino + ESP-IDF) | Full pet sim (stats/mood/evolution), **65 animated RGB565 face sprites**, BLE peer + bond/memory, on-device TinyStories LLM personality, Telegram bot | NVS + SPIFFS (faces as `.rgb565`) | 1.47" IPS **320×172** | ✅ (16 MB / 8 MB PSRAM — matches) | ❌ | 8/10 pet · ⚠ ships WiFi-audit (deauth/PMKID/handshake) — **inappropriate for an 8-yo**; monolithic 272 KB `.ino` |
| 3 | `cifertech/TamaFi` | 388 | 2026-06-06 | **MIT** | C (Arduino) | hunger/happiness/health + traits (curiosity/activity/stress) + **7 moods** + **evolution BABY→TEEN→ADULT→ELDER** + **death + rebirth** (reset to BABY) | **NVS via `Preferences`** (hunger/happy/health/stage) | ST7789 240×240 (TFT_eSPI + TFT_eSprite) | ✅ | ❌ ticks on `millis()` (uptime) — no RTC catch-up; WiFi-scan "feeding" | 7/10 |
| 4 | `MikuruM/Mikuru_Tamagotchi_ESP32` | 83 | 2023-02-22 (stale) | GPL-2.0 | C (Arduino) | rooms/games/eat/sleep/shop; sprites as C arrays (~10 MB+ of `.h`) | unclear (single 150 KB `.ino`, undocumented) | LILYGO T-QT Pro ESP32-S3 | ✅ | ❌ | 6/10 — undocumented, monolithic, stale, copyleft |
| 5 | `dominikwojcicki/tamafi` | 2 | 2026-05-02 | **MIT** | C (Arduino) | Same engine as TamaFi (touch-screen fork) | NVS `Preferences` | **LCDWiki ESP32-S3 + ILI9341 + FT6336 touch** (closest to CoreS3), Arduino_GFX backend; 240×240 logical scaled | ✅ | ❌ | 6/10 — best hardware reference (ILI9341≈ILI9342C, FT6336≈FT6336U) |
| 6 | `ad-naan/AdPet-ESP32-S3` | 2 | 2026-07-17 | **NONE (not reusable)** | C++ | Clean skeleton: `PetBrain`, `Emotion`, `VoiceManager`, `DisplayManager`, `LlmClient`; expression switching | `ConfigManager` | ESP32-S3 SuperMini | ✅ | ❌ | 3/10 — minimal; **no license ⇒ cannot legally reuse** |

### Recomendação (Need 1)
- **Use `cifertech/TamaFi` como base C++** — MIT, ESP32-S3, NVS-persistente,
  já modela mood + 4 estágios de evolução + morte + rebirth + loop
  non-blocking. Melhor ponto de partida drop-in.
- **Use `moonbench/catode32` como *referência de design* para enriquecer
  o motor** — portar seu modelo superior (18 stats com decay tiers, 25
  behaviors, sickness, minigames, store, adoption/rebirth) de MicroPython
  para C++. Seu **código é MIT** (reutilizável), mas a **arte é
  CC-BY-NC-ND** (não pode distribuir — ver gaps).
- **Referência `dominikwojcicki/tamafi`** para a adaptação
  ILI9341+FT6336+touch em S3 (mas ainda é 240×240).
- **Evitar como base**: Sablina (GPL copyleft + features de WiFi-hacking
  inadequadas p/ 8 anos), Mikuru (GPL, sem docs, stale), AdPet (sem
  licença).

---

## 2. Bibliotecas gráficas / UI para face animada no ILI9342C 320×240

### Tabela comparativa

| # | Repo | ★ | Último commit | Licença | Fit CoreS3/ILI9342C | Animação | Partial update (olhos/boca) | PSRAM | Compl. UI |
|---|---|---:|---|---|---|---|---|---|---:|
| 1 | `m5stack/M5Unified` + `m5stack/M5GFX` | 687 / 369 | 2026-08-26 (ativo) | **MIT** | **Native CoreS3** — ILI9342C ✅, FT6336U touch, AXP2101 power/battery, **BM8563 RTC + wake timer** (hourly deep-sleep!), I2S speaker/mic, IMU | Sem framework — DIY frame-table loop em `M5Canvas` sprites | ✅ `M5Canvas.pushSprite(x,y)` blits só a sub-região mudada; SPI DMA | ✅ PSRAM-aware (`M5.setSPRAM`) | 8/10 |
| 2 | `lvgl/lvgl` | 24.499 | 2026-08-26 (ativo) | **MIT** | ESP32-S3 via `esp_lcd` panel (ILI9341/ILI9342 family) — precisa wiring de driver display+touch (ou usar **M5GFX como flush backend**) | ✅ **`lv_anim`** completo + transições + temas + widgets/fontes | ✅ **Invalidation-based PARTIAL redraw** (`LV_DISPLAY_RENDER_MODE_PARTIAL`) — redesenha só regiões sujas = ideal p/ boca/olhos | ✅ double-buffer em PSRAM, DMA2D opcional | 10/10 UI · mais pesado |
| 3 | `lovyan03/LovyanGFX` | 1.740 | 2026-08-25 | MIT | **ILI9342C autodetect ✅** (M5GFX é fork CoreS3-tuned deste) | DIY (sprites) | ✅ sprite `pushSprite` + DMA | ✅ | 7/10 (cross-board; M5GFX é a melhor escolha p/ CoreS3) |
| 4 | `Bodmer/TFT_eSPI` | 4.888 | 2026-04-03 | custom permissive (non-OSI) | ESP32-S3 DMA ✅; tem `Setup12_M5Stack_Basic_Core` (ILI9342 clássico) mas **sem setup CoreS3** (perde helpers M5 PMIC/touch) | DIY (TFT_eSprite) | ✅ `sprite.pushSprite(x,y)` | ✅ | 6/10 — o que TamaFi usa; não ideal p/ CoreS3 |
| 5 | `moononournation/Arduino_GFX` | 1.133 | 2026-08-20 | MIT | broad controllers (ILI9341 family); usado pelo fork tamafi touch em S3 | DIY | ✅ `setAddrWindow` partial | ✅ | 5/10 |
| 6 | `adafruit/Adafruit-GFX-Library` | 2.828 | 2026-04-09 | BSD-3 | portable core + driver; **sem DMA/SPI optimization**, sem sprite-diff | DIY | weak (manual `setAddrWindow`, lento) | parcial | 4/10 — lento demais p/ animação 320×240 suave |

### Recomendação (Need 2)
- **Stack primária: `M5Unified` + `M5GFX`** (camada nativa do CoreS3)
  para display/touch/power/**RTC deep-sleep wake**/speaker — todo o
  hardware que o pet precisa, turnkey, MIT.
- **Adicionar LVGL por cima** se quiser um framework de UI completo
  (barras de status, menus, ticker, fontes, sistema de animação). O
  **partial redraw por invalidação** do LVGL é exatamente o update
  eficiente de olhos/boca descrito no
  [`CoreS3-capacidades.md`](../../hardware/cores3/CoreS3-capacidades.md)
  §5, e ele pode flushar via M5GFX (`M5.Display.pushImage` no
  `flush_cb`).
- **Para a animação de face do pet especificamente**, o padrão mais
  eficiente é um `M5Canvas` sprite dedicado por parte do corpo (olhos,
  boca) blitted com `pushSprite(x,y)` — ou `lv_image`/`lv_canvas` frames
  driven por `lv_anim`. Evitar TFT_eSPI no CoreS3 (precisa config
  custom, perde helpers M5 PMIC/touch).

---

## 3. Alguma solução cobre AMBOS (motor + UI)?

| Repo | Engine | UI/graphics | Fit CoreS3 + C++ + licença permissiva? |
|---|---|---|---|
| `cifertech/TamaFi` | ✅ engine | ✅ TFT_eSPI sprites + bars + menus | ⚠ ESP32-S3 sim, mas **240×240 ST7789, button-only, TFT_eSPI** — precisa portar p/ M5GFX/ILI9342C/touch/320×240 |
| `MaliosDark/Sablina-Tamagotchi-ESP32` | ✅ full | ✅ 65 sprites RGB565 animados | ❌ **GPL + WiFi-hacking** (precisa stripar + relicença impossível); 320×172 não 320×240 |
| `moonbench/catode32` | ✅ mais rico | ✅ renderer + scenes | ❌ **MicroPython + OLED 128×64** (não C++/CoreS3) |
| `MikuruM/...` | ✅ (opaco) | ✅ sprite-heavy | ❌ GPL, sem docs, stale |

**Nenhum projeto cobre ambos E fita CoreS3** (ILI9342C 320×240 + FT6336U
+ C++ + licença permissiva + deep-sleep). O **mais próximo de um
ponto de partida único é o TamaFi** — tem engine + UI + sprites em
ESP32-S3 sob MIT — mas ainda exige uma portabilidade real para
M5GFX/CoreS3. Sablina é a combo mais completa mas é legal/eticamente
inadequada como base de um produto infantil.

---

## Recomendação consolidada

- **Motor**: `cifertech/TamaFi` (C++ base, MIT) — portar de ST7789→M5GFX,
  botões→touch. Enriquecer o modelo de stats com o design do
  `moonbench/catode32` (18 stats / decay tiers / behaviors /
  minigames), em C++.
- **UI**: `m5stack/M5Unified` + `M5GFX` (nativo CoreS3) como base; opção
  de adicionar `lvgl/lvgl` por cima para framework de UI (barras,
  menus, ticker, partial redraw por invalidação).
- **Build**: o glue do **RTC catch-up** + **sprites originais** + nova
  mecânica de alimentação por touch.

---

## Lacunas que exigem código custom (não há pronto)

1. **Deep-sleep + RTC hourly wake + catch-up (o maior gap).** Todo
   engine tica stats em `millis()`/uptime (TamaFi) ou loop always-on
   (catode32, Sablina). **Nenhum** avança stats por tempo de
   relógio/RTC no wake. Precisa escrever: (a) alarme BM8563 + deep-sleep
   + wake-timer via `M5.Rtc`/`M5.Power`, e (b) um
   `advanceStats(elapsedSeconds)` que decaia fome/saúde/etc. por tempo
   decorrido. **Os decay tiers do catode32 (daily/weekly/monthly) são o
   modelo a replicar.**
2. **Adaptação de hardware CoreS3.** Remapear TamaFi ST7789 240×240 /
   TFT_eSPI / 6 botões → **M5GFX ILI9342C 320×240 + FT6336U touch** +
   AXP2101 (`M5Unified Power`) + I2S speaker (`M5Unified Speaker`) em
   vez de buzzer/NeoPixel.
3. **Sprites de pet + pipeline de animação.** Nenhum projeto entrega arte
   reutilizável: catode32 é CC-BY-NC-ND; TamaFi/Sablina usam assets
   itch.io de terceiros. Precisa source/desennhar **sprites RGB565
   originais** + frame-table loop + partial blit por região (olhos/boca).
   O mapeamento behavior→animação do catode32 e o pipeline
   RGB565-SPIFFS da Sablina são boas referências.
4. **Redesign da alimentação por WiFi.** TamaFi/Sablina "alimentam" o
   pet via WiFi scans — unreliable/inadequado p/ um pocket pet offline
   de 8 anos. Substituir por **alimentação/minigames por touch**
   (minigames/store do catode32 é o modelo de referência).
5. **Tuning de evolução/idade.** TamaFi evolui em minutos (TEEN@20,
   ADULT@60, ELDER@180 min) tuned p/ demo always-on; p/ um pet com wake
   horário a escala de tempo toda precisa re-tunar para
   horas/dias de relógio.
6. **Licenciamento/segurança infantil.** Manter cadeia all-MIT: TamaFi
   (engine) + catode32 código (ref de design) + M5GFX/M5Unified + LVGL
   = tudo MIT. Stripar/evitar Sablina (GPL + hacking) e AdPet (sem
   licença); não reusar arte catode32/Sablina/TamaFi.

---

## Referências

| Componente | URL | Licença | Verificado em |
|:--|:--|:--|:--|
| `cifertech/TamaFi` | https://github.com/cifertech/TamaFi | MIT | 2026-08-26 |
| `moonbench/catode32` | https://github.com/moonbench/catode32 | MIT (code) / CC-BY-NC-ND 4.0 (art) | 2026-08-26 |
| `MaliosDark/Sablina-Tamagotchi-ESP32` | https://github.com/MaliosDark/Sablina-Tamagotchi-ESP32 | GPL-2.0 | 2026-08-26 |
| `MikuruM/Mikuru_Tamagotchi_ESP32` | https://github.com/MikuruM/Mikuru_Tamagotchi_ESP32 | GPL-2.0 | 2026-08-26 |
| `dominikwojcicki/tamafi` | https://github.com/dominikwojcicki/tamafi | MIT | 2026-08-26 |
| `ad-naan/AdPet-ESP32-S3` | https://github.com/ad-naan/AdPet-ESP32-S3 | NONE | 2026-08-26 |
| `m5stack/M5Unified` | https://github.com/m5stack/M5Unified | MIT | 2026-08-26 |
| `m5stack/M5GFX` | https://github.com/m5stack/M5GFX | MIT | 2026-08-26 |
| `lvgl/lvgl` | https://github.com/lvgl/lvgl | MIT | 2026-08-26 |
| `lovyan03/LovyanGFX` | https://github.com/lovyan03/LovyanGFX | MIT | 2026-08-26 |
| `Bodmer/TFT_eSPI` | https://github.com/Bodmer/TFT_eSPI | custom permissive | 2026-08-26 |
| `moononournation/Arduino_GFX` | https://github.com/moononournation/Arduino_GFX | MIT | 2026-08-26 |
| `adafruit/Adafruit-GFX-Library` | https://github.com/adafruit/Adafruit-GFX-Library | BSD-3 | 2026-08-26 |
