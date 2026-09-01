# AGENTS.md

Instruções para agentes OpenCode no repositório **robo-felipe**. Este
arquivo é o ponto de entrada; subsistemas com regras próprias têm
`AGENTS.md` aninhados (`android/AGENTS.md`, `core/AGENTS.md`).

## Propósito

Robô Felipe é um robô conversacional de voz em **português (pt-BR)**,
variante **Tamagotchi** — um pet de bolso para o **Sobrinho** (8 anos),
autocontido num M5Stack CoreS3 (ESP32-S3 com PSRAM), que fala direto com
a nuvem sem relay de smartphone. Os ADRs 016–023 governam a variante
atual; as variantes bípede/quadrúpede anteriores estão arquivadas nos
branches `main` e `quadrupede`.

## Idioma

- **Código e identificadores** em inglês (nomes de variáveis, funções,
  tipos, arquivos de código).
- **Comentários no código** em **pt-BR** — só o porquê, nunca o que o
  código já diz.
- **Documentação** (ADRs, research, CONTEXT.md, este arquivo) em pt-BR.
- **Mensagens de commit** em pt-BR, primeira palavra no imperativo
  (ex.: "Remove variantes bípede/quadrúpede...").

## Glossário — `CONTEXT.md` é autoritativo

`CONTEXT.md` define a linguagem ubiquitástica com entradas _Avoid_. Use
EXATAMENTE os termos prescritos e evite os listados. Termos centrais:

- **Tamagotchi** (variante atual), nunca "o bichinho" / "o pet".
- **Robô Felipe**, nunca "o robô" / "o cachorro".
- **Sobrinho** (usuário final, 8 anos), nunca "criança" / "usuário".
- **Plataforma**, **Trigger**, **Batch**, **Core**, **Ação**, **Plano de
  Ações**, **Nuvem**, **Relay** têm definições precisas — consulte antes
  de usar em ADRs ou código.

Não invente variantes de corpo: as três existentes são **Bípede**,
**Quadrúpede** e **Tamagotchi**.

## Arquitetura — o contrato que governa tudo

O Robô Felipe separa **comportamento** (o que o pet faz) de **encarnação**
(onde ele roda). Comportamento mora num **Core TypeScript
auto-hospedado**; a encarnação é uma **Plataforma** que detecta Triggers,
envia Batches ao Core e executa o Plano de Ações que volta.

```
Plataforma ──Trigger──► Batch ──► Core (TS) ──Plano de Ações──► Plataforma
  (Android hoje,            │       │  ├── estado do pet (18 stats)
   CoreS3 depois)           │       │  ├── HTTP endpoints (adapter Python
                           │       │  │   bridgeia ao LLM do xiaozhi-server)
                           │       │  └── Nuvem (xiaozhi-esp32-server:
                           │       │      ASR/LLM/TTS pt-BR)
                           └─ o contrato Batch→Plano de Ações
                              sobrevive à troca de host
```

**Regras arquiteturais não-negociáveis:**

1. **Comportamento nunca mora no firmware ou no app.** Firmware e app
   são Plataformas: eles detectam Triggers e executam Ações, nada mais.
   Lógica de pet, decay de stats, decisão do que dizer — tudo no Core.
2. **O contrato Batch→Plano de Ações é o que sobrevive à troca de host.**
   A Plataforma Android de hoje e o CoreS3 de amanhã devem falar o mesmo
   contrato. Mudar a Plataforma nunca exige reescrever o Core.
3. **O Core é cloud-primary.** O estado canônico do pet vive no Core
   (SQLite), não no dispositivo. No MVP não há fallback NVS — se o
   dispositivo perde conectividade, o pet pausa, não diverge.
4. **A Nuvem é trocável.** Provedores de ASR/LLM/TTS são config, não
   código. Trocar Groq por outro provedor não toca no Core nem no
   firmware.

Detalhes da decisão: ADR-018 (Core + contrato), ADR-022 (Nuvem),
ADR-023 (pet vivo), ADR-016 (sem relay).

## Mapa do repositório

