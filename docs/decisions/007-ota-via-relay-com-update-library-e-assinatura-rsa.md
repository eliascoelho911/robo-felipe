# ADR-007: OTA via relay com Update library embutida e assinatura RSA

## Status
Accepted

## Date
2026-07-12

## Context

O robô-felipe usa o smartphone como relay local (ADR-002) e mantém um
WebSocket persistente com o app para a pipeline de voz (ADR-006). Esse
canal de controle já está aberto, autenticado na LAN, e transporta
mensagens binárias e textuais.

Conforme o produto evoluir, o firmware do ESP32 precisará ser
atualizado — novas features de voz, ajustes de servo, retraining do
modelo KWS (ADR-005), correções de bugs. Reprogramar por cabo USB a
cada mudança é impraticável para o usuário final.

OTA (Over-The-Air) é o mecanismo padrão para isso. O ESP32 suporta OTA
nativamente: partições A/B (duas slots de firmware), escrita na partição
inativa, reboot para a nova imagem, e rollback automático se a nova
imagem não se validar.

A questão arquitetural é **como o binário chega ao ESP32** e **como
garantir que ele é legítimo** (não foi corrompido ou substituído por um
atacante na LAN).

Restrições do projeto:

- **Link ESP32 ↔ relay não tem TLS** (decisão do ADR-002 — TLS termina
  no relay, na borda da nuvem). O canal LAN é Trusted-ish, não
  criptografado.
- **Sem PSRAM**, 4 MB flash (ver ADR-001). Esquema de partição precisa
  comportar duas slots OTA + modelo KWS + nvs.
- **Robô de hobby** — não pode brickar. Rollback automático é
  essencial; secure boot por eFuse (irreversível) é overkill.
- **Canal WebSocket já aberto** para controle/áudio (ADR-006) —
  reusá-lo evita abrir nova porta/protocolo.

Pesquisa (workflow `/search-first`) avaliou cinco candidatos:

| # | Solução | Modelo | Score | Veredito |
|---|---------|--------|-------|----------|
| 1 | **`Update` library** (embutida no arduino-esp32) | Push primitivo via Stream/chunks | **9/10** | `Update.write(buf,len)` aceita chunks arbitrários — encaixa no WebSocket existente. OTA assinada via `UpdaterRSAVerifier` (RSA). |
| 2 | ESP-IDF OTA APIs (`esp_ota_*`) | Primitivo C | 7/10 | Necessário só para habilitar rollback (`CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE`). Anti-rollback por eFuse é irreversível — overkill. |
| 3 | esp32FOTA (chrisjoyce911, 414★, v0.3.0, Unlicense) | HTTP-pull + manifest JSON | 8/10 | Lib madura: semver, assinatura RSA, gzip, SPIFFS. Mas é **pull** — o ESP32 busca de um servidor HTTP. Não é push-via-WebSocket. |
| 4 | ArduinoOTA (embutida) | MDNS + UDP/TCP, para IDE | 4/10 | Feita para upload do IDE na mesma rede; não para app-driven production push. |
| 5 | Secure Boot v2 (Espressif, eFuse) | Bootloader-level | 3/10 | Queima chave em eFuse irreversivelmente; brick risk se perder a chave. Overkill para robô hobby. |

## Decision

**Adotar a `Update` library embutida no arduino-esp32 como motor de OTA,
empurrando o binário via WebSocket existente (reusando o canal do
ADR-006), com verificação de assinatura RSA e rollback automático
anti-brick.**

Um shim minimalista (~150 linhas) no firmware mapeia mensagens do
WebSocket para chamadas da `Update` API. Nenhuma biblioteca de OTA de
alto nível é adicionada — o motor é nativo, o protocolo de entrega é o
WebSocket já aberto.

### Protocolo de entrega (relay → ESP32, reusando WebSocket)

```
relay → WS text : {"cmd":"ota_begin","size":N,"version":"x.y.z"}
                   └─ ESP32 responde {"ok":true,"free":F}; aborta se free < N
relay → WS bin  : chunk de ~4 KB  (× ceil(N/4096))
                   └─ ESP32 responde {"ack":bytes_written} a cada chunk
relay → WS text : {"cmd":"ota_end"}
                   └─ ESP32 verifica assinatura, responde {"ok":true}
                      ou {"err":"signature_failed"}
ESP32 → ESP.restart()
```

Reuso do WebSocket:
- **Mesma porta, mesma conexão, mesmo código de controle.** Nenhuma
  nova porta aberta no firewall do celular, nenhum novo protocolo.
- Mensagens binárias já são suportadas (ADR-006 as usa para frames de
  áudio).
- Backpressure natural: o ESP32 confirma cada chunk antes do próximo.

### Assinatura RSA (independe do canal não-TLS)

