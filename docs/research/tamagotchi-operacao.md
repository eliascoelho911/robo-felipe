# Operação do Tamagotchi: OTA, WiFi Provisioning e Wake/Power

> **Ticket de pesquisa** que cobre as três necessidades operacionais da
> variante Tamagotchi no M5Stack CoreS3 (ESP32-S3 + PSRAM + AXP2101 PMU
> + BM8563 RTC + LiPo 500 mAh + FT6336U touch + BMI270 IMU + LTR-553
> proximidade). Autocontido, sem relay (TLS termina no dispositivo —
> [ADR-016](../decisions/016-tamagotchi-processa-voz-sem-relay-de-smartphone.md)).
> Firmware C++ (Arduino ou ESP-IDF). Usuário: 8 anos.

| | |
|---|---|
| **Data da pesquisa** | 2026-08-26 |
| **Status** | Concluída — recomendações claras (esp32FOTA + WiFiManager + M5Unified/SensorLib/BMI270) |
| **Alimenta** | futuras ADRs de OTA, provisioning e power do Tamagotchi |
| **Confiança** | Alta — métricas de repositório verificadas via GitHub API em 2026-08-26 |

## Metodologia e fontes

Busca via `gh search repos` / `gh search code` + `webfetch` de READMEs.
Métricas verificadas em 2026-08-26.

---

## Need 1 — OTA "pull" para ESP32-S3

O dispositivo deve **puxar** suas próprias atualizações de firmware
sobre HTTPS (TLS termina no dispositivo, sem relay pushing). O projeto
já menciona `esp32FOTA` no ADR-016.

### Tabela comparativa

| Lib / Solução | ★ | Último commit | Licença | S3+PSRAM | Assinatura | App + Filesystem | Compl. | Notas |
|---|---|---|---|---|---|---|---|---|
| **chrisjoyce911/esp32FOTA** | 417 | 2026-08-08 (v0.3.0 Nov/2025) | Unlicense | ✅ (Arduino + ESP-IDF) | RSA (default sig 512 B = RSA-4096) + SHA-256, verificação **in-app** via mbedtls, independente de Secure Boot | ✅ SPIFFS/LittleFS/FAT (`spiffs`/`littlefs`/`fatfs`) | **8** | Manifest JSON + semver, gzip/zlib (não combina com assinatura), HTTPS com CA bundle (Arduino 3.x) ou CA custom, callbacks de progresso. Sem anti-rollback por efuse. |
| **espressif/esp-idf · `esp_https_ota`** | — (IDF master, ativo) | — | Apache-2.0 | ✅ | Delegada ao **Secure Boot v2** (bootloader) + `secure_version` efuse (anti-rollback); `decrypt_cb` p/ OTA pré-criptografada | ❌ sem helper de FS — você escreve a partição | **6** | Download parcial, staging/final partition, `esp_app_desc` p/ checagem. Baixo nível: você monta manifest/semver. |
| **78/xiaozhi-esp32 · `Ota` (nativo)** | 29,1k | 2026-08-21 | MIT | ✅ (ESP-IDF) | `esp_ota_ops` + ativação por desafio/código + **HMAC efuse** + serial de efuse (anti-rollback server-side); sem verif. RSA in-app do bin | ❌ app only | **7** | "Pull" puxando `firmware_url` do backend xiaozhi via `network->CreateHttp()`. TLS depende da impl. de Http do board. Fortemente acoplado ao backend/ativação xiaozhi. |

### Recomendação — **Adotar `esp32FOTA` (estender levemente)**
- É o mais completo para um "pull" autônomo sobre HTTPS: manifest JSON
  + semver + assinatura RSA/SHA-256 verificada no próprio firmware
  (independente de Secure Boot, o que casa com ADR-016 — TLS/segurança
  terminam no dispositivo), e atualiza **app + filesystem** na mesma
  rodada. Licença Unlicense (permissiva).
- **Estender**: combinar com `esp_ota_set_anti-rollback`/efuse secure
  version para defesa em profundidade, e considerar RSA-2048/3072
  (baixar `signature_len`) para reduzir custo de mbedtls no PSRAM. Se
  o firmware for derivado de **xiaozhi-esp32**, há uma decisão a tomar:
  **ou** se reusa o `Ota` nativo do xiaozhi (mas fica amarrado ao
  backend de ativação deles), **ou** o substitui por `esp32FOTA`
  apontando para um manifest/CDN próprio — recomendado para um produto
  consumidor (Sobrinho) que não deve depender do servidor MCP xiaozhi.