| Caminho | O que é | Estado |
|:---|:---|:---|
| `AGENTS.md` | este arquivo | ativo |
| `CONTEXT.md` | glossário ubíquo | ativo, autoritativo |
| `android/` | **Plataforma atual** de testes do Core (Kotlin/Compose) | ativo (ADR-018) |
| `core/` | **Core** TypeScript (Hono + SQLite) | ativo |
| `packages/contract/` | schemas Zod do Batch e Plano de Ações → JSON Schema | ativo |
| `esp32-server/` | Nuvem auto-hospedada (submodule → fork xiaozhi-esp32-server) | ativo |
| `firmware/` | firmware (submodule → fork 78/xiaozhi-esp32, ESP-IDF) | ativo |
| `ota/` | manifestos e chaves de assinatura OTA | ativo |
| `docs/decisions/` | ADRs (imutáveis) | ativo |
| `docs/research/` | pesquisa de solução | ativo |
| `hardware/cores3/` | capacidades e skin do CoreS3 | ativo |
| `hardware/audio/` | BOM/esquema de áudio do WROOM (legado) | legado — GPIOs precisam remap p/ S3 |
| `samples/` | sketches Arduino legados (hello_world, stream_mic_serial) | legado — referência |
| `Justfile` | recipes multi-linguagem (test/build/lint) | ativo |
| `tutorial_raw/` | brutos de tutorial (~300MB) | gitignored |

## Branches = variantes de corpo

Cada variante de corpo do robô vive num branch separado. Você está no
branch `tamagotchi` (a variante atual, em desenvolvimento):

| Branch | Variante | MCU | Status |
|:---|:---|:---|:---|
| `tamagotchi` | pet de bolso (sem servos, com câmera — ADR-017) | ESP32-S3 + PSRAM | ativa |
| `quadrupede` | cão de 4 patas, chassi ESP-HI | ESP32-WROOM-32E-N4 | arquivada |
| `main` | bípede ACEBOTT | ESP32-WROOM-32E-N4 | arquivada |

**Não espere** encontrar neste branch: pinout-quadrupede, tutorial
ACEBOTT, sketch do bípede, 3d-models, datasheet do WROOM, ESP32-CAM.
Esses foram removidos — vivem nos branches arquivados. Se uma referência
parece faltante, provavelmente está noutro branch.

## ADRs são registro histórico imutável

`docs/decisions/` contém os ADRs. **Não reescreva o contexto técnico de
um ADR para refletir o estado atual** — eles registram a decisão da
época. Especificamente:

- ADRs 001, 002, 005, 006, 007 (presentes neste branch) foram escritos
  para o bípede/quadrúpede no WROOM-32E-N4 (sem PSRAM, com relay de
  smartphone). Referências a "WROOM", "`ACB_Biped_Robot`", "4 servos",
  "relay" são intencionais e corretas para a decisão histórica.
- **ADRs 016–023 pertencem à variante Tamagotchi** neste branch: 016
  (sem relay), 017 (câmera), 018 (Core + contrato), 019 (hardware
  CoreS3), 020 (OTA), 021 (firmware xiaozhi), 022 (Nuvem), 023 (pet vivo).
  ADRs 003, 004 e 008–015 existem no branch `quadrupede`.
- Para criar um novo ADR, siga o formato dos existentes (Status / Date /
  Context / Decision / Alternatives Considered / Consequences / Notas).

## Regras requeridas — Firmware (ADR-019, ADR-021)

O firmware é um **fork do `78/xiaozhi-esp32`** (ESP-IDF, board
`m5stack/core-s3`), não Arduino. As regras abaixo herdam o padrão do
xiaozhi e se aplicam ao conteúdo do nosso fork dentro de `firmware/`.

- **MVP push-to-talk por toque no display** (`WAKE_WORD_DISABLED`) até
  treinar a wake word "Felipe" em pt-BR. Não bloqueie a árvore de
  decisão atrás da wake word antes dela existir.
- **Locale pt-BR a criar** em `main/assets/locales/pt-BR/` (cópia do
  `pt-PT` existente, editada). Regenerar `lang_config.h` com
  `python3 scripts/gen_lang.py` e registrar em `main/CMakeLists.txt`.
- **Board**: copiar `main/boards/m5stack/core-s3` para
  `main/boards/m5stack/cores3-felipe` e adaptar. A identidade de board
  afeta OTA — atualize a cadeia config.json→build.py→Kconfig→CMakeLists
  em conjunto.
- **Patches focados.** Cada linha do diff rastreia ao pedido. O core do
  xiaozhi nunca depende de board concreto — dependa de interfaces.
