# AGENTS.md

Instruções para agentes OpenCode no repositório **robo-felipe**. Este
arquivo é o ponto de entrada; subsistemas com regras próprias têm
`AGENTS.md` aninhados (padrão xiaozhi).

## Propósito

Robô Felipe é um robô conversacional de voz em **português (pt-BR)**,
variante **Tamagotchi** — um pet de bolso para o **Sobrinho** (8 anos),
autocontido num M5Stack CoreS3 (ESP32-S3 com PSRAM), que fala direto com
a nuvem sem relay de smartphone. Os ADRs 016–024 governam a variante
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
- **Plataforma**, **Trigger**, **Ação**, **Nuvem**, **Relay** têm
  definições precisas — consulte antes de usar em ADRs ou código.

Não invente variantes de corpo: as três existentes são **Bípede**,
**Quadrúpede** e **Tamagotchi**.

## Arquitetura — o comportamento mora na Nuvem

O Robô Felipe separa **comportamento** (o que o pet faz) de **encarnação**
(onde ele roda). O comportamento mora no **xiaozhi-esp32-server** (fork
nosso — a Nuvem), que também orquestra a voz; a encarnação é o **device**
(firmware do CoreS3), que detecta Triggers e executa as Ações que a
Nuvem envia.

```
Device (CoreS3, firmware xiaozhi) ──WSS/TLS──► Nuvem (xiaozhi-esp32-server, fork)
   detecta Triggers,                             ├── voz: ASR → LLM → TTS pt-BR
   executa Ações                                 ├── comportamento do pet (Python nativo):
   (TTS, animações LVGL)                         │   estado (18 stats), decay, estágios,
                                                 │   tools do LLM, handlers de trigger
                                                 └── provedores plugáveis (Groq/OpenAI/Edge)
```

**Regras arquiteturais não-negociáveis:**

1. **Comportamento nunca mora no firmware.** O device é Plataforma:
   detecta Triggers e executa Ações, nada mais. Lógica de pet, decay de
   stats, decisão do que dizer — tudo no servidor.
2. **O comportamento é código Python nativo do fork da Nuvem** — tools
   do pet (`plugins_func`, `ToolType.SYSTEM_CTL`, com `conn`) para o
   caminho vozeado e handlers de mensagem WSS para triggers
   não-vozeados. Sem serviço TypeScript separado, sem contrato
   Batch→Plano de Ações.
3. **Estado cloud-primary no servidor.** O estado canônico do pet vive
   na persistência do servidor, não no dispositivo. No MVP não há
   fallback NVS — se o device perde conectividade, o pet pausa, não
   diverge.
4. **Provedores de ASR/LLM/TTS são trocáveis** — config, não código.
   Trocar Groq por outro provedor não toca no firmware nem no
   comportamento. O comportamento é código nosso: isole-o em módulo
   próprio dentro do fork, sem espalhar lógica pelo core upstream.

Detalhes da decisão: ADR-018 (comportamento no xiaozhi-server),
ADR-022 (Nuvem), ADR-023 (pet vivo), ADR-016 (sem relay).

## Mapa do repositório

| Caminho | O que é | Estado |
|:---|:---|:---|
| `AGENTS.md` | este arquivo | ativo |
| `CONTEXT.md` | glossário ubíquo | ativo, autoritativo |
| `.github/` | CI (ci.yml), release OTA (release.yml), Dependabot | ativo (ADR-024) |
| `esp32-server/` | **Nuvem** auto-hospedada — voz + comportamento do pet (submodule → fork xiaozhi-esp32-server) | ativo |
| `firmware/` | firmware (submodule → fork 78/xiaozhi-esp32, ESP-IDF) | ativo |
| `ota/` | manifestos e chaves de assinatura OTA | ativo |
| `docs/decisions/` | ADRs | ativo |
| `docs/research/` | pesquisa de solução | ativo |
| `hardware/cores3/` | capacidades e skin do CoreS3 | ativo |
| `hardware/audio/` | BOM/esquema de áudio do WROOM (legado) | legado — GPIOs precisam remap p/ S3 |
| `samples/` | sketches Arduino legados (hello_world, stream_mic_serial) | legado — referência |
| `Justfile` | recipes (build/check por stack) | ativo |
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

## ADRs — decisões vigentes e histórico das variantes arquivadas

`docs/decisions/` contém os ADRs, no formato (Status / Date / Context /
Decision / Alternatives Considered / Consequences / Notas). Regras:

- ADRs 001, 002, 005, 006, 007 (presentes neste branch) foram escritos
  para o bípede/quadrúpede no WROOM-32E-N4 (sem PSRAM, com relay de
  smartphone). Referências a "WROOM", "`ACB_Biped_Robot`", "4 servos",
  "relay" são intencionais e corretas para a decisão histórica —
  **não reescreva** esses ADRs.
