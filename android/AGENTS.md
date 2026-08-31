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
  Ações (`falar`, `expressar emoção`, etc.).

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
│   ├── Navigation.kt            # grafo de navegação (Navigation3)
│   ├── NavigationKeys.kt
│   ├── data/                    # RobotCommand, DataRepository
│   ├── theme/                   # Color, Type, Theme
│   └── ui/main/                 # MainScreen + MainScreenViewModel (MVVM)
├── test/        # unit (MainScreenViewModelTest)
└── androidTest/ # instrumentado (MainScreenTest)
```

Padrão **MVVM**: `ViewModel` expõe `StateFlow`, `@Composable` consome,
`DataRepository` é a fronteira de dados. Não vaze lógica de domínio para
o Composable — ele só renderiza estado.

## Contrato com o Core

O app consome o **contrato Batch/Plano de Ações** definido em
`packages/contract/` (schemas Zod → JSON Schema). Gere os tipos Kotlin
via `kotlinx.serialization` (ou quicktype) a partir do JSON Schema
publicado pelo contrato — **não duplique** o schema à mão no app. Se o
contrato mudar, regenere os tipos e ajuste o app.

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
