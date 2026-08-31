# ADR-021: Firmware do Tamagotchi — xiaozhi-esp32 como base com customizações pt-BR, UI de pet e OTA wrapper

## Status

Accepted

## Date

2026-08-31

## Context

As decisões anteriores fixaram o contorno do Tamagotchi: hardware é o M5Stack
CoreS3 ([ADR-019](019-tamagotchi-hardware-m5stack-cores3.md)); o dispositivo é
autocontido, sem relay, terminando TLS ele mesmo ([ADR-016](016-tamagotchi-processa-voz-sem-relay-de-smartphone.md));
a câmera GC0308 e o LTR-553 estão no escopo ([ADR-017](017-tamagotchi-inclui-camera-gc0308.md));
o comportamento mora num Core em TypeScript auto-hospedado ([ADR-018](018-tamagotchi-comportamento-mora-no-core-typescript.md));
o OTA é pull com esp32FOTA e manifest no GitHub Releases ([ADR-020](020-tamagotchi-ota-pull-com-esp32fota.md)).

Resta decidir **qual firmware roda no CoreS3** e quais customizações são
necessárias para transformá-lo de assistente de voz genérico no Robô Felipe.

O research [`../research/tamagotchi-firmware-voz.md`](../research/tamagotchi-firmware-voz.md)
(2026-08-26, verificado) recomenda adotar `78/xiaozhi-esp32` como
firmware-base. Esta ADR confirma essa adoção e registra as customizações
decididas, com base na inspeção direta do código-fonte do xiaozhi-esp32
(verificado em 2026-08-31 via GitHub API + `webfetch` de arquivos-fonte).

### Por que o xiaozhi-esp32 é o firmware-base

`78/xiaozhi-esp32` (29.181 ★, MIT, ESP-IDF v5.5.2+, 138 boards) entrega, num só
firmware, o pipeline completo que o Tamagotchi exige:

- **Board `m5stack/core-s3` first-class** — `main/boards/m5stack/core-s3/config.h`
  usa os mesmos GPIOs e codecs do CoreS3 (I2S MCLK=0/WS=33/BCLK=34, I²C
  SDA=12/SCL=11, AW88298+ES7210). **Zero portabilidade de pinout.**
- **Pipeline de voz E2E** — KWS local (esp-sr/WakeNet+AFE) → Opus → WSS/TLS
  (terminado no dispositivo, ADR-016) → ASR → LLM → TTS → Opus → speaker.
- **Câmera GC0308 + CoreS3 nativos** — `config.json` já configura
  `CONFIG_CAMERA_GC0308=y`, DVP 320×240 YUV422 20FPS. Visual-Q&A via MCP
  `self.camera.take_photo` + `Explain()` (POST multipart à Nuvem, ADR-017).
- **LVGL 9.5 já integrado** — `main/display/lvgl_display/` (theme, image,
  emoji_collection, dynamic_glyph_cache) + `esp_lvgl_port`. Não é uma
  adição a fazer; é infra já embarcada.
- **WiFi provisioning nativo** — `USE_HOTSPOT_WIFI_PROVISIONING` (captive
  portal) ou `USE_ESP_BLUFI_WIFI_PROVISIONING` (BLE). Dispensa WiFiManager
  externo.
- **OTA nativo** (substituído por esp32FOTA, ver ADR-020).
- **i18n por assets** — `main/assets/locales/<lang>/` com `language.json`
  (strings de UI) + áudios `.ogg` (TTS de sistema). Existe `pt-PT`; `pt-BR`
  é criar um diretório.

O servidor `xinnan-tech/xiaozhi-esp32-server` (10.432 ★, MIT) é a "Nuvem"
orquestradora (ASR/LLM/TTS plugável, comunidade pt-BR) — configurado do lado
servidor, sem mudar o firmware.

### Achados da inspeção do código (2026-08-31)

1. **Kconfig.projbuild** — wake word tem 3 modos: `USE_ESP_WAKE_WORD`
   (WakeNet s/AFE, C3/C5/C6), `USE_AFE_WAKE_WORD` (WakeNet+AFE, S3+PSRAM,
   default), `USE_CUSTOM_WAKE_WORD` (MultiNet, só zh/en pinyin). O default
   para S3+PSRAM é `USE_AFE_WAKE_WORD` com modelo `wn9_nihaoxiaozhi`
   (chinês "你好小智"). Nenhum suporta "Felipe" em pt-BR.
