# AGENTS.md

Instruções para agentes OpenCode neste repositório.

## Idioma

Toda documentação (ADRs, research, CONTEXT.md, comentários) é em
**português (pt-BR)**. Escreva novos arquivos `.md` em português para
 manter o padrão. Mensagens de commit também em português, primeira
 palavra no imperativo (ex.: "Remove variantes bípede/quadrúpede...").

## Glossário — siga `CONTEXT.md`

`CONTEXT.md` define a linguagem ubiquitástica do projeto com entradas
**_Avoid_**. Use EXATAMENTE os termos prescritos e evite os listados:

- Diga **Tamagotchi** (variante atual), nunca "o bichinho" / "o pet".
- Diga **Robô Felipe**, nunca "o robô" / "o cachorro".
- Diga **Sobrinho** (usuário final, 8 anos), nunca "criança" / "usuário".
- **Relay** e **Nuvem** têm definições precisas lá — consulte antes de
  usar em ADRs.

Não invente variantes de corpo: as três existentes são **Bípede**,
**Quadrúpede** e **Tamagotchi**.

## Branches = variantes de corpo

Cada variante de corpo do robô vive num branch separado. Você está no
branch `tamagotchi` (a variante atual, em desenvolvimento):

| Branch | Variante | MCU | Status |
|:---|:---|:---|:---|
| `tamagotchi` | pet de bolso (sem servos/câmera) | ESP32-S3 + PSRAM | ativa |
| `quadrupede` | cão de 4 patas, chassi ESP-HI | ESP32-WROOM-32E-N4 | arquivada |
| `main` | bípede ACEBOTT | ESP32-WROOM-32E-N4 | arquivada |

**Não espere** encontrar neste branch: pinout-quadrupede, tutorial ACEBOTT,
sketch do bípede, 3d-models, datasheet do WROOM, ESP32-CAM. Esses foram
removidos — vivem nos branches arquivados. Se uma referência parece
faltante, provavelmente está noutro branch.

## ADRs são registro histórico imutável

`docs/decisions/` contém os ADRs. **Não reescreva o contexto técnico
de um ADR para refletir o estado atual** — eles registram a decisão da
época. Especificamente:

- ADRs 001, 002, 005, 006, 007 (presentes neste branch) foram escritos
  para o bípede/quadrúpede no WROOM-32E-N4 (sem PSRAM, com relay de
  smartphone). Referências a "WROOM", "biblioteca `ACB_Biped_Robot`", "4
  servos", "relay" são intencionais e corretas para a decisão histórica.
- ADR-016 (Tamagotchi) revoga o relay para a variante atual. É o único
  ADR da variante Tamagotchi neste branch. ADRs 003, 004 e 008–015 existem
  no branch `quadrupede`.
- Para criar um novo ADR, siga o formato dos existentes (Status / Date /
  Context / Decision / Alternatives Considered / Consequences / Notas).

## `android/` não é escopo do Tamagotchi

O app Android (Kotlin + Compose, 43 arquivos) é o **relay smartphone**
das variantes bípede/quadrúpede (ADR-002). ADR-016 explicitamente remove
o app do escopo do Tamagotchi. Ele permanece tracked neste branch por
enquanto, mas **não invista tempo melhorando-o** a menos que o usuário
peça.

Para testar o app (se necessário): `./gradlew test` (unit) ou
`./gradlew connectedAndroidTest` (instrumentado) em `android/`. Gradle
9.1.0, Kotlin 2.3.20, Compose BOM 2026.03.01.

## Firmware ESP32 — toolchain incompleta

Não há build system de firmware ESP32 committed no repositório. Os
sketches em `samples/` são referência; o toolchain Arduino é gerenciado
localmente via `.vscode/`:

- `.vscode/arduino.json` ainda aponta para board **WROOM**
  (`esp32:esp32:esp32`, `PSRAM=disabled`). Para compilar firmware do
  Tamagotchi é preciso trocar para uma board ESP32-S3 com PSRAM
  habilitada — **ainda não configurada** (placeholder em
  `hardware/cores3/`).
- `arduino-cli` não está no PATH deste ambiente.
- `samples/stream_mic_serial/` é o sample de áudio I2S reusável
  (mic + decimação), útil para validar o subsistema de áudio isolado.

## Sem CI, sem pre-commit, sem lint

Não há workflows em `.github/`, pre-commit, Makefile, nem comandos de
lint/typecheck no repo. A verificação de mudanças é por inspeção e
`grep` de resíduos. Após editar `.md`, confira que nenhum link aponta
para arquivo removido (pattern: `tutorial/`, `hardware/mcu/`,
`hardware/esp32-cam/`, `pinout-quadrupede`, `esp32-wroom-32e-n4`).

## Subsistema de áudio é compartilhado

`hardware/audio/` (BOM-audio.md, esquema-audio.md) documenta o subsistema
de áudio (SPH0645LM4H + MAX98357A) desenhado para o WROOM, **reusado**
pelo Tamagotchi. O pinout GPIO documentado é do WROOM; na variante
Tamagotchi os GPIOs do I2S precisam ser remapeados para o ESP32-S3. Os
datasheets/PDFs de referência do WROOM foram removidos deste branch
(vivem nos branches arquivados).

## `tutorial_raw/` é gitignored

Materiais brutos de tutoriais do kit de origem (~300 MB de PDFs/drivers/
instaladores) vivem em `/tutorial_raw/` e são ignorados pelo git. Não
tente commitar esse conteúdo.
