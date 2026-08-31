# ADR-020: OTA pull com esp32FOTA, assinatura RSA in-app e manifest no GitHub Releases

## Status

Accepted

## Date

2026-08-31

## Context

O Tamagotchi é **autocontido, sem relay** ([ADR-016](016-tamagotchi-processa-voz-sem-relay-de-smartphone.md)): o ESP32-S3 termina TLS ele mesmo (mbedTLS, RAM absorvida pela PSRAM) e fala HTTPS direto com a Nuvem. Sem smartphone intermediário, não há WebSocket de controle por onde empurrar firmware — o mecanismo de OTA do [ADR-007](007-ota-via-relay-com-update-library-e-assinatura-rsa.md) (push via relay) **não se aplica** a esta variante. O ADR-016 já registrava, em Notas, que "sem relay, OTA vira pull; candidata natural é `esp32FOTA` — merece sua própria ADR". Esta é essa ADR.

O hardware é o M5Stack CoreS3: ESP32-S3 FN8, **16 MB flash**, 8 MB PSRAM ([ADR-019](019-tamagotchi-hardware-m5stack-cores3.md)). O firmware-base candidato é `78/xiaozhi-esp32` (research `tamagotchi-firmware-voz.md`), que traz uma classe `Ota` nativa em `main/ota.cc`.

### O `Ota` nativo do xiaozhi-esp32 — inspeção do código

Leitura direta de `main/ota.cc`/`ota.h` (verificado em 2026-08-31) revela três fatos decisivos:

1. **Não há verificação de assinatura do binário.** `Ota::Upgrade()` faz HTTP GET do `firmware_url`, escreve direto em `esp_ota_*` e confia no TLS + no servidor xiaozhi. Nenhum `UpdaterRSAVerifier`, nenhum hash assinado.
2. **O HMAC de efuse é para ativação, não integridade.** `Ota::Activate()` usa `esp_hmac_calculate(HMAC_KEY0, …)` com um challenge do servidor para provar a identidade do dispositivo e obter um código de ativação. É autenticação do dispositivo perante o servidor xiaozhi — **não** verificação de que o firmware baixado é legítimo.
3. **`CheckVersion()` é um acoplamento operacional.** Além de `firmware.version`/`url`, a respostaJSON carrega `mqtt`, `websocket`, `server_time` e `activation`. É o ponto de configuração do modelo operacional xiaozhi — não apenas um manifest de firmware.

### Filosofia de segurança herdada do ADR-007

O ADR-007 (histórico, bípede/quadrúpede) estabeleceu o modelo de ameaça do projeto: **robô de hobby** → assinatura RSA verificada *in-app* basta (antes do commit, não no boot); Secure Boot v2 por eFuse é *overkill* (brick risk irreversível); anti-rollback por eFuse irreversível é *overkill*. Essa premissa carrega para o Tamagotchi — o que muda é apenas o vetor de entrega (push via WebSocket → pull via HTTPS). O ADR-007 rejeitou o `esp32FOTA` *para o quadrupede* apenas porque seu modelo pull exigiria um mini-HTTP-server no relay; no Tamagotchi, sem relay, o pull é o caminho natural e correto.

### Pesquisa

`../research/tamagotchi-operacao.md` (Need 1, 2026-08-26) comparou três opções com métricas verificadas via GitHub API:

| Lib / Solução | ★ | Licença | Assinatura | App + FS | Veredito |
|---|---|---|---|---|---|
| **chrisjoyce911/esp32FOTA** | 417 (v0.3.0) | Unlicense | RSA in-app (indep. de Secure Boot) | ✅ SPIFFS/LittleFS/FAT | Adotar + estender |
| `esp_https_ota` (ESP-IDF) | — | Apache-2.0 | Secure Boot v2 (bootloader) + eFuse | ❌ (você escreve fs) | Muito glue |
| `78/xiaozhi-esp32` (`Ota` nativo) | 29,1k | MIT | ❌ (só TLS) | ❌ app only | Amarrado ao server xiaozhi |

## Decision

**Adotar `esp32FOTA` (v0.3.0, Unlicense) como motor de OTA pull, substituindo a classe `Ota` nativa do xiaozhi-esp32 por um wrapper próprio, com assinatura RSA-4096 verificada in-app, manifest JSON e binários hospedados no GitHub Releases, anti-rollback por semver in-app (sem eFuse), atualizando app + filesystem LittleFS na mesma rodada.**