- `esp_https_ota` é a base oficial para integrar caso queira Criptografia
  de imagem (decrypt_cb) e Secure Boot v2 end-to-end — mas exige mais
  código de glue (manifest, semver, fs).

---

## Need 2 — WiFi provisioning SEM app de smartphone (captive portal ou BLE)

O pet deve obter credenciais WiFi sem app. O CoreS3 tem display 320×240
touch + USB-C. **Digitão de senha no display por uma criança de 8 anos
é impraticável** — o caminho natural é portal cativo no navegador do
celular do adulto.

### Tabela comparativa

| Solução | ★ | Último commit | Licença | S3 | UX (display touch / navegador) | Compl. | Notas |
|---|---|---|---|---|---|---|---|
| **tzapu/WiFiManager** | 7.250 | 2026-02-25 (release v2.0.17 Mar/2024) | MIT | ✅ (badge ESP32-S3) | **Captive portal puro num navegador de telefone** (sem app). HTML/CSS/JS injetáveis → UI amigável. | **8** | O padrão de facto. AutoConnect + fallback + on-demand + timeout. Apenas framework Arduino (funciona sob Arduino-ESP32, não ESP-IDF puro). |
| **ESP-IDF Unified Provisioning** (`idf-extra-components/network_provisioning`) | — (IDF, ativo) | — | Apache-2.0 | ✅ | SoftAP+HTTP **ou** BLE; protocolo **protobuf via protocomm** → exige o app da Espressif ou `esp_prov` CLI. **Não** é portal browser puro. Security 0/1 (Curve25519+AES-CTR) / 2 (SRP6a+AES-GCM). | **7** | Mais robusto/seguro, mas viola "sem app" a menos que construa um front-end web protobuf (trabalho significativo). |
| **espressif/esp-rainmaker** | 630 | 2026-08-18 | Apache-2.0 | ✅ | Requer **Rainmaker Cloud + claiming + app Rainmaker** | **4** | Violaria ADR-016 (self-contained, sem nuvem proprietária/app). Não recomendado. |
| **alanswx/ESPAsyncWiFiManager** (mathieucarbou fork **arquivado**) | 240 | 2026-07-27 | LGPL-3.0 (ESPAsyncWebServer) | ✅ | Captive portal async, HTML custom | **6** | mathieucarbou/ESPAsyncWebServer está **archived** (Jan/2025). Forks vivem mas fragmentados. LGPL menos conveniente que MIT. |
| **khoih-prog/ESP_WiFiManager** | 405 | 2022-12-21 | MIT | ✅ (S2/S3/C3) | Portal web, many params | **6** | Feature-rich mas **stale desde 2022**. |
| **ESP-IDF captive portal DIY** (`tedbyron/...`, `omelaweng/...`) | 0 | 2024 | — | ✅ | `esp_http_server` + DNS hijack | **3** | Só sketches-exemplo 0★. Construir do zero. |

### Recomendação — **Adotar `tzapu/WiFiManager` (estender UI)**
- Atende exatamente o requisito: **portal cativo abrível no navegador
  do celular do adulto**, sem app dedicado, com badge ESP32-S3 e licença
  MIT. Injeção de HTML/CSS permite uma tela grande, poucos botões e
  teclado virtual simples — o adulto conecta ao AP `Tamagotchi-XXXX` e
  toca em "Escolher WiFi".
- **Estender**: customizar a página com a identidade do Robô Felipe,
  listar SSIDs com ícones, e expor um botão "re-provisionar" no display
  touch do CoreS3 (segurar o dedo 5 s reinicia o WiFiManager
  `startConfigPortal()`).
- **Se o firmware for ESP-IDF puro** (xiaozhi), a melhor rota sem app é
  construir um captive portal leve com `esp_http_server` (há só
  exemplos 0★ — conta como custom) **ou** aceitar o custo de um
  front-end web protobuf sobre `network_provisioning`. A Unified
  Provisioning só vale a pena se a segurança SRP6a (Security 2) for
  mandatória; para um brinquedo de bolso, o WiFiManager + HTTPS no OTA
  é suficiente.

---

## Need 3 — Cadeia de wake / power management (CoreS3, ~35 dias em 500 mAh)

