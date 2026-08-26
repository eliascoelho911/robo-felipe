# Visão do Tamagotchi: Câmera GC0308 + Endpoint Multimodal em Nuvem

> **Ticket de pesquisa** que subsidia o subsistema de visão da variante
> Tamagotchi. O [ADR-017](../decisions/017-tamagotchi-inclui-camera-gc0308.md)
> colocou a câmera GC0308 e o sensor de proximidade LTR-553ALS-WA no
> escopo de hardware. Este documento define o caminho de visão:
> **captura local (driver DVP) + descrição via endpoint multimodal em
> nuvem** (VLLM). A detecção on-device fica fora do escopo imediato — a
> preferência do projeto é visão em nuvem.

| | |
|---|---|
| **Data da pesquisa** | 2026-08-26 |
| **Status** | Concluída — recomendação clara (xiaozhi captura + servidor VLLM) |
| **Alimenta** | ADR-017 (casos de uso de visão), futura ADR de nuvem |
| **Confiança** | Alta — métricas e código verificados via GitHub API em 2026-08-26; docs de visão do servidor fetchados em 2026-08-26 |

## Metodologia e fontes

Busca via `gh search repos` / `gh search code` / `gh api` + `webfetch`
de READMEs e docs oficiais (incl. `docs/mcp-vision-integration.md` do
servidor xiaozhi). Métricas verificadas em 2026-08-26.

## Decisão de produto (preferência registrada)

O projeto prefere **visão em nuvem via endpoint multimodal** (VLLM
OpenAI-compatível: Gemini/Qwen-VL/GPT-4o), não detecção on-device. O
fluxo é:
1. Firmware do CoreS3 captura JPEG da GC0308 (320×240)
2. POSTa `multipart/form-data` (foto + pergunta) para um endpoint HTTP
3. O VLLM configurado na Nuvem descreve a imagem
4. O LLM conversacional incorpora a descrição e o TTS fala na persona
   do pet

Detecção on-device (face/mão/gesto via `esp-dl`) fica documentada como
**alternativa futura**, não como caminho primário.

---

## 1. Driver da câmera — `espressif/esp32-camera`

- **URL**: https://github.com/espressif/esp32-camera · 2.771★ ·
  **Licença**: Apache-2.0 · **Último commit**: 2026-06-05.
- **S3 + GC0308**: ✅ oficial. Tabela de sensores lista
  `GC0308 640×480 — YUV/YCbCr422, RAW Bayer, RGB565, Grayscale, 1/6"`.
  SoCs suportados: ESP32, **S3**, S2. Driver `sensors/gc0308.c` +
  `gc0308_regs.h` + `gc0308_settings.h`. DVP S3 em
  `target/esp32s3/ll_cam.c`.
- **PSRAM**: necessário p/ resoluções >CIF;
  `CONFIG_CAMERA_PSRAM_DMA` (S2/S3), toggável em runtime via
  `esp_camera_set_psram_mode()`.
- **Frameworks**: ESP-IDF, Arduino-ESP32 core, PlatformIO.
- **É o que o xiaozhi usa?** Sim — `main/idf_component.yml` depende de
  `espressif/esp32-camera`.
- **Completeness**: **8/10** (não detecta, mas é o alicerce que todos
  usam).

## 2. Firmware de referência — `78/xiaozhi-esp32` (já com CoreS3 + GC0308)

- **URL**: https://github.com/78/xiaozhi-esp32 · 29.181★ · MIT ·
  pushed 2026-08-21.
- **CoreS3/GC0308 nativos**: `main/boards/m5stack/core-s3/config.json`
  → `target: esp32s3`, `CONFIG_CAMERA_GC0308=y`,
  `CONFIG_CAMERA_GC0308_AUTO_DETECT_DVP_INTERFACE_SENSOR=y`,
  `CONFIG_CAMERA_GC0308_DVP_YUV422_320X240_20FPS=y`,
  `CONFIG_SPIRAM_MODE_QUAD=y`.
- **Protocolo de imagem** (verificado em `mcp-protocol.md` +
  `mcp_server.cc` + `esp32_camera.cc`):
  - MCP tool `self.camera.take_photo` — descrição: *"Always remember you
    have a camera. If the user asks you to see something, use this tool
    to take a photo and then explain it."* O LLM decide quando "olhar".
  - `Esp32Camera::Capture()` pega frame; `Esp32Camera::Explain(question)`
    codifica JPEG em thread dedicada (chunks em PSRAM) e faz
    **POST `multipart/form-data`** (campo `question` + parte
    `file=camera.jpg`, `Content-Type: image/jpeg`,
    `Transfer-Encoding: chunked`, `Authorization: Bearer <token>`) para
    `explain_url_`. Retorna o texto-descritivo (que o LLM incorpora e o
    TTS fala).
  - `explain_url` + token vêm das **capabilities** do dispositivo: objeto
    `"vision": { "url": "...", "token": "..." }` — *explicitamente "must
    be an http URL, not a websocket URL"*.
  - Há também `self.screen.preview_image` para mostrar imagens no
    display.
