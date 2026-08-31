# Firmware de Voz para o Tamagotchi (ESP32-S3 + CoreS3)

> **Ticket de pesquisa** que subsidia a decisão de firmware da variante
> Tamagotchi. A restrição arquitetural já está decidida no
> [ADR-016](../decisions/016-tamagotchi-processa-voz-sem-relay-de-smartphone.md)
> (autocontido, TLS termina no próprio ESP32-S3 com PSRAM, sem relay de
> smartphone). Este documento cobre **qual stack de firmware de voz
> adotar**, incluindo KWS local, streaming de áudio, transporte TLS e
> orquestração da nuvem (ASR/LLM/TTS). A câmera é tratada à parte em
> [research de visão](tamagotchi-visao-cam.md).

| | |
|---|---|
| **Data da pesquisa** | 2026-08-26 |
| **Status** | Concluída — recomendação clara (adotar xiaozhi + servidor) |
| **Alimenta** | ADR-016 (valida o caminho TLS-direto), futura ADR de firmware do Tamagotchi |
| **Confiança** | Alta — métricas de repositório e código verificados via GitHub API em 2026-08-26; README do servidor fetchado em 2026-08-26 |

## Metodologia e fontes

Busca via `gh search repos` / `gh search code` / `gh api` (GitHub CLI
autenticado) + `webfetch` de READMEs e docs oficiais. Métricas (stars,
último commit, licença) verificadas em 2026-08-26. O `78/xiaozhi-esp32`
já é nomeado no `CONTEXT.md` como projeto de referência — esta pesquisa
aprofunda a avaliação dele e busca alternativas.

## Glossário (ver `CONTEXT.md`)

- **Nuvem** = orquestrador ASR/NLP/TTS (auto-hospedável, trocável sem
  mudar firmware). O que é proibido é o **Relay** (smartphone, ADR-002).
  Logo, um servidor orquestrador na nuvem **não viola** o ADR-016 — é
  exatamente o que o glossário classifica como "Nuvem".

---

## 1. Firmware de assistente de voz conversacional E2E para ESP32-S3

### `78/xiaozhi-esp32` — o vencedor claro

- **URL**: https://github.com/78/xiaozhi-esp32
- **Stars/Forks**: 29.181 ★ / 6.745 forks · **Linguagem**: C++ ·
  **Licença**: MIT (comercial OK)
- **Último commit**: 2026-08-21 (atividade quase diária); criado
  2024-08-31; 673 issues abertas; top contributor "78" (456 commits) +
  ~30 contributors; base em **ESP-IDF v6.0.2**; **171 variantes de
  release, 138 diretórios de board**.
- **TLS**: terminado no dispositivo. `websocket_protocol.cc` cria
  WebSocket via `network->CreateWebSocket(1)` (flag TLS), envia header
  `Authorization: Bearer <token>`, conecta a `wss://...`. **Sem relay**
  — o dispositivo fala direto com a "Nuvem".
- **Wake word customizável**: SIM — interface `WakeWord` plugável
  (`main/audio/wake_word.h`); backends `EspWakeWord` (WakeNet s/AFE),
  `AfeWakeWord` (WakeNet c/AFE, padrão p/ S3+PSRAM), `CustomWakeWord`
  (MultiNet). **Porém** `CustomWakeWord` = MultiNet só suporta **chinês
  (pinyin) e inglês** — não português.
- **M5Stack CoreS3**: ✅ **Suporte first-class**.
  `main/boards/m5stack/core-s3/` (`config.h`,
  `cores3_audio_codec.{h,cc}`, `m5stack_core_s3.cc`, `README.md`,
  `config.json`). Pinout **bate exato** com o BOM de áudio do projeto:
  I2S MCLK=0/WS=33/BCLK=34/DIN=14/DOUT=13; I2C SDA=12/SCL=11; codecs
  **AW88298** (alto-falante) + **ES7210** (mic, 3 canais); display
  320×240; pinos de câmera definidos. **Zero portabilidade necessária
  para CoreS3.**
- **pt-BR**: idioma de interface é `PT_PT` (português europeu), não
  `PT_BR`. A voz conversacional pt-BR vem do provedor de TTS configurado
  no servidor (lado "Nuvem"), não do firmware.