Como o link LAN ESP32 ↔ relay não tem TLS (ADR-002), a autenticidade do
firmware **não depende do canal** — depende da assinatura no binário.

- Embarcar `public_key.h` (RSA-3072) no firmware em flash.
- No build do firmware (CI ou local):
  ```
  openssl genrsa -out priv_key.pem 3072          # uma vez, guardado em segredo
  openssl rsa -in priv_key.pem -pubout > rsa_key.pub
  openssl dgst -sign priv_key.pem -sha256 -out fw.sign -binary fw.bin
  cat fw.sign fw.bin > fw.img                   # firmware assinado
  ```
- No firmware, antes de `Update.end()`:
  ```cpp
  UpdaterRSAVerifier sign(PUBLIC_KEY, PUBLIC_KEY_LEN);
  Update.installSignature(&sign);
  ```
- Se `Update.end()` retornar `UPDATE_ERROR_SIGN`, abortar e não reboot.

A chave privada fica fora do firmware (CI secret / máquina do
desenvolvedor). Só a pública vai no robô.

### Rollback anti-brick grátis

Habilitar `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y` em
`sdkconfig.defaults`. Esquema de partição: **"Two OTA"** (factory +
ota_0 + ota_1 + otadata + nvs) — cabe folgado nos 4 MB.

No 1º boot pós-OTA, o firmware novo roda em estado
`ESP_OTA_IMG_PENDING_VERIFY`. Self-test em ~30 s:
- WiFi conecta?
- I2S mic lê amostras?
- Modelo KWS carrega?
- Servos respondem a um comando de teste?

Se tudo OK → `esp_ota_mark_app_valid_cancel_rollback()` (commit).
Se falha → `esp_ota_mark_app_invalid_rollback_and_reboot()` (reverte).

Se o firmware novo **travar no boot** e nunca chamar a função de
commit, o bootloader auto-reverte após o watchdog — brick-proof sem
intervenção.

### Versionamento

Macro `FIRMWARE_VERSION` ("x.y.z") no firmware. O relay consulta um
manifest da nuvem (GitHub Releases / bucket S3), compara com a versão
do robô (obtida via WS no handshake inicial), e só empurra se `> current`.

## Alternatives Considered

### esp32FOTA (pull, HTTP) como biblioteca de alto nível

- **Prós:**
  - Semver, assinatura RSA, gzip, SPIFFS — tudo pronto.
  - 414★, mantida (v0.3.0 Nov 2025), Unlicense.
  - Exemplos abundantes.
- **Contras:**
  - **Modelo pull** — o ESP32 busca o binário de um servidor HTTP na
    nuvem. Isso exige que o robô tenha TLS e conecte-se à internet
    diretamente, **contrariando o ADR-002** (o ESP32 é STA only, o TLS
    termina no relay, o robô não fala com a nuvem direto).
  - Para usar pull, o relay precisaria expor um mini HTTP server na LAN
    (GCDWebServer no iOS, NanoHTTPD no Android) servindo o `manifest.json`
    + `fw.img` baixado da nuvem. Isso é **mais complexo** que o push
    via WebSocket já aberto — troca 150 linhas de shim por um HTTP server
    no celular.
  - Abre uma segunda porta/protocolo no relay, além do WebSocket.
- **Rejeitada:** o push via WebSocket reusa o canal de controle
  existente e respeita o ADR-002 (ESP32 não fala com a nuvem). esp32FOTA
  é uma ótima lib, mas seu modelo pull não se encaixa na arquitetura
  relay-centric.

### ArduinoOTA (embutida, para IDE)

- **Prós:**
  - Zero configuração para desenvolvimento.
  - Já no arduino-esp32.
- **Contras:**
  - **Feita para o IDE** — descoberta por MDNS, upload do IDE na mesma
    rede. Não é um protocolo para app mobile orquestrar em produção.
  - **Sem assinatura** — o binário não é verificado. Inaceitável no
    nosso cenário (link LAN sem TLS).
  - **Sem versionamento** — o usuário precisa saber qual versão enviar.
- **Rejeitada:** inadequada para produção app-driven. Pode ser mantida
  como ferramenta de desenvolvimento (IDE → robô) paralelamente, mas
  não é o mecanismo de OTA do produto.

### Secure Boot v2 (eFuse, bootloader-level)

- **Prós:**
  - Verificação de assinatura no bootloader, antes do boot. Máxima
    garantia de integridade.
  - Anti-rollback por eFuse irreversível.
- **Contras:**
  - **Queima chave em eFuse irreversivelmente** — se a chave privada
    vazar, todos os robôs com essa chave ficam comprometidos para
    sempre (não dá pra revogar).
  - **Brick risk** — se a chave pública no eFuse não corresponder à
    chave privada usada para assinar, o robô nunca mais dá boot.
    Recuperação só por JTAG/USB com reflash de bootloader.
  - **Overkill para robô de hobby** — a assinatura em software via
    `UpdaterRSAVerifier` já cobre o modelo de ameaça (atacante na LAN
    tentando injetar firmware falso).