- **Mude de estado via state machine** (`Application::SetDeviceState()`),
  não mutando variáveis soltas. Agende callbacks no event loop
  (`Application::Schedule()`); **nunca bloqueie o event loop** nem as
  tasks de áudio.
- **Caminhos de áudio**: sem queues unbounded, sem alocações grandes em
  paths quentes. Validar input de rede e ownership de cJSON.
- **Não assumir PSRAM/S3** — guardar com Kconfig. As chaves NVS são uma
  API persistente: mudar nome exige migração.
- **Nunca editar arquivos gerados/vendor**: `build/`, `releases/`,
  `managed_components/`, `components/`, `sdkconfig*`, `lang_config.h`.
- **Formate só os arquivos tocados** com `.clang-format`.
- Antes de adicionar código, **leia a implementação mais próxima que já
  existe** e prefira a camada mais estreita que ownership do recurso.

## Regras requeridas — Core (ADR-018, ADR-023)

O Core é um serviço TypeScript (Hono + `@modelcontextprotocol/sdk` +
SQLite) modelado em `@modelcontextprotocol/typescript-sdk/examples/hono/`.
Regras detalhadas em `core/AGENTS.md`. Resumo aqui:

- **Estado cloud-primary.** O estado canônico do pet vive no SQLite do
  Core. No MVP **não há fallback NVS** no dispositivo.
- **18 stats, 17 editáveis + `health` derivado.** `health` nunca é
  editado direto — é calculado a partir dos outros. Decay de stats é
  função pura de timestamp, não de wall-clock com efeitos colaterais.
- **Estágios Filhote→Jovem→Adulto** via XState v5 (transições auditáveis,
  sem morte do pet).
- **Tools expostas via HTTP REST.** O Core é um HTTP server (Hono). Um
  adapter Python interno no xiaozhi-server (`plugins_func/functions/
  pet_tools.py`) registra cada tool como `ToolType.SYSTEM_CTL`, chama o
  Core via HTTP, e usa `conn` para enviar ações ao device. O LLM chama
  via function calling. A Nuvem é um provedor de capacidades, não
  acoplada ao Core.
- Código em inglês, comentários em pt-BR, testes em Vitest.

## Regras requeridas — OTA (ADR-020)

- **esp32FOTA** em modo pull, com um wrapper que **substitui** a classe
  `Ota` nativa do xiaozhi (não estende).
- **RSA-4096**; manifest e binários no **GitHub Releases**.
- **Semver anti-rollback**: o dispositivo recusa firmware com versão
  menor que a atual.
- **app + LittleFS no mesmo round** de atualização.
- A chave **privada nunca entra no repo** (`ota/keys/` privada é
  gitignored); a pública é tracked.

## Regras requeridas — Contrato (`packages/contract/`)

- Schemas Zod para **Batch** (envelope versionado de Triggers) e **Plano
  de Ações** (resposta ordenada do Core).
- Gerar JSON Schema a partir do Zod para o Android consumir via
  `kotlinx.serialization` (ou quicktype). O contrato é a fonte da verdade
  compartilhada entre Core e Plataforma.
- Mudar o contrato = novo ADR se for breaking; bump de versão do Batch
  em qualquer caso.

## Comandos essenciais

O repo é multi-linguagem (TS, Kotlin, ESP-IDF, Python da Nuvem). O
`Justfile` na raiz orquestra tudo; prefira `just <recipe>` aos comandos
diretos quando existir.

| Tarefa | Comando | Onde |
|:---|:---|:---|
| Testes do Core (TS) | `pnpm --filter core test` ou `just core-test` | raiz |
| Lint/format TS | `pnpm biome check .` ou `just lint` | raiz |
| Testes do contrato | `pnpm --filter contract test` | raiz |
| Testes do app Android | `./gradlew test` (unit) / `connectedAndroidTest` | `android/` |
| Build do app | `./gradlew assembleDebug` | `android/` |
| Build do firmware | `source esp-idf/export.sh && python3 scripts/build.py m5stack/cores3-felipe --name felipe` | `firmware/` |
| Regenerar locales | `python3 scripts/gen_lang.py` | `firmware/` |
| Nuvem (local) | `docker compose -f esp32-server/docker-compose.override.yml up` | raiz |
| Verificar links .md | `just check-docs` | raiz |

Se um comando não existe ainda (firmware ESP-IDF não configurado neste
repo, Nuvem sem `docker compose` inicializado), **não finja que rodou**.
Reporte o que executou e o que ainda precisa de setup.