- **Portanto: o protocolo xiaozhi JÁ suporta enviar imagens** — não pelo
  WS de áudio, mas por um endpoint HTTP dedicado (LLM multimodal em
  nuvem). **Não há "visual Q&A" on-device no xiaozhi** (a descrição vem
  da nuvem).
- **Completeness**: **10/10** para "ver e conversar"; não traz detecção
  on-device (fora do escopo atual).

## 3. Nuvem — `xinnan-tech/xiaozhi-esp32-server` (slot VLLM)

- **URL**: https://github.com/xinnan-tech/xiaozhi-esp32-server ·
  10.432★ · MIT · pushed 2026-08-21.
- **VLLM plugável**: o slot `selected_module.VLLM` aceita *"qualquer
  VLLM compatível com a interface OpenAI"* (ChatGLM-VL, Gemini,
  Qwen-VL, GPT-4o). Configura-se `api_key` no `data/.config.yaml`.
- **Endpoint de visão**: `http://seu-server:8003/mcp/vision/explain`
  (porta 8003 por default). O servidor recebe o `multipart` do
  dispositivo, encaminha ao VLLM, devolve texto.
- **Doc oficial de visão** (`docs/mcp-vision-integration.md`):
  confirma o fluxo completo — setar `selected_module.VLLM` (ex.:
  `ChatGLMVLLM`), configurar `server.vision_explain` com URL público,
  ativar com "abra a câmera e diga o que você vê".
- **Free tier**: `glm-4v-flash` (ChatGLM) é gratuito — entry point sem
  custo p/ experimentar.

## 4. Alternativa on-device (documentada, NÃO primária)

Para referência futura, caso o pet precise reagir sem nuvem (ex.:
acordar ao ver rosto, gesto por toque), a stack é:

### `espressif/esp-dl` — inferência on-device
- **URL**: https://github.com/espressif/esp-dl · 1.124★ · MIT ·
  pushed 2026-08-26.
- **Modelos que rodam em ESP32-S3** (Model Zoo): Human Face Detect,
  Face Recognition, **Hand Detect**, **Hand Gesture (8+ gestos)**,
  COCO Detect (YOLO11n), COCO Pose, Cat/Dog, Pedestrian, Person ReID,
  MobileNetV2, Motion, Color.
- **Latência S3 (int8)**: face `espdet_pico_224` ≈ **140 ms**;
  hand-gesture 128 ≈ **118 ms**; face 2-stage ≈ **44 ms**. Trabalhável
  a poucos fps em janelas curtas.
- **PSRAM**: modelos int8 pequenos em flash rodata/partição/SD; o
  planejador posiciona camadas em RAM interna vs PSRAM. PSRAM do CoreS3
  cobre frame + working set.

### `espressif/esp-who` — demos + framework
- **URL**: https://github.com/espressif/esp-who · 2.133★ ·
  MIT "só produtos Espressif" (CoreS3 ok) · pushed 2026-08-21.
- Exemplos atuais: `human_face_recognition`, `object_detect`,
  `object_tracking`, `qrcode_recognition`, `pp_ocr_v6`. Suporta LVGL.
- Board S3: ESP32-S3-EYE. GC0308 funciona trocando pin config.

### Edge Impulse — classificador custom
- SaaS; exporta lib C++ p/ S3 (integrar com esp32-camera à mão). Útil
  p/ classificador custom leve ("estou sendo segurado?"). Sem target
  S3-câmera oficial.

### `espressif/esp-tflite-micro` — alternativa genérica
- **URL**: https://github.com/espressif/esp-tflite-micro · 693★ ·
  Apache-2.0. Exemplo `person_detection` no ESP32-S3-EYE. Catálogo de
  visão p/ S3 magro vs esp-dl.

## 5. Sensor de proximidade — LTR-553ALS-WA

- Driver: `lewisxhe/SensorLib` (MIT, ESP-IDF+Arduino) tem LTR-553
  (ALS+Prox c/ INT). Ver
  [research de operação](tamagotchi-operacao.md) §wake.