- **Rejeitada:** risco de brick irreversível não se justifica. A
  assinatura em software (verificada antes do commit do OTA, não no
  boot) atinge o mesmo objetivo de integridade com menos perigo.

### HTTP server no relay + esp32FOTA (pull via mini-server no celular)

- **Prós:**
  - Zero código de OTA no ESP32 — usa esp32FOTA como-is.
  - Reusa toda a lógica de semver/assinatura da lib.
- **Contras:**
  - **HTTP server no celular** — GCDWebServer (iOS) ou NanoHTTPD
    (Android) adiciona complexidade ao app.
  - **Segunda porta/protocolo** além do WebSocket — mais coisa para
    configurar, debugar, manter em iOS background.
  - **Não reusa o canal existente** — derrota a vantagem arquitetural
    de ter um WebSocket único de controle.
- **Rejeitada:** o shim de ~150 linhas no ESP32 é mais simples que um
  HTTP server no celular, e mantém a arquitetura limpa (um canal, um
  protocolo).

## Consequences

### Positivas

- **Zero nova dependência** — `Update` library é embutida no
  arduino-esp32, já no build. Nenhum `library.json` adicionado.
- **Reuso do WebSocket** — nenhuma porta extra no relay, nenhum
  protocolo novo, nenhum HTTP server no celular.
- **Assinatura RSA independe do canal** — mesmo com link LAN sem TLS,
  firmware falso é rejeitado. Chave privada fora do robô.
- **Rollback anti-brick grátis** — se a nova imagem travar, o
  bootloader reverte para a anterior automaticamente. Sem
  intervenção, sem JTAG.
- **Self-test pós-OTA** — só faz commit da nova imagem se WiFi, I2S,
  KWS e servos passarem nos diagnósticos. Catches regressões
  silenciosas.
- **Unifica mecanismo de update** — o mesmo `Update.write()` serve
  para atualizar partição `data` (modelo KWS) no futuro, se o modelo
  crescer além do que cabe embarcado no firmware.
- **Flash cabe folgado** — esquema "Two OTA" (factory 1 MB + ota_0
  1 MB + ota_1 1 MB + otadata + nvs) usa ~3.3 MB dos 4 MB disponíveis.
  Modelo KWS (~30 KB) embarca dentro de cada slot de firmware.

### Negativas

- **Shim custom (~150 linhas)** — código de glue entre WebSocket e
  `Update`. Não é muito, mas é código a manter e testar.
- **Self-test em 30 s no 1º boot** — o robô fica indisponível por 30 s
  após cada OTA. Aceitável, mas o usuário precisa ser avisado pelo app.
- **Binário assinado dobra o fluxo de build** — `openssl dgst -sign` é
  um passo extra no CI; se esquecido, o OTA é rejeitado. Mitigado por
  script de build automatizado.
- **Chave privada é secreto de longo prazo** — se vazar, todos os
  robôs aceitam firmware do atacante. Mitigado por guarda em CI
  secret (GitHub Actions) e rotação planejada (ex.: nova chave a cada
  release major, com chave pública de fallback embarcada).
- **Rever OTA via IDE (ArduinoOTA) separadamente** — desenvolvimento
  de bancada pode querer upload rápido sem assinatura; precisa ficar
  claro que isso é só para dev, não para produção.

### Notas

- O handshake inicial do WebSocket (após conectar) já informa a
  `FIRMWARE_VERSION` do robô ao relay — reusar essa mensagem, sem
  custo extra.
- O relay (app mobile) consulta o manifest da nuvem em segundo plano
  (GitHub Releases, bucket S3, etc.) e, ao detectar versão mais
  nova, baixa `fw.img` assinado e empurra via WebSocket quando o robô
  estiver conectado e ocioso.
- O chunk de 4 KB alinha-se com o tamanho de página de flash
  (`spi_flash_write` exige alinhamento de 4 KB para região de app),
  reduzindo overhead de buffer no ESP32.
- Durante o OTA, o streaming de áudio (ADR-006) é pausado — o
  WebSocket é half-duplex na prática, e a RAM da pipeline de voz é
  liberada para o buffer de escrita OTA.
- Esta ADR **depende de** ADR-002 (relay) e ADR-006 (WebSocket) —
  junto, definem o canal de atualização do firmware.
- Referência de assinatura: mesma receita documentada pela esp32FOTA
  (`openssl dgst -sign` + `cat fw.sign fw.bin > fw.img`), adaptada para
  push via WebSocket em vez de pull HTTP.