O pet deve ficar "vivo" numa LiPo 500 mAh por ~35 dias, acordando
hourariamente via RTC, por toque, por movimento e por proximidade.
Ver
[`hardware/cores3/CoreS3-capacidades.md`](../../hardware/cores3/CoreS3-capacidades.md)
§6 p/ a estimativa de bateria (I_deep ≈ 49 µA, ~35 dias com wake
horário de 10s de voz).

### Tabela comparativa

| Solução | ★ | Último commit | Licença | S3 + CoreS3 | Fontes de wake suportadas | Compl. | Notas |
|---|---|---|---|---|---|---|---|
| **m5stack/M5Unified** | 687 | 2026-08-26 | MIT | ✅ (CoreS3 listado) | RTC (BM8563) `timerSleep(s)`/`setAlarmIRQ`; `deepSleep(us, touch_wakeup=true)`; `lightSleep`; `powerOff`; AXP2101 wrapped; `setINTPinActiveLogic` p/ IMU | **6** | Primitivas existem, **mas o próprio README lista "Power / Battery lifespan / RTC wakeup" como exemplos ainda por contribuir** — não há receita testada. Touch wake depende do roteamento do INT do FT6336U (via IO expander AW9523 no CoreS3). |
| **boschsensortec/BMI270_SensorAPI** | 152 | 2025-06-18 | BSD-3 | ✅ (driver genérico) | **Any-motion, No-motion, Significant-motion, Wrist-wear wake** → INT1/INT2 p/ ext0/ext1 | **7** | Driver de referência Bosch com exemplos de any-motion. No CoreS3 o INT do BMI270 passa pelo IO expander/PMIC (comentário no M5Unified: "PM1 GPIO4 is the BMI270 INT1 (motion wakeup)") — roteamento precisa ser confirmado p/ deep sleep. |
| **lewisxhe/SensorLib** | 240 | 2026-07-30 | MIT | ✅ Arduino/ESP-IDF | **LTR-553 (ALS+Prox c/ INT)**, AXP2101 PMIC, PCF8563/BM8563 RTC, FT6X36 touch, BMM150 | **7** | Mais completo p/ os sensores **não-IMU** do CoreS3 (prox/RTC/PMIC/touch). **Sem driver BMI270** (tem BHI260AP/BHI360/QMI8658). Boa base p/ wake por proximidade. |
| **aselectroworks/Arduino-FT6336U** | 52 | 2026-05-23 | MIT | ✅ | Leitura de toque apenas | **4** | **Não expõe o modo monitor/low-power (~220 µA) wake-on-touch** do FT6336U. Precisa configurar registradores manualmente. |
| **ESP-IDF `esp_sleep` (ext0/ext1/RTC timer/ULP)** | — (IDF) | — | Apache-2.0 | ✅ | ext0 (GPIO único), ext1 (mascara), RTC timer, ULP/coproc, touch (ESP32 clássico, **não S3** — no S3 touch-wake é via INT p/ GPIO) | **9** | A camada primitiva; tudo o resto se apoia nela. |

### Recomendação — **Adotar `M5Unified` como núcleo + `SensorLib` (LTR-553) + `BMI270_SensorAPI` (any-motion); BUILD do glue da cadeia de wake**
- `M5Unified` já entrega: `timerSleep(3600)` (wake horário via BM8563),
  `deepSleep(..., touch_wakeup=true)`, `powerOff`, e wrappers do
  AXP2101 — use como núcleo. Para **proximidade** (LTR-553) e
  **RTC/PMIC** alternativos, `SensorLib` é a base mais completa (MIT,
  ESP-IDF+Arduino, ativa). Para **any-motion**, use `BMI270_SensorAPI`
  (Bosch) — o M5Unified só lê accel/gyro, não configura o interruptor
  de any-motion/wrist-wake.
- **Construir (build)**: a orquestração da cadeia completa (RTC horário
  + touch + motion + proximity → `esp_sleep_enable_ext1_wakeup` no pino
  INT comum) não existe pronta. E **power-down das rails do AXP2101** em
  deep sleep (cortar DCDCs/ALDOs não usados, deixar só RTC + I²C dos
  sensores de wake) para atingir o alvo de ~35 dias/500 mAh também é
  custom.

---

## Scorecard final

