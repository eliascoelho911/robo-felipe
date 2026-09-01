# android/AGENTS.md

Regras do app Android — a **primeira Plataforma** do Robô Felipe. Para
regras globais e o contrato arquitetural, leia o `AGENTS.md` da raiz.

## Papel

O app é a **Plataforma atual de testes do Core** (ADR-018): detecta
Triggers, envia Batches ao Core e executa o Plano de Ações que volta.
Mais tarde o host passa a ser o CoreS3, **sem reescrever o Core** — por
isso o app deve falar só o contrato, não implementar comportamento.

- **Não é mais o relay** (ADR-016 revogou o relay para o Tamagotchi). O
  histórico do app como relay do bípede/quadrúpede é legado.
- **Comportamento nunca mora aqui.** Lógica de pet, decay de stats,
  decisão do que dizer ficam no Core. O app só: (1) captura Trigger,
  (2) empacota Batch, (3) envia, (4) recebe Plano de Ações, (5) executa
  Ações (`speak`, `express_emotion`, etc.).
- **Fase 1 (protótipo de laboratório)**: o app é o **device proxy** —
  fala WSS+Opus com o `xiaozhi-server` (protocolo xiaozhi), envia Batch
  via HTTPS ao Core para triggers não-vozeados, e executa o Plano de
  Ações (TTS via EdgeTTS no servidor + animações de pet na UI). O
  código de áudio/WSS é temporário (substituído pelo CoreS3 na Fase 2),
  mas o Core e o xiaozhi-server são permanentes (zero throwaway).

## Stack

- Gradle wrapper **9.1.0**, Android Gradle Plugin **9.0.1**
- Kotlin **2.3.20**, Compose BOM **2026.03.01**
- Navigation3 (`androidx.navigation3`), Lifecycle ViewModel, coroutines
  1.10.2
- Testes: JUnit 4.13.2, `androidx.test`, coroutines-test

Versões vivem em `gradle/libs.versions.toml` (version catalog). Não
passe versões hardcoded no `build.gradle.kts`.

## Estrutura

Módulo único `:app`, package `com.example.robofelipe`:

```
app/src/
├── main/java/com/example/robofelipe/
│   ├── MainActivity.kt          # entry point, host do Compose
│   ├── audio/                   # OpusEncoder, OpusDecoder, StreamPlayer
│   ├── network/                 # WebSocketManager (WSS xiaozhi), BatchClient (HTTPS Core)
│   ├── data/                    # tipos do contrato (kotlinx.serialization via JSON Schema)
│   ├── ui/pet/                  # PetFace, StatBars, animações (Compose)
│   └── theme/                   # Color, Type, Theme
├── test/        # unit tests
└── androidTest/ # instrumentados
```

Padrão **MVVM**: `ViewModel` expõe `StateFlow`, `@Composable` consome.
O `WebSocketManager` (WSS+Opus com o xiaozhi-server) e o `BatchClient`
(HTTPS com o Core) são as fronteiras de dados. Não vaze lógica de
domínio para o Composable — ele só renderiza estado.

**Referência de implementação**: `xiaoniu/xiaozhi-ai-android` (48★,
Kotlin+Compose, OkHttp+Gson) tem `audio/` (OpusEncoder/Decoder/
StreamPlayer) e `network/WebSocketManager.kt` self-contained — usar
como referência para portar, não forkar.

## Contrato com o Core

O app consome o **contrato Batch/Plano de Ações** definido em
`packages/contract/` (schemas Zod → JSON Schema) **apenas para triggers
n-vozeados e estado do pet** — o app envia Batch ao Core via HTTPS e
recebe o Plano de Ações de volta. Gere os tipos Kotlin via
`kotlinx.serialization` (ou quicktype) a partir do JSON Schema publicado
pelo contrato — **não duplique** o schema à mão no app.

Para **voz**, o app fala o **protocolo WSS do xiaozhi** (handshake hello,
listen start/stop, Opus binary 16kHz mono 60ms, TTS de volta) com o
`xiaozhi-server` na porta 8000 — não usa o contrato Batch/Plano. O
`xiaozhi-server` orquestra ASR→LLM→TTS; o adapter Python interno
bridgeia as tools do pet ao Core.

## Comandos

| Tarefa | Comando |
|:---|:---|
| Testes unitários | `./gradlew test` |
| Testes instrumentados | `./gradlew connectedAndroidTest` (exige device/emulador) |
| Build debug | `./gradlew assembleDebug` |
| Lint | `./gradlew lint` |

Rode sempre dentro de `android/`. Se não houver SDK/`local.properties`
configurado neste ambiente, declare que o build não rodou.

## Convenções

- **Código e identificadores em inglês**; **comentários em pt-BR** (só o
  porquê). Docs e commits em pt-BR imperativo.
- Strings de UI voltadas ao Sobrinho em **pt-BR** (`strings.xml`); não
  hardcode texto visível em Composables.
- Mudanças cirúrgicas: cada linha do diff rastreia ao pedido. Não
  reformate arquivos não tocados.
- Não adicione bibliotecas sem checar se já existe equivalente no version
  catalog.