- **ADRs 016–024 pertencem à variante Tamagotchi** neste branch: 016
  (sem relay), 017 (câmera), 018 (comportamento no xiaozhi-server), 019
  (hardware CoreS3), 020 (OTA), 021 (firmware xiaozhi), 022 (Nuvem),
  023 (pet vivo), 024 (CI GitHub Actions). ADRs 003, 004 e 008–015
  existem no branch `quadrupede`.
- **ADRs ativos refletem a decisão vigente.** Quando o dono muda a
  arquitetura, ele pode mandar ajustar o ADR no lugar — nesse caso,
  reescreva-o por completo (contexto, decisão, alternativas,
  consequências), anote a reescrita no campo Date e deixe o histórico
  no git. Sem direção explícita do dono, trate um ADR como decisão
  vigente e discorde de mudanças que conflitem com ele.

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

## Regras requeridas — Nuvem / comportamento do pet (ADR-018, ADR-022, ADR-023)

O comportamento do pet é **código Python nativo do fork da Nuvem**
(`esp32-server/upstream`, fork de `xinnan-tech/xiaozhi-esp32-server`).
Regras:

- **Módulo isolado.** O comportamento (estado, stats, decay, estágios,
  tools, handlers de trigger) vive num módulo próprio dentro do fork,
  sem espalhar lógica pelo core upstream — o upstream Python é
  movimentado e o merge precisa ser barato.
- **Estado cloud-primary no servidor.** O estado canônico do pet vive
  na persistência do servidor. No MVP **não há fallback NVS** no
  dispositivo.
- **18 stats, 17 editáveis + `health` derivado.** `health` nunca é
  editado direto — é calculado a partir dos outros. Decay de stats é
  função pura de timestamp (on-demand, sem cron), não de wall-clock com
  efeitos colaterais.
- **Estágios Filhote→Jovem→Adulto** com transições determinísticas e
  auditáveis, sem morte do pet.
- **Tools do pet**: `plugins_func`, `ToolType.SYSTEM_CTL`, recebem
  `conn`; mutam o estado localmente e enviam `pet_action` JSON ao
  device via `conn`; retornam `Action.REQLLM` para o LLM compor a
  resposta. O LLM chama via function calling.
- **Triggers não-vozeados** (sacudida, botão, proximidade, RTC wake)
  chegam como mensagens no protocolo WSS do xiaozhi e são tratados por
  handler interno do fork — sem passar pelo LLM.
- Código em inglês, comentários em pt-BR, testes em pytest no
  submódulo.

## Regras requeridas — OTA (ADR-020)

- **esp32FOTA** em modo pull, com um wrapper que **substitui** a classe
  `Ota` nativa do xiaozhi (não estende).
- **RSA-4096**; manifest e binários no **GitHub Releases**.
- **Semver anti-rollback**: o dispositivo recusa firmware com versão
  menor que a atual.
- **app + LittleFS no mesmo round** de atualização.
- A chave **privada nunca entra no repo** (`ota/keys/` privada é
  gitignored); a pública é tracked.

## Comandos essenciais

O repo é multi-linguagem (ESP-IDF do firmware, Python da Nuvem). O
`Justfile` na raiz orquestra tudo; prefira `just <recipe>` aos comandos
diretos quando existir.

| Tarefa | Comando | Onde |
|:---|:---|:---|
| Build do firmware | `just firmware-build` (exige ESP-IDF sourced) | raiz |
| Flash no CoreS3 | `just firmware-flash` | raiz |
| Regenerar locales | `just firmware-locales` | raiz |
| Nuvem (local) | `just esp32-server-up` / `just esp32-server-check` | raiz |
| Validar manifest OTA | `just ota-manifest-check` | raiz |

Se um comando não existe ainda (firmware ESP-IDF não configurado neste
repo, Nuvem sem `docker compose` inicializado), **não finja que rodou**.
Reporte o que executou e o que ainda precisa de setup.

## Verificação por tipo de mudança

A CI (ADR-024) roda em todo push/PR: jobs `docker`, `ota-manifest` em
`.github/workflows/ci.yml`; releases OTA em tags via
`.github/workflows/release.yml`. A CI não substitui a verificação local
— rode os comandos abaixo antes de push. Após cada mudança, execute o
que se aplica e **reporte o que verificou e o que ainda precisa de
hardware/setup extra** — um build que passa não é validação de hardware.

- **Mudou `.md` (docs/ADRs/CONTEXT)**: confira que nenhum link aponta
  para arquivo removido. Patterns de resíduo conhecidos: `tutorial/`,
  `hardware/mcu/`, `hardware/esp32-cam/`, `pinout-quadrupede`,
  `esp32-wroom-32e-n4`, `android/`, `core/`, `packages/contract/`,
  `018-tamagotchi-comportamento-mora-no-core-typescript`.