| Necessidade | Adoção principal | Veredito |
|---|---|---|
| 1. OTA pull | `chrisjoyce911/esp32FOTA` | **Adotar + estender** (anti-rollback; decisão xiaozhi) |
| 2. WiFi prov sem app | `tzapu/WiFiManager` | **Adotar + estender UI** (se ESP-IDF puro: build captive portal) |
| 3. Wake/power | `M5Unified` + `SensorLib` + `BMI270_SensorAPI` | **Adotar primitivas + BUILD do glue da cadeia e do power-down** |

---

## Lacunas que exigem código custom (resumo)

1. **OTA — anti-rollback:** `esp32FOTA` não controla
   `esp_ota_set_anti-rollback`/efuse. Combinar manualmente com
   `esp_app_desc.secure_version`.
2. **OTA — filesystem + assinatura + compressão não coexistem** no
   esp32FOTA (gzip/zlib desativa assinatura). Escolher política:
   assinar app-only, e atualizar LittleFS sem compressão+assinatura, ou
   hash SHA-256 do fs à parte.
3. **OTA — se derivado de xiaozhi:** decidir entre o `Ota` nativo
   (amarrado ao backend de ativação xiaozhi) **ou** troca por esp32FOTA
   apontando a CDN próprio. Decisão arquitetural a registrar em ADR.
4. **WiFi prov — UI no display touch:** nenhuma lib oferece fluxo de
   provisioning **pelo próprio display 320×240**. Recomendação
   confirmada: portal no **navegador do celular do adulto**, com botão
   "re-provisionar" disparado pelo display do robô (custom).
5. **Wake — BMI270 any-motion → GPIO de wake:** configurar
   any-motion/wrist-wake via Bosch `BMI270_SensorAPI`, mapear INT1 para
   o pino que chega no ESP32 (via IO expander/PMIC do CoreS3 — confirmar
   roteamento), e registrar como ext0/ext1 wake. M5Unified não faz
   isso.
6. **Wake — FT6336U wake-on-touch (~220 µA):** nenhuma lib (M5Unified
   Touch_Class, Arduino-FT6336U) habilita o modo monitor/low-power do
   controlador. Configuração de registradores custom + INT → GPIO wake.
7. **Wake — LTR-553 proximity-triggered wake:** driver existe em
   SensorLib, mas a cadeia "configurar limiar de PS + INT → GPIO → ext0
   wake" é custom.
8. **Power — deep-sleep current do CoreS3:** sem receita testada.
   Necessário desligar via AXP2101 as rails desnecessárias (DCDC1/2,
   ALDOs de display/áudio/câmera), manter só BM8563 + I²C dos sensores
   de wake + pull-ups adequados. Medir e iterar (alvo implícito: média
   < ~600 µA p/ ~35 dias em 500 mAh).
9. **Power — `timerSleep` reinicia do zero (cold boot a cada hora):**
   projetar o estado persistente do "pet vivo" em NVS/RTC memory (budget
   de "fome/sono" do Tamagotchi) para sobreviver ao ciclo de power-off +
   RTC boot. Ver [research de pet-engine](tamagotchi-pet-engine-ui.md).

---

## Referências

| Componente | URL | Licença | Verificado em |
|:--|:--|:--|:--|
| `chrisjoyce911/esp32FOTA` | https://github.com/chrisjoyce911/esp32FOTA | Unlicense | 2026-08-26 |
| `espressif/esp-idf` (`esp_https_ota`) | https://github.com/espressif/esp-idf | Apache-2.0 | 2026-08-26 |
| `78/xiaozhi-esp32` (Ota nativo) | https://github.com/78/xiaozhi-esp32 | MIT | 2026-08-26 |
| `tzapu/WiFiManager` | https://github.com/tzapu/WiFiManager | MIT | 2026-08-26 |
| ESP-IDF `network_provisioning` | https://github.com/espressif/idf-extra-components | Apache-2.0 | 2026-08-26 |
| `espressif/esp-rainmaker` | https://github.com/espressif/esp-rainmaker | Apache-2.0 | 2026-08-26 |
| `m5stack/M5Unified` | https://github.com/m5stack/M5Unified | MIT | 2026-08-26 |
| `boschsensortec/BMI270_SensorAPI` | https://github.com/boschsensortec/BMI270_SensorAPI | BSD-3 | 2026-08-26 |
| `lewisxhe/SensorLib` | https://github.com/lewisxhe/SensorLib | MIT | 2026-08-26 |
| `aselectroworks/Arduino-FT6336U` | https://github.com/aselectroworks/Arduino-FT6336U | MIT | 2026-08-26 |