- Caso de uso: "pet acorda ao ver o Sobrinho" — mão se aproximando do
  vidro dispara INT → wake do ESP32 (complementa touch e motion).

---

## Recomendação

### ADOPT (caminho primário — visão em nuvem)
1. **`78/xiaozhi-esp32`** (ESP-IDF) como firmware-base: câmera
   **GC0308 + CoreS3 já configurados**, captura 320×240 YUV422, e
   **visual-Q&A via nuvem já implementada** (`Explain()` + MCP
   `take_photo`). Isto cobre "o que eu estou vendo?" (nuvem) e a UX
   conversacional.
2. **`espressif/esp32-camera`** — o driver (já dependência do xiaozhi;
   GC0308 + S3 DVP oficial).
3. **`xinnan-tech/xiaozhi-esp32-server`** — hospedar o endpoint
   `vision.url` (`/mcp/vision/explain`) e plugar um VLLM multimodal
   (Gemini/Qwen-VL/4o).

### COMPOSE (se visão on-device virar necessária no futuro)
- `espressif/esp-dl` (+ `esp-who`) para detecção local sem nuvem
  (face/gesture/motion). Rodar em janela/core dedicado para não bater
  na latência de voz (risco já listado no ADR-017).
- Edge Impulse só para classificador custom leve.

---

## Lacunas que exigem código custom

1. **Provisionar o `vision.url`**: o Tamagotchi precisa de um endpoint
   multimodal em nuvem (Qwen-VL/Gemini/4o) que aceite o `multipart` do
   `Explain()`. Hoje o `vision.url` é entregue via `capabilities` pelo
   servidor xiaozhi — definir na ADR de nuvem quem hospeda.
2. **Tuning GC0308**: configs de mirror/flip/framesize já existem no
   xiaozhi (`camera_hmirror/vflip`,
   `CONFIG_CAMERA_GC0308_DVP_YUV422_320X240_20FPS`); ajustar
   AWB/gain/exposure p/ a ótica do CoreS3 e baixa luminância pode
   precisar de retoque em `gc0308_settings.h`.
3. **Concorrência visão↔voz no S3** (se on-device): captura + inferência
   consomem CPU/PSRAM; precisa rodar em janelas curtas/core dedicado
   para não bater na latência de TLS+áudio+KWS (risco já listado no
   ADR-017). **Não se aplica ao caminho de nuvem** (só captura JPEG +
   POST, leve).
4. **Mapeamento GPIO I2S/áudio**: ADRs já alertam que o pinout I2S do
   WROOM precisa remapear p/ S3 — não é bloqueador de visão, mas
   convém confirmar que os 12 GPIO do DVP não colidem com o subsistema
   de áudio. No `core-s3/config.h` já estão alocados sem conflito.

## Notas de licença
- esp32-camera (Apache-2.0), esp-dl (MIT), esp-tflite-micro
  (Apache-2.0), xiaozhi-esp32 + xiaozhi-esp32-server (MIT): todos
  permissivos.
- esp-who: MIT "só produtos Espressif" (CoreS3 é Espressif → ok).
- `espressif/esp-detection` (treino): **AGPL-3.0** — caveat se modificar
  o treino e distribuir; os pesos `.espdl` resultantes não herdam AGPL
  por si, mas convém confirmar.

---

## Referências

| Componente | URL | Licença | Verificado em |
|:--|:--|:--|:--|
| `78/xiaozhi-esp32` | https://github.com/78/xiaozhi-esp32 | MIT | 2026-08-26 |
| `xinnan-tech/xiaozhi-esp32-server` | https://github.com/xinnan-tech/xiaozhi-esp32-server | MIT | 2026-08-26 |
| `espressif/esp32-camera` | https://github.com/espressif/esp32-camera | Apache-2.0 | 2026-08-26 |
| `espressif/esp-dl` | https://github.com/espressif/esp-dl | MIT | 2026-08-26 |
| `espressif/esp-who` | https://github.com/espressif/esp-who | ESPRESSIF MIT | 2026-08-26 |
| `espressif/esp-tflite-micro` | https://github.com/espressif/esp-tflite-micro | Apache-2.0 | 2026-08-26 |
| `espressif/esp-detection` | https://github.com/espressif/esp-detection | AGPL-3.0 | 2026-08-26 |
| Doc visão (servidor) | https://github.com/xinnan-tech/xiaozhi-esp32-server/blob/main/docs/mcp-vision-integration.md | — | 2026-08-26 |