## Verificação por tipo de mudança

Não há CI automatizado. A verificação é por inspeção e comandos locais.
Após cada mudança, execute o que se aplica e **reporte o que verificou e
o que ainda precisa de hardware/setup extra** — um build que passa não é
validação de hardware.

- **Mudou `.md` (docs/ADRs/CONTEXT)**: confira que nenhum link aponta
  para arquivo removido. Patterns de resíduo conhecidos: `tutorial/`,
  `hardware/mcu/`, `hardware/esp32-cam/`, `pinout-quadrupede`,
  `esp32-wroom-32e-n4`.
- **Mudou código TS (core/contract)**: `pnpm biome check` + `pnpm test`
  na package afetada.
- **Mudou código Kotlin (android/)**: `./gradlew test`.
- **Mudou firmware**: build via `scripts/build.py` (exige ESP-IDF
  sourced). Se não tiver ESP-IDF neste ambiente, declare que o build não
  foi executado e liste o que precisaria do CoreS3 físico.
- **Mudou o contrato**: bump de versão do Batch + testes em core e
  android (ambos consomem o schema).
- **Mudou config da Nuvem**: `docker compose config` valida o override;
  não suba containers sem pedir.

## Regras de estilo para agentes

- **Mudanças cirúrgicas.** Cada linha do diff rastreia ao pedido. Não
  reformate código que não tocou; não reordene imports por gosto.
- **Comentários só o porquê**, nunca o que o código já diz, sem
  restating, sem divisores de seção decorativos.
- **Nunca fabrique** caminhos, hashes, resultados de comando ou ADRs.
  Se não rodou, diga que não rodou.
- **Pare quando confuso.** Se a premissa do pedido parece errada,
  discorde e explique; não execute por obediência.
- **Toque só o necessário.** Não crie arquivos de exemplo ou docs que o
  usuário não pediu. Não crie `README.md` ou `*.md` de-documentação
  preventivamente.
- **Prefira acesso direto de chave** a `.get()`/`??` quando a chave
  ausente seria um bug de contrato — surpreção de violação esconde erro.
- **Pergunte quando ambíguo**; prossiga quando o caminho é claro e
  reversível.

## Subsistemas com AGENTS.md próprio

- `android/AGENTS.md` — papel como primeira Plataforma (ADR-018), stack
  Kotlin/Compose, comandos Gradle.
- `core/AGENTS.md` — contrato, estado cloud-primary, 18 stats, HTTP
  endpoints (adapter Python bridgeia ao LLM), XState, SQLite, convenções TS.

Nuvem, OTA, firmware e `packages/contract/` não têm AGENTS.md próprio —
suas regras vivem aqui. Crie um AGENTS.md aninhado só quando o
subsistema acumular regras que mereçam isolamento (padrão xiaozhi).

## Tickets (tracer-bullet slices)

Tickets de implementação vivem em `.scratch/<feature-slug>/issues/`, um
arquivo por ticket nomeado `<NN>-<slug>.md` (numerados de `01` em ordem
de dependência, blockers primeiro). Cada ticket é um **tracer-bullet
vertical slice** — corta todas as camadas (schema, API, UI, tests) e é
demoable por si só. Declara `Blocked by` (tickets que devem completar
antes) e `Status: ready-for-agent`. O template está na skill
`/to-tickets`.

Feature atual: `fase-1-core-android` (Core TS/Bun + xiaozhi-server +
Android, 6 tickets). Fase 2 (CoreS3) inicia após o hardware chegar.

## `tutorial_raw/` é gitignored

Materiais brutos de tutoriais do kit de origem (~300 MB de PDFs/drivers/
instaladores) vivem em `/tutorial_raw/` e são ignorados pelo git. Não
tente commitar esse conteúdo.

## Project Learnings

Seção de auto-melhoria: registre aqui padrões aprendidos que mudam
decisões futuras no projeto. Mantenha enxuto — quando esta seção passar
de ~30 linhas, promova o conteúdo a um ADR ou a uma regra na seção
pertinente e limpe aqui.

<!-- Exemplo de entrada (apague ao adicionar a primeira real):
- 2026-09-01: [firmware] O locale pt-PT do xiaozhi usa "você"; o pt-BR
  do Tamagotchi deve usar "você" também (Sobrinho espera tratamento
  informal). Não trocar por "tu".
-->