- **Completeness 1–10 (pet conversacional autocontido em S3)**: **9/10**
- **Prós p/ este projeto**: MIT; pronto p/ CoreS3 com os mesmos codecs;
  pipeline de voz completo e testado em escala (171 variantes);
  interface de wake word plugável (abre porta p/ "Felipe"); já tem
  emoji/câmera (alinhado ao ADR-017); nuvem trocável sem mudar firmware
  (define "Nuvem"); comunidade enorme e ativa.
- **Contras**: (1) wake word em português não existe no caminho padrão
  (ver esp-sr); (2) idioma de UI é pt-PT, não pt-BR; (3) protocolo
  assume um **servidor compatível com xiaozhi** entre o device e os
  provedores — não fala "direto" com a API de um único provedor; (4) UI
  é de chatbot genérico, não simulação de pet; (5) default aponta para
  `xiaozhi.me` (servidor chinês) — precisa apontar para "Nuvem" própria.

### `xinnan-tech/xiaozhi-esp32-server` — a "Nuvem" orquestradora

- **URL**: https://github.com/xinnan-tech/xiaozhi-esp32-server ·
  10.432★ · MIT · JS/Python · pushed 2026-08-21.
- **O que é**: orquestrador ASR/LLM/TTS plugável (modo "tudo-API" ou
  FunASR self-hosted), MQTT+UDP e WebSocket, MCP, reconhecimento de
  locutor (3D-Speaker), base de conhecimento (RAGFlow).
- **LLM conversacional via endpoint próprio**: ✅ — o slot
  `selected_module.LLM` aceita *"qualquer LLM compatível com a interface
  OpenAI"*: DeepSeek, Gemini, Ollama (local), Dify, Coze, Xinference,
  HomeAssistant, ou um endpoint OpenAI-API próprio. Configura-se
  `api_key` + `base_url` no `data/.config.yaml`. O firmware não sabe
  quem responde — a Nuvem fica trocável sem mudar o firmware (como o
  glossário define).
- **README em pt-BR**: existe `docs/readme/README_pt_BR.md` (comunidade
  brasileira ativa).
- **VLLM (visão)**: ver [research de visão](tamagotchi-visao-cam.md).

### Concorrentes diretos