- **Mudou o fork da Nuvem** (`esp32-server/upstream`): rode o pytest do
  submódulo que cobre o módulo tocado; se o compose mudou,
  `docker compose config` valida o merge — não suba containers sem
  pedir.
- **Mudou firmware**: build via `scripts/build.py` (exige ESP-IDF
  sourced). Se não tiver ESP-IDF neste ambiente, declare que o build não
  foi executado e liste o que precisaria do CoreS3 físico.
- **Mudou config da Nuvem**: `just esp32-server-check` valida o merge;
  não suba containers sem pedir.
- **Mudou o manifest OTA** (`ota/manifest/`): `just ota-manifest-check`.
  Release OTA: tag `vX.Y.Z` dispara o `release.yml` (exige o secret
  `OTA_RSA_PRIVATE_KEY`).

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
  ausente seria um bug — surpreção de violação esconde erro.
- **Pergunte quando ambíguo**; prossiga quando o caminho é claro e
  reversível.

## Subsistemas com AGENTS.md próprio

Nenhum subsistema tem `AGENTS.md` aninhado hoje — Nuvem (`esp32-server/`),
OTA, firmware e o restante seguem as regras deste arquivo. Crie um
AGENTS.md aninhado só quando o subsistema acumular regras que mereçam
isolamento (padrão xiaozhi).

## Specs (specifications)

Specs de implementação vivem em `docs/specs/`, um arquivo por spec
nomeado `<NN>-<slug>.md` (numerados de `01` na ordem de dependência).
Cada spec descreve o **quê** e o **porquê** de uma feature — Problem
Statement, Solution, User Stories, Implementation Decisions, Testing
Decisions, Out of Scope, Further Notes — sem prescrever file paths ou
code snippets. O template está na skill `/to-spec`.

Cada spec referencia seu ticket correspondente em `docs/tickets/` pelo
número. Specs são a fonte da verdade para o agente que implementa o
ticket.

### Status de specs e tickets

Cada spec e cada ticket tem um campo `**Status:**`. Mantenha-o
atualizado conforme a implementação progride — um status desatualizado
é pior que nenhum. Status disponíveis:

| Status | Significado |
|:---|:---|
| `draft` | em elaboração, ainda não pronto para implementação |
| `ready-for-agent` | pronto para um agente implementar |
| `in-progress` | implementação em andamento |
| `in-review` | PR aberto, aguardando review |
| `done` | implementado, verificado e mergeado |

Transições típicas: ao pegar um ticket `ready-for-agent`, mova para
`in-progress`; ao abrir PR, para `in-review` (anote o número entre
parênteses, ex.: `in-review (PR #3)`); ao mergear e verificar, para
`done`. Anote bloqueios externos entre parênteses, ex.:
`ready-for-agent (aguarda hardware CoreS3 — Fase 2)`. O status do
ticket e da spec correspondente devem refletir a mesma fase da
implementação.

## Tickets (tracer-bullet slices)

Tickets de implementação vivem em `docs/tickets/`, um arquivo por ticket
nomeado `<NN>-<slug>.md` (numerados de `01` em ordem de dependência,
blockers primeiro). Cada ticket é um **tracer-bullet vertical slice** —
corta todas as camadas (schema, API, UI, tests) e é demoable por si só.
Declara `Blocked by` (tickets que devem completar antes) e
`Status: ready-for-agent`. O template está na skill `/to-tickets`.

Feature atual: **comportamento do pet no xiaozhi-server** (ADRs
018/022/023). Specs ativas: 03 (config da Nuvem), 07 (CI GitHub
Actions, ADR-024) e 08 (self-test pós-OTA — aguarda o hardware CoreS3).
As specs/tickets 01, 02, 04, 05 e 06 (contrato, Core, adapter Python de
ponte, Android) foram removidas junto com o código que descreviam.

## `tutorial_raw/` é gitignored

Materiais brutos de tutoriais do kit de origem (~300 MB de PDFs/drivers/
instaladores) vivem em `/tutorial_raw/` e são ignorados pelo git. Não
tente commitar esse conteúdo.

## Project Learnings

Seção de auto-melhoria: registre aqui padrões aprendidos que mudam
decisões futuras no projeto. Mantenha enxuto — quando esta seção passar
de ~30 linhas, promova o conteúdo a um ADR ou a uma regra na seção
pertinente e limpe aqui.

- 2026-09-14: [esp32-server] O `prompt_manager.py` envolve o prompt no
  template `agent-base-prompt.txt` (upstream, centrado em zh) e injeta
  `{{language}}` a partir de `config.TTS.<provider>.language` — se a
  chave não existir, defaulta para **中文** e o LLM responde em chinês;
  a voz pt-BR (Francisca) então falha com "No audio was received"
  (Edge TTS não sintetiza caracteres chineses). Fix: `language:` no
  bloco TTS do `.config.yaml`. O template upstream é zh-cêntrico — um
  template pt-BR próprio é melhoria futura.