### Mecanismo — esp32FOTA substitui o `Ota` do xiaozhi

Um wrapper próprio chama `esp32FOTA.execHTTPcheck()` (consulta o manifest, compara semver) e `esp32FOTA.execOTA()` (baixa + assina + instala). O fluxo de ativação/MQTT/websocket-config do `Ota` nativo é **removido** — não usamos o servidor xiaozhi para nada além do firmware de referência. O `MarkCurrentVersionValid()` (commit pós-self-test) é preservado. A substituição de `ota.cc` é detalhada na futura ADR de firmware.

### Hospedagem — GitHub Releases

- **Manifest** (`manifest.json`) publicado no branch default (servido via `raw.githubusercontent.com`) ou em GitHub Pages — sempre a versão "mais recente". URL fixa configurada no firmware via `setManifestURL()`.
- **Binários** (`firmware.img`, `filesystem.img`) publicados como **assets de GitHub Release tagueada** (semver). O manifest aponta para eles via formato por componentes (`host`/`port`/`bin`/`littlefs`), pois o campo `url` ignora filesystem.
- **CA bundle embutido** do Arduino 3.x (`esp32FOTA.useBundledCerts()`) cobre `github.com` — sem certificado custom a manter. O ADR-016 já previa "CA bundle no firmware"; esta ADR confirma que o bundle padrão do Arduino-ESP32 basta para GitHub.
- **Redirect**: GitHub Releases redireciona (302) para `objects.githubusercontent.com`. O HTTP client subjacente segue redirects por padrão — **validar empiricamente** no primeiro build.

### Assinatura RSA-4096 in-app

- `check_sig = true`. Cada imagem (app e fs) é assinada com RSA-4096 (`openssl dgst -sign priv_key.pem -sha256`) e prepended da assinatura (512 B): `cat firmware.sign firmware.bin > firmware.img`.
- **Chave pública embarcada em progmem** (`CryptoMemAsset`), dentro da partição de app — **não** no LittleFS que é atualizado. O LittleFS não tem redundância A/B: se a chave morasse lá, um OTA de fs corrompido perderia a chave e brickaria o OTA. Em progmem, a chave sobrevive a qualquer OTA de fs.
- **CA bundle também em progmem** (embutido no app pelo Arduino-ESP32), pelo mesmo motivo.
- Chave privada fica fora do firmware (CI secret / máquina do desenvolvedor), como no ADR-007.

### Anti-rollback por semver in-app (sem eFuse)

O app recusa instalar qualquer versão **mais velha** que a sua (comparação semver via `h2non/semver.c`, embutida no esp32FOTA). Não queima `secure_version` em eFuse. Isso mantém a operação **reversível** (consistente com o ADR-007: eFuse irreversível é overkill para hobby). O modelo de ameaça cobre: um atacante que comprometa o GitHub e troque o manifest para apontar a um firmware *velho válido* é barrado pelo semver; um atacante sem a chave privada não consegue assinar nada que passe na verificação.

### App + filesystem LittleFS (ambos assinados)

- Ambas as partições são atualizadas na mesma rodada, **ambas assinadas** (`check_sig` aplica-se a app e fs).
- **Sem compressão**: gzip/zlib é incompatível com assinatura no esp32FOTA ("⚠️ This feature cannot be used with signature check"). Os 16 MB de flash do CoreS3 absorvem binários não-comprimidos; a banda WiFi de um brinquedo atualizado ocasionalmente é aceitável.
- **Ordem**: o esp32FOTA atualiza o filesystem **primeiro**, depois o app. Se a assinatura do fs falhar, aborta antes de tocar o app.
- **`type`** no manifest (`"robo-felipe-tamagotchi"`) impede flash cruzado de firmware de outra variante (ex.: quadrúpede) no Tamagotchi — mesma proteção por "firmware type" do ADR-007.

### Self-test + rollback anti-brick

`CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y`. No 1º boot pós-OTA, o firmware roda em `ESP_OTA_IMG_PENDING_VERIFY`. Self-test em ~30 s (WiFi conecta? I2S mic/speaker lêem amostras? modelo KWS carrega? display responde?). Se OK → `esp_ota_mark_app_valid_cancel_rollback()` (commit). Se falha ou trava → o bootloader reverte para a imagem anterior automaticamente. O checklist exato do self-test é definido na ADR de firmware.

