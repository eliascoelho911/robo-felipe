# ADR-014: Migrar toolchain de Arduino-ESP32 para ESP-IDF puro

## Status
Accepted

## Date
2026-08-09

## Context

ADR-001 escolheu "C/C++ com Arduino-ESP32 core e FreeRTOS" e listou
como positiva "Toolchain simples: Arduino IDE (ou PlatformIO)". Na
época, o firmware era o do kit ACEBOTT (biblioteca `ACB_Biped_Robot`,
sketches `.ino`, upload pela IDE) e não havia necessidade de componentes
ESP-IDF.

O pivot para quadrúpede (ADR-008) e a decisão de usar o componente
`espfriends/servo_dog_ctrl` (ADR-013) mudaram o cenário:

- O `servo_dog_ctrl` é um **componente ESP-IDF** (`idf_component.yml`,
  `espressif/servo` LEDC driver, ESP-IDF ≥5.0). Não é uma biblioteca
  Arduino.
- O ESP Component Registry (de onde vem `servo_dog_ctrl`,
  `espressif/servo`, `espressif/mdns`) integra-se nativamente ao ESP-IDF
  via `idf_component.yml` — não ao Arduino IDE.
- O `menuconfig` / `sdkconfig.defaults` do ESP-IDF é necessário para
  configurar partições OTA (A/B — ADR-007), `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE`,
  e o Kconfig do `servo_dog_ctrl` (ângulos neutros, WebUI on/off).
- ADR-007 já especifica `sdkconfig.defaults` com `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y`
  e esquema "Two OTA" — isso é configuração ESP-IDF, não Arduino IDE.

A **linguagem** (C/C++) e o **RTOS** (FreeRTOS, nativo do ESP-IDF)
permanecem os mesmos do ADR-001. Apenas a **toolchain** muda: Arduino
IDE → ESP-IDF.

## Decision

**Migrar a toolchain do firmware para ESP-IDF puro (≥5.0), abandonando
o Arduino IDE como toolchain primário.**

- Estrutura de projeto ESP-IDF: `CMakeLists.txt`, `main/`, `components/`,
  `idf_component.yml`, `sdkconfig.defaults`.
- Componentes do registry: `espfriends/servo_dog_ctrl`, `espressif/servo`,
  e futuros (TFLM, WebSocket client) via `idf_component.yml`.
- `menuconfig` para configuração de partições, Kconfig do servo_dog_ctrl,
  e parâmetros de build.
- **Código Arduino existente** (bibliotecas, patterns) pode ser portado:
  o arduino-esp32 core pode ser usado **dentro** do ESP-IDF como
  componente (`idf_component.yml: espressif/arduino-esp32`) se
  bibliotecas Arduino específicas forem necessárias. Mas o primário é
  ESP-IDF nativo.
- Build via `idf.py build / flash / monitor` (ou PlatformIO com backend
  ESP-IDF).

### Esta ADR refina ADR-001, não o substitui

- ADR-001 decidiu **C/C++ sobre MicroPython/Rust** — permanace válido.
- ADR-001 decidiu **FreeRTOS** — permanece válido (nativo do ESP-IDF).
- ADR-001 citou "Arduino IDE" como toolchain — **refinado** para ESP-IDF.
- A "Toolchain simples" que ADR-001 listou como positiva era verdadeira
  para o bípede; para o cão com componentes do registry, ESP-IDF é o
  caminho mais simples.

## Alternatives Considered

### Hybrid: arduino-esp32 core + IDF components (PlatformIO)
- Prós: mantém estilo Arduino, usa componentes IDF via PlatformIO.
- Contras: setup híbrido tem fricção; nem todo código Arduino é
  compatível com componentes IDF; debugging duplo.
- **Rejeitada:** o híbrido é o pior dos dois mundos — complexidade de
  IDF sem os benefícios de clareza.

### Vendorizar servo_dog_ctrl e adaptar para ESP32Servo (Arduino puro)
- Prós: fica 100% Arduino IDE, sem IDF dependency.
- Contras: reescrever `iot_servo_write_angle()` → `ESP32Servo.write()`
  (~50 linhas de glue); perde atualizações upstream do componente;
  `ESP32Servo` é menos robusto que `espressif/servo` (LEDC-based);
  não resolve a necessidade de `sdkconfig` para OTA (ADR-007).
- **Rejeitada:** cola temporária que adorta o inevitável.

## Consequences

### Positivas

- Acesso ao ESP Component Registry — `servo_dog_ctrl`, `espressif/servo`,
  `espressif/mdns`, e futuros (TFLM, WebSocket) sem fricção.
- `menuconfig` / `sdkconfig.defaults` para partições OTA, rollback
  (ADR-007), Kconfig do servo_dog_ctrl.
- Builds reproduzíveis (`idf.py build` + sdkconfig versionado).
- Caminho limpo para TFLM (ADR-005) — TensorFlow Lite Micro integra-se
  melhor via IDF component que via Arduino library manager.
- `idf.py monitor` superior ao Serial Monitor do Arduino IDE.
- Estrutura de projeto profissional (CMake, components, testes).

### Negativas

- **Curva de aprendizado** — ESP-IDF é mais complexo que Arduino IDE
  (CMake, menuconfig, components, partitions). Mas ADR-001 já previa
  "curva de aprendizado de FreeRTOS" como consequência aceita.
- **Tutorial ACEBOTT fica como referência** — os sketches `.ino` do
  tutorial não rodam diretamente no ESP-IDF sem adaptação. O tutorial
  permanece útil como referência de ensino, não como firmware do cão.
- **Build/flash mais lento** que Arduino IDE para mudanças triviais —
  compensado pela robustez.
- **Bibliotecas Arduino específicas** (ex.: Adafruit_SSD1306) podem
  precisar de adaptação ou substituição por equivalentes IDF. O
  arduino-esp32 core como IDF component pode ser usado como fallback.

### Notas

- O `sdkconfig.defaults` deve incluir: `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y`
  (ADR-007), `CONFIG_ESP_HI_WEB_CONTROL_ENABLED=n` (ADR-013, WebUI off),
  esquema de partições "Two OTA" (ADR-007).
- Os sketches `.ino` do tutorial ACEBOTT (`samples/biped_robot_full/`,
  `tutorial/`) permanecem no repo como referência — não são deletados,
  mas não são o firmware do cão.
- PlatformIO pode ser usado como IDE com backend ESP-IDF se o autor
  preferir uma IDE sobre o `idf.py` CLI.