2. **AEC device-side** — `USE_DEVICE_AEC` depende de board específica;
   CoreS3 **não está na lista**. Há `USE_SERVER_AEC` (instável, depende do
   servidor). Sem AEC, o speaker tocando TTS vira eco no mic.
3. **Partições v2/16m.csv** — `nvs` (16KB) + `otadata` (8KB) + `phy` (4KB) +
   `ota_0` (~4MB) + `ota_1` (~4MB) + `assets` (SPIFFS 8MB). Já tem A/B de
   app (rollback) e partição de assets OTA-atualizável.
4. **sdkconfig.defaults.esp32s3** — `CONFIG_SPIRAM_MODE_OCT=y` (global), mas
   `config.json` do CoreS3 appenda `CONFIG_SPIRAM_MODE_QUAD=y` (sobrescreve
   para QUAD). Hardware é octal; firmware configura quad.
5. **i18n** — `scripts/gen_lang.py` gera `lang_config.h` a partir de
   `language.json` + áudios `.ogg`, com fallback en-US. Strings são ~50
   chaves (UI de chatbot: "A escutar...", "A falar...", "Bateria fraca").
6. **Display styles** — `USE_DEFAULT_MESSAGE_STYLE` (chat), `USE_WECHAT_MESSAGE_STYLE`,
   `USE_EMOTE_MESSAGE_STYLE` (animação de emote, restrito a boards
   específicas, CoreS3 fora).

## Decision

**Adotar `78/xiaozhi-esp32` (ESP-IDF, board `m5stack/core-s3`) como
firmware-base do Tamagotchi, com as customizações abaixo.** O firmware é um
fork do xiaozhi que substitui a identidade de assistente de voz genérico pela
identidade do Robô Felipe (pet de bolso pt-BR para o Sobrinho de 8 anos).

### 1. Wake word — push-to-talk por toque no MVP