## Alternatives Considered

### `Ota` nativo do xiaozhi-esp32 (manter, apontar `ota_url` ao nosso server)

- **Prós:** zero divergência de upstream; semver in-app já pronto (`IsNewVersionAvailable`); rollback (`MarkCurrentVersionValid`) já implementado.
- **Contras:** **sem verificação de assinatura do binário** — confia só no TLS. Se o CA bundle ou o endpoint forem comprometidos, firmware falso é aceito. Acoplado ao protocolo do server xiaozhi (`mqtt`/`websocket`/`activation`/`server_time` em `CheckVersion`) — herdaríamos o modelo de ativação por código, que não faz sentido para um produto de consumo autocontido do Sobrinho. App-only (sem fs).
- **Rejeitada:** a ausência de assinatura é inaceitável como defesa-em-profundidade, e o acoplamento operacional contradiz a independência do servidor xiaozhi (Core auto-hospedado, ADR-018).

### `esp_https_ota` (ESP-IDF cru) + Secure Boot v2

- **Prós:** verificação no bootloader (antes do boot); anti-rollback por eFuse nativo; criptografia de imagem (`decrypt_cb`).
- **Contras:** **baixo nível** — manifest, semver e atualização de fs são glue a construir do zero. Secure Boot v2 queima chave em eFuse **irreversível** (brick risk se a chave privada vazar ou for perdida). Overkill para brinquedo de hobby (ADR-007).
- **Rejeitada:** o custo de glue e o risco de eFuse irreversível não se justificam. A assinatura in-app do esp32FOTA atinge a mesma integridade com menos perigo.

### Anti-rollback por eFuse (`secure_version`)

- **Prós:** defesa em profundidade — nem firmware antigo assinado dá boot.
- **Contras:** **irreversível** — se a versão for incrementada por engano, dispositivos ficam brickados. Overkill para hobby (ADR-007). Um atacante precisaria da chave privada para assinar firmware velho válido; o semver in-app já barraria downgrade via manifest.
- **Rejeitada:** semver in-app basta para o modelo de ameaça; eFuse irreversível é risco desnecessário.

### Manifest hospedado no Core (auto-hospedado, ADR-018)

- **Prós:** controle de rollout por dispositivo (canary, full); o Core já tem TLS.
- **Contras:** se o Core cair, o pet não descobre/atualiza. O Core é um protótipo em PC hoje (ADR-018). GitHub Releases é CDN grátis, versionado por tag, e não depende do Core estar no ar.
- **Rejeitada para o MVP:** GitHub Releases é mais confiável para distribuição de firmware. O Core pode ganhar um papel de orquestração de rollout no futuro (manifest do Core apontando aos binários do GitHub) sem mudar esta decisão.

## Consequences

### Positivas

- **Assinatura RSA independe do canal** — mesmo que o CA bundle seja comprometido ou o GitHub sirva um binário adulterado, a verificação in-app rejeita firmware não-assinado. Defesa-em-profundidade sobre o TLS do ADR-016.
- **Independência do servidor xiaozhi** — o pet puxa firmware de um manifest nosso no GitHub, sem ativação por código nem config de MQTT/WebSocket herdada. Coerente com o Core auto-hospedado (ADR-018) e o produto de consumo do Sobrinho.
- **App + filesystem na mesma rodada** — sprites, assets e dados do pet (research `tamagotchi-pet-engine-ui.md`) são atualizáveis sem cabo.
- **CA bundle embutido** — sem manter certificado custom; `useBundledCerts()` cobre GitHub.
- **Rollback anti-brick grátis** — partições A/B do app; self-test commit; bootloader reverte se travar.
- **Sem eFuse irreversível** — toda a operação é recuperável (consistente com ADR-007).
- **GitHub Releases** — grátis, CDN global, versionado por tag, sem infra a manter.
- **`type` no manifest** — impede flash de firmware de variante errada.

### Negativas