A busca `gh search repos "voice assistant esp32"` não retornou
**nenhum** concorrente maduro — todos os 15 resultados têm 0–2★ e são
projetos pessoais (`bennyzen/espie` 2★, `Yehiaraslan/7akim-voice` 0★,
`CapitanaIcoachai/local-voice-edge` 0★ — este último é "ESP32 como
cliente de áudio + servidor Python", i.e., arquitectura relay-like).
**Conclusão: xiaozhi domina sozinho o espaço.**

---

## 2. KWS / wake word on-device em ESP32-S3

### `espressif/esp-sr` (WakeNet + AFE) — o caminho padrão do xiaozhi

- **URL**: https://github.com/espressif/esp-sr · 1.492★ · **Licença**:
  "ESPRESSIF MIT" (grátis em chips Espressif — ESP32-S3 OK; código
  aberto mas atado a HW Espressif) · **Linguagem**: C · **Último
  commit**: 2026-08-19 · `xiaozhi` o depende como
  `espressif/esp-sr ~2.4.7`.
- **Módulos**: **AFE** (AEC + VAD + BSS + NS, qualificado
  Alexa-built-in), **WakeNet** (KWS, modelos
  `wn9/wn9l/wn9s/wn10`), **VADNet**, **MultiNet** (comandos offline —
  só zh/en, até 200–300 comandos), Speech Synthesis.
- **Wake words disponíveis**: ~70 modelos, todos em **chinês, inglês,
  francês, japonês**. **Nenhum em português.**
- **Customização de wake word**: dois caminhos oficiais — (a) serviço
  gratuito Espressif (submeter corpus, ~3 semanas, exige 500+ locutores
  reais OU projeto/votos); (b) `CustomESP-SR.com` (~US$ 1k/palavra, 10
  dias, modelo proprietário `wn9_*.bin`). Adicionalmente, **TTS Pipeline
  V3** treina wake words por amostras sintéticas — hoje só
  zh/en/ja/fr; **português está PLANEJADO** mas não entregue (issue #88
  aberta desde 2023-12, 449 comentários, atualizada 2026-08-26:
  "Planned support for: Korean, Spanish, Portuguese, German, Russian,
  and Arabic").
- **Custom wake word (xiaozhi)**: `USE_CUSTOM_WAKE_WORD` usa
  **MultiNet** → só zh/en. Impossível fazer "Felipe" em pt-BR pelo
  caminho nativo hoje.
- **pt-BR**: ❌ não suportado (planejado há ~3 anos, sem entrega).
- **Completeness**: **7/10** (pipeline robusto, mas gap de idioma mortal
  p/ "Felipe").
- **Prós**: AFE de produção (AEC/VAD/NS) grátis; já integrado ao
  xiaozhi; baixo footprint; wakeNet10 recente.
- **Contras**: sem português; customização real é paga ou lenta;
  MultiNet (comandos) só zh/en; licença atada a HW Espressif.

**Complemento — `espressif/esp-skainet`**
(https://github.com/espressif/esp-skainet · 964★ · licença Espressif ·
pushed 2026-02-14): exemplos/apps de referência para esp-sr
(reconhecimento de comandos cn/en). Sem board M5Stack CoreS3 (usa
ESP-BOX, Korvo, S3-EYE). Útil como **referência de uso do esp-sr**, não
como firmware.

### `kahrendt/microWakeWord` — wake word open-source, independente de idioma

- **URL**: https://github.com/kahrendt/microWakeWord · 10★ ·
  **Licença**: Apache-2.0 · governado pela **Open Home Foundation**
  (fork de `OHF-Voice/micro-wake-word`).
- **Modelos prontos (verificado em 2026-08-31)**: o "model zoo" oficial
  — `esphome/micro-wake-word-models` (121★, Apache-2.0) — entrega
  **apenas wake words em inglês**: v1 `alexa`/`hey_jarvis`/`okay_nabu`;
  v2 +`hey_mycroft`/`vad`; `v2/experiments` (não suportados, "use por
  sua conta e risco") `choo_choo_homie`/`hey_home_assistant`/
  `hey_peppa_pig`/`okay_computer`. **Nenhum modelo em pt-BR / "Felipe"
  existe off-the-shelf** — `microWakeWord` é um *framework de treino*
  (early release, "advanced users"), não um repositório de modelos.
  Amostras positivas são geradas via `rhasspy/piper-sample-generator`
  (Piper tem voz pt-BR `pt_BR-cadu` — ver research selfhosted), logo a
  **única rota é treinar** um modelo "Felipe" com Piper pt-BR.
- **Como funciona**: áudio mono 16 kHz → espectrograma 40 features/10
  ms (preprocessor do `micro_speech` c/ NS + AGC) → modelo streaming
  MixConv INT8 → .tflite. **Treinamento por amostras sintéticas** (TTS)
  + ambientes negativos; métricas de false-accept/hora otimizadas.
  **Independente de idioma** — pode treinar "Felipe" com amostras
  sintéticas em português.
- **Status do treinamento**: "Treinar novos modelos é para usuários
  avançados. Treinar um modelo que funciona bem ainda é difícil"
  (exige experimentação de hiperparâmetros).
- **Runtime em S3**: consome o `.tflite` via **ESPHome
  `micro_wake_word`** (produção) ou via **`xiaozhi-tflite`** (ponte p/
  xiaozhi). Requer PSRAM.
- **pt-BR "Felipe"**: ✅ **factível** (idioma-agnóstico). Exige gerar
  amostras TTS de "Felipe" em pt-BR + ruído ambiente + treinar.
- **Completeness**: **6/10** (treina o modelo que falta ao esp-sr, mas
  sozinho não é firmware — precisa de um runtime/host).
- **Prós**: Apache-2.0 puro; idioma-agnóstico (único caminho realmente
  open p/ "Felipe" hoje); saída roda em S3 c/ PSRAM.
- **Contras**: treinar bem é difícil/ML-heavy; sozinho não entrega
  firmware nem UI; precisa de ponte p/ integrar ao xiaozhi.

**Runtime de produção — ESPHome `micro_wake_word`**
(`esphome/esphome` · 11.608★ · pushed 2026-08-26): componente maduro
que executa modelos microWakeWord `.tflite` em ESP32-S3. **Caveat**:
ESPHome é **outro framework** (YAML, não Arduino/IDF C++ como o
xiaozhi). Adotar `micro_wake_word` via ESPHome significa abandonar o
xiaozhi — **rota divergente**. Melhor tratar como **referência de
implementação**.

### `temm1e-labs/xiaozhi-tflite` — a ponte microWakeWord ↔ xiaozhi

- **URL**: https://github.com/temm1e-labs/xiaozhi-tflite · 2★ / 1 fork
  · **Licença**: NOASSERTION · pushed 2026-05-17.
- **O que faz**: adiciona um **4º backend `WakeWord` ao xiaozhi** que
  roda `.tflite` treinado pelo microWakeWord via
  `espressif/esp-tflite-micro`, em **ESP32-S3/P4 com PSRAM**. Patch de
  3 arquivos + componente symlinkado. Expõe `USE_TFLITE_WAKE_WORD` no
  Kconfig.
- **Por que importa**: **revela que a interface `WakeWord` do xiaozhi é
  plugável** — e dá o esqueleto exato de como plugar um wake word em
  português ("Felipe") treinado por microWakeWord/Edge Impulse.
  Documenta que o caminho esp-sr custom é "Espressif 3 semanas grátis
  (500+ locutores) OU CustomESP-SR.com US$1k".
- **Status**: **v0 — feature-complete em software, AGUARDANDO
  verificação on-device** (não testado em HW). Apenas 2★ → **alto
  risco, não pronto p/ adoção direta**.
- **Completeness**: **5/10** (conceito certo, maturidade muito baixa).
- **Prós**: exatamente o que falta (Felipe em pt-BR dentro do
  xiaozhi); derivado do `micro_wake_word` do ESPHome (Apache-2.0,
  preservado em `src/_reference/`); alocações em PSRAM.
- **Contras**: não verificado em device; 2★; mantenedor único;
  precisará de adaptação quando o xiaozhi evolui.

### `Picovoice/porcupine` — proprietário, binding Apache

- **URL**: https://github.com/Picovoice/porcupine · 4.922★ ·
  **binding** Apache-2.0 (Python) · pushed 2026-08-12. (Binding
  ESP32: `picovoice-esp32`.)
- **Licença real**: o **engine e os modelos são proprietários**
  (Picovoice License) — free tier p/ uso pessoal **com limite de
  ativações de dispositivo**; comercial exige licença paga; **custom
  words treinadas no Picovoice Console** (suporta qualquer idioma,
  **incl. português**).
- **Completeness**: **6/10** (engine maduro, mas licença restritiva).
- **Prós**: melhor qualidade/menor FAR-FRR do mercado; treinar
  "Felipe" em pt-BR é trivial no Console; binding ESP32 existe.
- **Contras**: **não é open-source de fato** (engine/modelos
  proprietários); free tier limita ativações e **veda redistribuição**
  — problema se o projeto for publicado; dependência de vendor. **Não
  recomendado** para um projeto que o autor pode abrir/compartilhar.

### Edge Impulse — SaaS hosted, suporta S3

- Não é repo de firmware; é **plataforma hosted**. Suporta ESP32-S3,
  treina KWS custom (incl. português "Felipe") por upload de amostras,
  exporta **C++ library ou TFLite-Micro** (que roda via
  `esp-tflite-micro`).
- **Licença**: Developer tier grátis (pessoal); runtime/comercial pago
  acima de threshold.
- **Completeness**: **7/10** p/ obter um modelo Felipe rápido; **4/10**
  como solução aberta de longo prazo.
- **Prós**: menos esforço de ML que microWakeWord; UI de coleta/treino;
  exporta p/ S3.
- **Contras**: vendor lock-in; licença do runtime p/ comercial; não é
  "open-source" no espírito do projeto.

### Outros (menção — não adequados)

- **`espressif/esp-tflite-micro`** (693★ · Apache-2.0 · pushed
  2026-08-14): runtime TFLite-Micro p/ ESP32 — **infraestrutura**
  usada por microWakeWord/xiaozhi-tflite/Edge Impulse. Adotar
  transitivamente.
- **`dscripka/openWakeWord`** (2.702★ · Apache-2.0 · pushed
  2025-12-30): wake word Python/ONNX, alvo Pi/desktop/server. **Pesado
  demais p/ ESP32-S3 on-device** — não serve p/ o pet.
- **`TaterTotterson/microWakeWords`** (202★): firmware ESPHome p/
  satellites Home Assistant — **RETIRED**. Inativo, ignorar.

---

## 3. mbedTLS / cliente TLS em ESP32

Não há um "repo" isolado a adotar — é **prática de ESP-IDF**, e o
**xiaozhi já a implementa** (WSS com `Authorization: Bearer`).
Recomendações/best-practices verificadas:

- **Camada**: `esp-tls` (+ `esp_http_client`, `esp_websocket_client`)
  sobre **mbedTLS** — todos parte do ESP-IDF, usados pelo xiaozhi. Para
  WSS, o `WebSocket` do xiaozhi já encapsula handshake + TLS.
- **Hardware crypto do ESP32-S3**: ativa aceleração AES/SHA/RSA/ECC
  (mbedTLS HW accel) — reduz CPU e memória do handshake.
  `sdkconfig.defaults.esp32s3` do xiaozhi já configura.
- **Certificados**: usar **`esp_crt_bundle`** (Pacote Mozilla CA, ~9
  KB) para validar a cadeia da nuvem pública, **ou** embutir o CA do
  seu servidor/self-hosted como `servercert.pem`. Em PSRAM, buffers TLS
  podem ser maiores (handshake mais robusto).
- **SNI/ALPN**: habilitar SNI (obrigatório p/ virtual hosts de
  provedores) e ALPN `h2`/`http/1.1` conforme o provedor. Renegociação
  geralmente desativada (vuln).
- **Cert rotation/OTA**: atualizar certs via OTA (`esp_https_ota`) — o
  xiaozhi já tem OTA. Manter `CONFIG_ESP_TLS_INSECURE=n`,
  `CONFIG_MBEDTLS_HARDWARE_AES/SHA/RSA/ECC=y`.
- **Token/segredo**: o token de acesso (`Authorization: Bearer`) é lido
  de NVS, nunca hardcoded. xiaozhi já faz isso (`settings.h`).
- **Conclusão**: a camada TLS **não é gap** ao adotar o xiaozhi — já
  termina TLS no dispositivo, sem relay.

---

## Recomendação

### ADOPT as-is
1. **`78/xiaozhi-esp32`** — firmware-base do Tamagotchi. É,
   literalmente, um pet conversacional autocontido em ESP32-S3 com
   **suporte first-class a M5Stack CoreS3** (pinout e codecs
   AW88298/ES7210 idênticos ao BOM do projeto), MIT, pipeline de voz
   completo (KWS→Opus→WSS/TLS→ASR→LLM→TTS→Opus→speaker), AEC, UI de
   emoji, câmera. Usar a board `m5stack/core-s3` sem portabilidade.
2. **`espressif/esp-sr`** (~2.4.7) — KWS+AFE padrão (já dependência do
   xiaozhi). Adotar para **AFE (AEC/VAD/NS)** e, interinamente, um wake
   word preset (`wn9_hiesp`/`wn9_hijason_tts2`) até ter "Felipe".
3. **`xinnan-tech/xiaozhi-esp32-server`** — adotar como a **"Nuvem"**
   auto-hospedada (orquestrador ASR/LLM/TTS plugável, MIT, comunidade
   pt-BR). Configurar provedores pt-BR no servidor — firmware não muda.

### EXTEND (código custom necessário)
1. **Wake word "Felipe" em pt-BR** — o **maior gap**. O caminho nativo
   (esp-sr) não tem português (e o MultiNet "custom" só é zh/en). Rotas:
   - **Preferencial (open)**: treinar modelo microWakeWord "Felipe" com
     amostras TTS pt-BR + ruído, integrar ao xiaozhi via
     `xiaozhi-tflite` (precisa **verificar/estabilizar** a ponte v0 em
     hardware CoreS3).
   - **Rápida (paid/proprietary)**: `CustomESP-SR.com` (~US$1k) gera
     `wn9_felipe.bin` que o xiaozhi já sabe carregar — sem tocar no
     firmware, mas modelo proprietário.
   - **Baixo-esforço ML**: Edge Impulse treina "Felipe" e exporta
     TFLite-Micro → mesma ponte `xiaozhi-tflite` (atenção à licença do
     runtime p/ comercial).
   - **Esperar**: pedir "Felipe" no esp-sr issue #88 quando o pipeline
     TTS pt decolar (grátis, mas incerto).
2. **i18n pt-BR**: só existe `LANGUAGE_PT_PT`. Reusar pt-PT (próximo)
   **ou** fork dos strings para `pt-BR` (esforço baixo — é só texto de
   UI; a voz vem do TTS configurado pt-BR no servidor).
3. **Personalidade Tamagotchi**: xiaozhi tem face de emoji genérica.
   Customizar expressões/estados de pet (display 320×240 +
   `esp_emote_expression` já presente) para o Sobrinho de 8 anos. Ver
   [research de pet-engine + UI](tamagotchi-pet-engine-ui.md).

### COMPOSE
**Firmware**: `78/xiaozhi-esp32` (board `m5stack/core-s3`) + `esp-sr`
(AFE + wake word interim) + **`xiaozhi-tflite` + modelo microWakeWord
"Felipe"** (wake word pt-BR).
**Nuvem**: `xinnan-tech/xiaozhi-esp32-server` auto-hospedado,
configurado com provedores pt-BR — ASR (ex.: Deepgram/Azure Whisper),
LLM (qualquer endpoint OpenAI-compatível — DeepSeek/Gemini/Ollama
local), TTS (ex.: Azure/ElevenLabs/edge-tts, voz pt-BR infantil) —
tudo trocável sem refazer firmware.
**TLS**: já resolvido pelo xiaozhi (WSS on-device); só embutir o CA da
sua Nuvem e confirmar crypto HW no `sdkconfig`.

### Solução ÚNICA mais completa para o pipeline de voz
**`78/xiaozhi-esp32` (device) + `xinnan-tech/xiaozhi-esp32-server`
(nuvem).** Juntos cobrem KWS + streaming Opus + TLS/WSS + protocolo
ASR/LLM/TTS + AEC + locutor + MCP + UI/emoji/câmera, em ESP32-S3 PSRAM,
MIT, M5Stack CoreS3 pronto. Tudo o mais é fragmento.

---

## Lacunas que precisam código custom

1. **Wake word "Felipe" em pt-BR** — inexistente off-the-shelf no
   esp-sr; exige treinar microWakeWord (+ estabilizar a ponte
   `xiaozhi-tflite`) **ou** pagar `CustomESP-SR.com` (proprietário).
   **Gap #1.**
2. **Strings pt-BR** (só pt-PT existe) — fork de i18n, baixo esforço.
3. **Personalidade/UI de Tamagotchi** — xiaozhi é chatbot, não
   simulação de pet; design de expressões/estados para o Sobrinho.
4. **Ajuste conversacional pt-BR p/ 8 anos** — prompt de sistema
   child-safe, vocabulário simples, respostas curtas e baixa latência;
   **configuração do servidor/Nuvem**, não firmware. Ver [research de
   pet-engine](tamagotchi-pet-engine-ui.md) para a ponte estado→prompt.
5. **CA/TLS da Nuvem própria** — embutir certificado/`esp_crt_bundle` e
   rotação via OTA (mecânica, não bloqueador).
6. **Remapeamento I2S** — **NÃO necessário** para CoreS3 (o
   `core-s3/config.h` já usa os GPIOs do ES7210/AW88298 do BOM do
   projeto); só aplicaria a uma placa custom diferente da CoreS3.

---

## Referências

| Componente | URL | Licença | Verificado em |
|:--|:--|:--|:--|
| `78/xiaozhi-esp32` | https://github.com/78/xiaozhi-esp32 | MIT | 2026-08-26 |
| `xinnan-tech/xiaozhi-esp32-server` | https://github.com/xinnan-tech/xiaozhi-esp32-server | MIT | 2026-08-26 |
| `espressif/esp-sr` | https://github.com/espressif/esp-sr | ESPRESSIF MIT | 2026-08-26 |
| `espressif/esp-skainet` | https://github.com/espressif/esp-skainet | ESPRESSIF MIT | 2026-08-26 |
| `kahrendt/microWakeWord` | https://github.com/kahrendt/microWakeWord | Apache-2.0 | 2026-08-31 |
| `esphome/micro-wake-word-models` (model zoo) | https://github.com/esphome/micro-wake-word-models | Apache-2.0 | 2026-08-31 |
| `temm1e-labs/xiaozhi-tflite` | https://github.com/temm1e-labs/xiaozhi-tflite | NOASSERTION | 2026-08-26 |
| `Picovoice/porcupine` | https://github.com/Picovoice/porcupine | Proprietário (binding Apache) | 2026-08-26 |
| `espressif/esp-tflite-micro` | https://github.com/espressif/esp-tflite-micro | Apache-2.0 | 2026-08-26 |
| Docs servidor (visão, MCP) | https://github.com/xinnan-tech/xiaozhi-esp32-server/tree/main/docs | — | 2026-08-26 |