Até que a wake word "Felipe" em pt-BR seja treinada (Gap #1, rota
microWakeWord + Piper pt-BR + ponte `xiaozhi-tflite`, ver research
`tamagotchi-firmware-voz.md`), o MVP usa **push-to-talk por toque no
display**: o Sobrinho toca na tela para abrir a sessão de voz. Isso evita
forçar um wake word em outro idioma (chinês "ni hao xiao zhi" ou inglês "hi
esp") que não faz sentido para uma criança brasileira de 8 anos.

Configuração: `WAKE_WORD_DISABLED` no Kconfig (desativa WakeNet+AFE
interino). O toque no display (FT6336U) dispara o mesmo fluxo que a wake
word dispararia — abre a captura de áudio e envia à Nuvem. Quando o modelo
"Felipe" estiver treinado e validado em hardware, troca-se para
`USE_AFE_WAKE_WORD` (ou `USE_TFLITE_WAKE_WORD` via ponte) sem mudar o resto
do pipeline.

### 2. AEC — tentar device-AEC no CoreS3

Adicionar `BOARD_TYPE_M5STACK_CORE_S3` à lista de boards do `USE_DEVICE_AEC`
no `Kconfig.projbuild`. O CoreS3 tem mics frontais + speaker traseiro
(isolamento acústico físico favorável) e o ES7210 é multicanal (2 mics),
o que permite ao AFE do esp-sr usar o canal de referência do speaker para
cancelar eco. **Validar empiricamente** no primeiro build com hardware em
mãos — se o device-AEC não funcionar bem, fallback para `USE_SERVER_AEC`
(xiaozhi-esp32-server processa o eco, marcado "Unstable" no Kconfig mas
funcional) ou para VAD que silencia o speaker durante captura.

### 3. UI / personalidade — lvgl_display + camada de pet

Usar a camada LVGL já integrada (`main/display/lvgl_display/`, LVGL 9.5 +
`esp_lvgl_port`) como infra de rendering e **acrescentar uma camada de pet
customizada** com:

- **Sprites de olhos/boca** via `lv_image` (RGB565, blitted com DMA via
  `esp_image_effects`). Sprites originais a desenhar (não reusar arte de
  terceiros — ver research `tamagotchi-pet-engine-ui.md` gap de licença).
- **Barras de stats** (fome/sono/afeto) via `lv_bar`, inspiradas no design
  do `moonbench/catode32` (18 stats, decay tiers — ver ADR futura "PET vivo").
- **Animações de humor** via `lv_anim` (transições entre expressões:
  feliz, com fome, dormindo, tonto, falando).
- **Partial redraw por invalidação** — `LV_DISPLAY_RENDER_MODE_PARTIAL`,
  redesenha só regiões sujas (olhos/boca), ideal para animação fluida em
  320×240 semConsumer toda a SPI.

O estilo de display do xiaozhi (`USE_DEFAULT_MESSAGE_STYLE`, chat) é
**substituído** por esta camada de pet. O texto transcrito da conversa
(subtítulo do que o pet fala) pode aparecer como `lv_label` temporário na
base da tela, sobreposto à face do pet.

A personalidade conversacional (persona, vocabulário para 8 anos,
child-safe) é **configuração do servidor/Nuvem** (prompt de sistema), não
do firmware — o firmware só renderiza a UI e transporta áudio. A ponte
estado→prompt fica no Core (ADR-018).

### 4. i18n — criar locale pt-BR

Criar `main/assets/locales/pt-BR/` copiando a estrutura de `pt-PT/` e
ajustando:

- **`language.json`** — ~50 strings de UI, traduzir de pt-PT para pt-BR
  (ex.: "A escutar..." → "Ouvindo...", "A falar..." → "Falando...",
  "Bateria fraca" → "Bateria fraca", "A inicializar..." → "Iniciando...").
  Esforço baixo — é só texto.
- **Áudios `.ogg`** (~15 arquivos: `welcome.ogg`, `activation.ogg`,
  `wificonfig.ogg`, `upgrade.ogg`, `err_pin.ogg`, `err_reg.ogg`, dígitos
  0-9) — regravar em pt-BR com voz de pet (TTS pt-BR infantil do provedor
  configurado na Nuvem, ou voz sintética local para os prompts de sistema).
- **`LANGUAGE_PT_BR`** — adicionar opção no `Kconfig.projbuild` (choice
  "Default Language") e rodar `scripts/gen_lang.py --language pt-BR`.

Selecionar `LANGUAGE_PT_BR` no build. A voz conversacional pt-BR vem do
provedor de TTS configurado no servidor (lado Nuvem), não do firmware — os
`.ogg` são apenas prompts de sistema offline (bem-vinda, configuração
WiFi, erro de ativação).

### 5. OTA — substituir `ota.cc` por wrapper esp32FOTA

Conforme ADR-020, a classe `Ota` nativa do xiaozhi (`main/ota.cc`/`ota.h`)
é **substituída** por um wrapper próprio sobre `chrisjoyce911/esp32FOTA`
(v0.3.0, Unlicense). O wrapper:

- Chama `esp32FOTA.execHTTPcheck()` (consulta manifest no GitHub Releases,
  compara semver) e `esp32FOTA.execOTA()` (baixa + verifica assinatura
  RSA-4096 + instala app + LittleFS).
- Remove o fluxo de ativação/MQTT/websocket-config do `Ota` nativo — não
  usamos o servidor xiaozhi para nada além do firmware de referência.
- Preserva `MarkCurrentVersionValid()` (commit pós-self-test).
- Adiciona `esp32FOTA` como dependência em `idf_component.yml`.

### 6. WiFi provisioning — captive portal nativo do xiaozhi

Usar `USE_HOTSPOT_WIFI_PROVISIONING` (captive portal nativo do xiaozhi, já
embarcado) em vez de `tzapu/WiFiManager` (research `tamagotchi-operacao.md`
Need 2). O xiaozhi já tem AP temporário + DNS hijack + página de seleção de
SSID. Customizar a página HTML com a identidade do Robô Felipe (nome do AP
`Robo-Felipe-XXXX`, ícones amigáveis). O adulto conecta ao AP no celular e
configura o WiFi pelo navegador — sem app. Dispensa uma dependência externa
(WiFiManager é Arduino-only; o xiaozhi é ESP-IDF puro).

Botão "re-provisionar" no display touch (segurar 5s) reinicia o modo
configPortal — customização leve no firmware.

### 7. Build system — ESP-IDF (não Arduino)

O xiaozhi é ESP-IDF puro (CMakeLists.txt, `idf_component.yml`, Kconfig).
**Abandonar a ideia de Arduino IDE** — `.vscode/arduino.json` (que ainda
aponta WROOM/PSRAM=disabled, conforme AGENTS.md) é substituído por
configuração ESP-IDF (`idf.py set-target esp32s3`, `idf.py menuconfig`,
board `m5stack/core-s3`). O `arduino-cli` não está no PATH e não é necessário.

### 8. Esquema de partições — v2/16m.csv (existente)

Usar `partitions/v2/16m.csv` do próprio xiaozhi, já compatível com o CoreS3
(16 MB flash):

| Partição | Tipo | Offset | Tamanho | Uso |
|:--|:--|:--|:--|:--|
| `nvs` | data/nvs | 0x9000 | 16 KB | estado do pet (NVS, ADR-018 fallback) |
| `otadata` | data/ota | 0xd000 | 8 KB | seleção de partição OTA ativa |
| `phy_init` | data/phy | 0xf000 | 4 KB | calibração RF |
| `ota_0` | app/ota_0 | 0x20000 | ~4 MB | imagem A (app) |
| `ota_1` | app/ota_1 | — | ~4 MB | imagem B (app, rollback) |
| `assets` | data/spiffs | 0x800000 | 8 MB | sprites, áudios locale, emojis |

Isso satisfaz o ADR-020: A/B de app (rollback), partição de dados
(assets OTA-atualizável via esp32FOTA filesystem), NVS (estado do pet). A
partição `assets` (SPIFFS 8MB) é o "LittleFS" do ADR-020 — o esp32FOTA
suporta SPIFFS/LittleFS/FAT; o xiaozhi usa SPIFFS, que passa a ser atualizada
via OTA junto com o app (sprites e áudios pt-BR atualizáveis sem cabo).

### 9. Self-test pós-OTA (checklist)

No 1º boot após OTA (`ESP_OTA_IMG_PENDING_VERIFY`), o firmware roda um
self-test de ~30s antes de `esp_ota_mark_app_valid_cancel_rollback()`:

1. **Display** — inicializa ILI9342C, renderiza um frame de teste (face do
   pet ou logo). Falha = tela em branco.
2. **Touch** — FT6336U responde a toque (lê pelo menos 1 evento). Falha =
   sem input.
3. **I²C bus** — escaneia endereços conhecidos (AW88298@0x36, ES7210,
   AXP2101@0x34, BM8563@0x51, AW9523B@0x58, LTR-553@0x23). Falha = I²C
   morto.
4. **Áudio captura** — ES7210 lê N amostras de mic (não-zero, não-saturado).
5. **Áudio saída** — AW88298 toca um beep curto (audível). Falha = mudo.
6. **WiFi** — conecta ao SSID salvo em NVS (se houver). Falha = entra em
   captive portal (não é fatal).
7. **PSRAM** — `esp_psram_get_size()` retorna 8MB. Falha = sem PSRAM
   (fatal para TLS, ADR-016).
8. **Câmera** (se habilitada) — GC0308 captura 1 frame. Falha = câmera
   desligada (não fatal, visão é secundária).
9. **NVS** — lê/escreve uma chave de teste. Falha = estado do pet perdido.

Se qualquer passo **fatal** (display, I²C, PSRAM, NVS) falha → não commitar
→ bootloader reverte para a imagem anterior após timeout/watchdog. Passos
**não-fatais** (WiFi, câmera) logam warning mas permitem commit.

### 10. Agendamento da checagem OTA

A checagem de OTA (chamar `esp32FOTA.execHTTPcheck()`) ocorre em dois
momentos:

1. **Na conexão WiFi** — após obter IP (evento `IP_EVENT_STA_GOT_IP`),
   esperar ~10s (estabilizar) e checar OTA. Se há atualização, iniciar
   download (o Sobrinho vê "Atualizando..." no display).
2. **No wake do RTC** — a cada despertar horário (deep-sleep cycle do "PET
   vivo"), após o `advanceStats` (ADR futura), checar OTA. Se há update,
   aplicar antes de interagir.

Não checar OTA durante conversa de voz (concorrência de TLS/banda com o
streaming de áudio). Não checar em modo offline (sem WiFi, o check falha
silenciosamente).

## Alternatives Considered

### Escrever firmware do zero (ESP-IDF puro, sem xiaozhi)

- **Prós:** controle total; sem divergência de upstream; só o que o pet
  precisa.
- **Contras:** reconstruir do zero KWS (esp-sr integration), streaming
  Opus, WSS/TLS, protocolo com a Nuvem, AFE/AEC, display LVGL, touch,
  câmera, captive portal, OTA — tudo o que o xiaozhi já entrega testado em
  171 variantes. Esforço de meses para igualar o que o xiaozhi já tem.
- **Rejeitada:** o xiaozhi é MIT e tem suporte first-class ao CoreS3. Não
  há motivo para reinventar o pipeline de voz conversacional E2E.

### ESPHome (framework YAML, componente `micro_wake_word`)

- **Prós:** wake word microWakeWord integrado nativo; YAML declarativo;
  comunidade enorme.
- **Contras:** ESPHome é **outro framework** — abandonaria o xiaozhi e seu
  pipeline de voz/TLS/display/câmera/protocolo prontos. O `micro_wake_word`
  do ESPHome roda modelos .tflite, mas não tem o protocolo xiaozhi (WSS +
  Opus + ASR/LLM/TTS orquestrados). Seria reconstruir a orquestração de voz.
- **Rejeitada:** o custo de abandonar o xiaozhi supera o ganho do
  `micro_wake_word` nativo. A ponte `xiaozhi-tflite` traz o microWakeWord
  para dentro do xiaozhi sem trocar de framework (ver Gap #1).

### Fork do xiaozhi sem customizações (usar como-is, pt-PT)

- **Prós:** zero esforço de customização; merges de upstream diretos.
- **Contras:** wake word em chinês ("ni hao xiao zhi"); UI de chatbot, não
  de pet; strings em pt-PT (português europeu); servidor default aponta
  para `xiaozhi.me` (China); sem identidade do Robô Felipe.
- **Rejeitada:** o firmware precisa ser do Robô Felipe, não de um assistente
  genérico chinês. As customizações (pt-BR, UI de pet, OTA wrapper, wake
  word) são o coração do produto.

## Consequences

### Positivas

- **Firmware-base maduro e testado** — 29k★, 171 variantes, pipeline de voz
  completo, MIT. O esforço de firmware do Tamagotchi encolhe para
  customizações, não construção do zero.
- **Zero portabilidade de hardware** — board `core-s3` do xiaozhi usa os
  mesmos GPIOs/codecs do CoreS3 (ADR-019).
- **LVGL já integrado** — não é uma dependência a adicionar; a camada de
  pet é construída sobre infra de rendering já configurada (SPI/DMA/PSRAM/
  partial-redraw).
- **WiFi provisioning nativo** — captive portal do xiaozhi dispensa
  WiFiManager externo (uma dependência a menos).
- **i18n pt-BR de baixo esforço** — ~50 strings + ~15 áudios; o gerador
  (`gen_lang.py`) e o fallback en-US já existem.
- **OTA A/B + assets OTA-atualizável** — partições v2/16m.csv já têm A/B
  de app e SPIFFS 8MB; sprites e áudios pt-BR atualizáveis sem cabo
  (consistente com ADR-020).
- **Câmera e visão já prontos** — GC0308 + CoreS3 + `Explain()` MCP já
  no xiaozhi (ADR-017); visão em nuvem via VLLM configurado no servidor.
- **Push-to-talk honesto** — não força wake word em outro idioma; o
  Sobrinho toca e fala. "Felipe" chega quando o modelo estiver validado.

### Negativas

- **Divergência de upstream do xiaozhi** — customizações (pt-BR, UI de
  pet, OTA wrapper, AEC list, wake word desativado) criam um fork a
  manter. Merges de upstream (commits diários do xiaozhi) exigem resolução
  de conflitos. Mitigação: manter customizações isoladas em arquivos
  próprios onde possível; documentar o diff no fork.
- **PSRAM em modo QUAD, não OCTAL** — o `config.json` do CoreS3 appenda
  `CONFIG_SPIRAM_MODE_QUAD=y`, sobrescrevendo o default OCTAL do
  `sdkconfig.defaults.esp32s3`. Hardware é octal; firmware configura quad.
  Isso **reduz a banda de PSRAM** (quad: 4 pinos; octal: 8 pinos). Pode
  afetar performance de TLS + áudio + visão simultâneos. **Validar
  empiricamente** se trocar para OCTAL é estável no CoreS3 — se sim,
  remover o append QUAD do `config.json`.
- **Device-AEC não validado no CoreS3** — CoreS3 não está na lista original
  de boards com AEC. Adicionar à lista é uma hipótese, não uma garantia.
  Se o AFE não cancelar bem o eco (speaker traseiro + mics frontais), o
  fallback é server-AEC (instável) ou VAD agressivo (degrada conversa).
- **Wake word "Felipe" ainda é Gap #1** — o MVP não tem wake word por voz.
  O Sobrinho precisa tocar para falar. Isso muda a UX (não é "falar
  naturalmente com o pet"), mas é honesto e funcional. O gap só fecha com
  o modelo treinado (microWakeWord + Piper pt-BR + ponte xiaozhi-tflite
  validada em HW).
- **i18n pt-BR exige regravar áudios** — os `.ogg` de sistema (welcome,
  wificonfig, upgrade) precisam de voz pt-BR. Se gerados por TTS do
  provedor da Nuvem, dependem de conectividade para produzir (one-off); se
  gravados localmente, exigem ferramenta de áudio.
- **Build system ESP-IDF, não Arduino** — o `.vscode/arduino.json` atual é
  inútil para este firmware. Configurar toolchain ESP-IDF (idf.py, não
  arduino-cli). O `arduino-cli` não está no PATH (AGENTS.md) e não será
  necessário.
- **Toolchain ainda não configurada** — ESP-IDF v5.5.2+ + board
  `m5stack/core-s3` + Python 3.9+ + `idf_component.yml` deps. Placeholder
  em `hardware/cores3/`; setup pendente.

### Notas

- **Supersede parcial do research `tamagotchi-firmware-voz.md`** apenas no
  trecho que recomendava `USE_AFE_WAKE_WORD` com wake word interim
  (`wn9_hiesp`/`wn9_hijason_tts2`) — esta ADR decide push-to-talk por
  toque em vez de wake word em outro idioma. O restante do research
  (ADOPT xiaozhi + esp-sr + servidor) é confirmado.
- **Supersede parcial do research `tamagotchi-pet-engine-ui.md`** no
  trecho que recomendava TFT_eSPI + TamaFi como base de UI — esta ADR
  decide LVGL (já integrado ao xiaozhi) + camada de pet customizada. O
  design de stats/decay do catode32 permanece como referência para a ADR
  "PET vivo" futura.
- **Supersede parcial do research `tamagotchi-operacao.md`** Need 2
  (WiFiManager) — esta ADR decide usar o captive portal nativo do xiaozhi
  (`USE_HOTSPOT_WIFI_PROVISIONING`), dispensando WiFiManager externo.
- **OTA wrapper**: detalhes de assinatura, manifest e hosting no
  [ADR-020](020-tamagotchi-ota-pull-com-esp32fota.md). Esta ADR só
  registra a substituição de `ota.cc` e o self-test.
- **Core (TypeScript)**: o firmware não decide comportamento — apenas
  detecta Triggers, envia Batches e executa Planos de Ações (ADR-018). A
  ponte firmware↔Core (como o Core se conecta ao protocolo xiaozhi) é
  detalhada quando o CoreS3 chegar em mãos.
- **"PET vivo"** (estado/decay/estágios, NVS vs cloud, `advanceStats` no
  wake do RTC) fica como tópico aberto — ADR futura.
- **Próximas ADRs pendentes**: Nuvem (provedores ASR/LLM/TTS pt-BR +
  hosting vision.url), "PET vivo" (estado/stats/decay/persistência).
- **Referências**: research [`../research/tamagotchi-firmware-voz.md`](../research/tamagotchi-firmware-voz.md),
  [`../research/tamagotchi-pet-engine-ui.md`](../research/tamagotchi-pet-engine-ui.md),
  [`../research/tamagotchi-operacao.md`](../research/tamagotchi-operacao.md),
  [`../research/tamagotchi-visao-cam.md`](../research/tamagotchi-visao-cam.md);
  código do xiaozhi-esp32 verificado em 2026-08-31 (Kconfig.projbuild,
  config.h, config.json, sdkconfig.defaults.esp32s3, partitions/v2/16m.csv,
  idf_component.yml, assets/locales/pt-PT/language.json, scripts/gen_lang.py).