- **Divergência de upstream do xiaozhi** — substituir `ota.cc` é uma customização a manter no fork. Já customizaremos o xiaozhi para pt-BR/wake-word/personalidade; mais uma alteração, mas é custo real de merge em upgrades de upstream.
- **Filesystem sem redundância A/B** — partição única de LittleFS. Um fs corrompido num OTA falho exige restart + `format` (perde assets até a próxima OTA). A chave pública e o CA em progmem (não no fs) mitigam o brick, mas os assets do pet se perdem.
- **Sem compressão** — gzip incompatível com assinatura. Binários maiores no flash e na banda; os 16 MB do CoreS3 absorvem, mas OTA de fs grande é mais lento.
- **Atomicidade app↔fs** — o fs é atualizado primeiro; se o app falhar após o fs OK, o rollback do bootloader reverte o **app** mas o **fs fica novo** (app velho + fs novo). Mitigação: manter o schema do fs **versionado e forward-compatible** (app velho lê fs novo) e fazer OTA de fs raramente, agrupado com app.
- **Redirect do GitHub Releases** — `github.com/…/releases/download/…` redireciona para `objects.githubusercontent.com`. O HTTP client segue 302 por padrão, mas **validar empiricamente** no primeiro build (limite de redirects, URL assinada expirando).
- **Chave privada RSA = secreto de longo prazo** — se vazar, todos os pets aceitam firmware do atacante. Mitigado por guarda em CI secret e rotação planejada (nova chave a cada release major, com pública de fallback embarcada). Sem eFuse, não há revogação por hardware.
- **Self-test de ~30 s pós-OTA** — o pet fica indisponível após cada atualização. Aceitável, mas o Sobrinho precisa ser avisado pelo display.
- **Sem anti-rollback por eFuse** — um atacante **com a chave privada** pode empurrar firmware velho assinado (o semver in-app só barraria via manifest legítimo). Aceitável para hobby; o vetor exige comprometimento da chave privada.

## Notas

- **Supersede parcial do ADR-007 para a variante Tamagotchi** — apenas no vetor de entrega (push via WebSocket → pull via HTTPS). O ADR-007 permanece Accepted para as variantes bípede/quadrúpede (com relay). Não reescrevemos o ADR-007; registramos a exceção por escopo, como feito no ADR-016.
- **Fecha a pendência do ADR-016** — "OTA vira pull; candidata natural `esp32FOTA` — merece sua própria ADR".
- **Política de build** (firmware + fs):
  ```
  # uma vez (guardar priv_key.pem em segredo):
  openssl genrsa -out priv_key.pem 4096
  openssl rsa -in priv_key.pem -pubout > rsa_key.pub

  # a cada release (app e fs):
  openssl dgst -sign priv_key.pem -sha256 -out firmware.sign -binary firmware.bin
  cat firmware.sign firmware.bin > firmware.img
  openssl dgst -sign priv_key.pem -sha256 -out filesystem.sign -binary filesystem.bin
  cat filesystem.sign filesystem.bin > filesystem.img
  ```
  Publicar `firmware.img` + `filesystem.img` como assets da Release `vX.Y.Z`; atualizar `manifest.json` no branch default.
- **`manifest.json`** (formato por componentes, obrigatório para app+fs):
  ```json
  {
    "type": "robo-felipe-tamagotchi",
    "version": "0.1.0",
    "host": "github.com",
    "port": 443,
    "bin": "/<org>/<repo>/releases/download/v0.1.0/firmware.img",
    "littlefs": "/<org>/<repo>/releases/download/v0.1.0/filesystem.img"
  }
  ```
- **Esquema de partições** — 16 MB flash: `factory` + `ota_0` + `ota_1` + `otadata` + `nvs` + partição LittleFS. O layout exato é definido na ADR de firmware, mas esta ADR estabelece a **restrição**: deve haver A/B de app (rollback) + uma partição de dados (fs OTA) + NVS (estado do pet).
- **Próxima ADR de firmware** detalhará: (a) a substituição de `ota.cc` do xiaozhi pelo wrapper esp32FOTA; (b) o checklist do self-test pós-OTA; (c) o esquema de partições; (d) o agendamento da checagem de OTA (no wake do RTC? na conexão WiFi?).
- **Rotação de CA bundle** — o bundle embutido do Arduino-ESP32 é atualizado a cada release do core. Como o OTA é pull, um bundle expirado impede a própria atualização. Mitigação: manter o core atualizado nos builds; bundle roda via OTA junto com o app (em progmem).
- **Referência**: research [`../research/tamagotchi-operacao.md`](../research/tamagotchi-operacao.md) Need 1; README do `chrisjoyce911/esp32FOTA` verificado em 2026-08-31.
