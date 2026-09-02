# Spec 07: CI com GitHub Actions no monorepo

**Ticket:** [07 — CI com GitHub Actions no monorepo](../tickets/07-ci-github-actions-monorepo.md)
**Status:** ready-for-agent
**Blocked by:** (nenhum — infraestrutura independente das features da fase-1)

## Problem Statement

O AGENTS.md declarava "Não há CI automatizado. A verificação é por inspeção
e comandos locais". Num repo onde a maior parte do código é escrita por
agentes, isso significa confiar no autorrelato de cada sessão: nada garante
que um PR que mexe no contrato não quebre Core e Android ao mesmo tempo,
que o `manifest.json` de OTA aponte para um asset que existe, ou que a
política de assinatura RSA do ADR-020 seja executada na ordem certa a cada
release. O processo de release OTA era inteiramente manual (openssl na
máquina do dev, upload de assets, edição de manifest) — lento e fácil de
errar.

## Solution

GitHub Actions no repo principal com três artefatos:

1. **`ci.yml`** — 4 jobs paralelos por stack (ts, android, docker,
   ota-manifest) em todo push para `tamagotchi` e PR.
2. **`release.yml`** — pipeline de release OTA acionada por tag `v*.*.*`:
   build do firmware no container ESP-IDF, checagem de versão, assinatura
   RSA-4096, GitHub Release com assets, atualização do manifest.
3. **`dependabot.yml`** — atualizações semanais agrupadas para
   github-actions, npm (raiz + core + contract) e gradle.

A build de firmware em PRs comuns fica de fora — a CI do submódulo
`firmware/` já a cobre.

## User Stories

1. As a desenvolvedor, I want todo PR rodar `biome ci` e os testes
   vitest do core e do contract, so that regressões TS não cheguem ao
   branch default.
2. As a desenvolvedor, I want todo PR rodar `./gradlew lint test
   assembleDebug` (incluindo o build NDK do Opus), so that o app Android
   compile e teste em cada mudança.
3. As a desenvolvedor, I want todo PR validar o `docker compose config`
   da Nuvem, so que um override quebrado seja pego antes do deploy local.
4. As a desenvolvedor, I want todo PR validar o `ota/manifest/manifest.json`
   contra regras (type, semver, URLs de release), so that o pet nunca leia
   um manifest inválido.
5. As a desenvolvedor, I want `git push` de uma tag `vX.Y.Z` disparar a
   build do firmware cores3-felipe no container oficial do ESP-IDF,
   so que a release não dependa do meu ambiente local.
6. As a desenvolvedor, I want a assinatura RSA-4096 feita com a chave
   vinda de um GitHub secret (nunca no repo), so que a política do
   ADR-020 seja executada sem exposição da chave privada.
7. As a desenvolvedor, I want a release publicar `firmware.img` e
   `filesystem.img` como assets e atualizar o manifest no branch default
   via commit validado, so que o dispositivo encontre a nova versão no
   próximo check.
8. As a desenvolvedor, I want o release verificar que a versão do app
   no binário bate com a tag, so que manifest e firmware nunca divergam.
9. As a desenvolvedor, I want Dependabot abrir PRs semanais agrupados
   para Actions, npm e Gradle, so que dependências não apodreçam.

## Implementation Decisions

### Jobs do `ci.yml` (todos em ubuntu-24.04, `contents: read`)

| Job | Passos |
|:--|:--|
| `ts` | `pnpm/action-setup@v4` (lê `packageManager` do package.json) → `setup-node@v4` node 22 + cache pnpm → `pnpm install --frozen-lockfile` → `pnpm exec biome ci .` → `pnpm -r test` → `pnpm --filter contract gen` |
| `android` | `setup-java@v4` temurin 17 → `setup-gradle@v4` → `./gradlew lint test assembleDebug --stacktrace` em `android/` |
| `docker` | init submódulo `esp32-server/upstream` → `docker compose --project-directory esp32-server -f <base> -f <override> config > /dev/null` |
| `ota-manifest` | `python3 ota/manifest/validate_manifest.py` |

- **`biome ci`** (não `check`): modo CI falha em qualquer issue sem
  tentar corrigir — recomendado pela doc do Biome.
- **`--frozen-lockfile`**: PR com lockfile dessincronizado falha cedo.
- **`contract gen` valida execução, não diff**: os schemas gerados são
  gitignored; o job garante que a geração roda sem erro.
- **Sem path filters no MVP**: repo pequeno, jobs rápidos, sinal completo
  a cada PR. Reversível se os tempos crescerem.

### `release.yml` (tag `v*.*.*`, `contents: write`)

- **Job `build`** em `container: espressif/idf:v6.0.2`: checkout com
  submódulo `firmware` → `python3 scripts/build.py m5stack/cores3-felipe
  --name cores3-felipe --language pt-BR` → leitura do app descriptor
  (magic `0xABCD5432`, version em `0x10–0x30`) comparada com a tag →
  assinatura de `build/firmware.bin` e `build/generated_assets.bin`
  (`openssl dgst -sign priv_key.pem -sha256`, prepend de 512 B) →
  upload-artifact. O assets binário do LittleFS é o
  `generated_assets.bin` (a board cores3-felipe não usa URL de assets
  custom; o CMake gera o default).
- **Job `release`**: `gh release create` com `firmware.img` +
  `filesystem.img` → `python3 ota/manifest/update_manifest.py <tag>` →
  commit bot (`github-actions[bot]`) → `git push origin HEAD:tamagotchi`.
- **Check de versão falha de propósito** enquanto a fork não definir
  `project VERSION` no CMakeLists — a primeira tag real exige alinhar
  a versão do firmware com a tag.

### Scripts de manifest (stdlib only, sem dependências)

- `ota/manifest/validate_manifest.py` — regras: chaves obrigatórias
  (`type, version, host, port, bin, littlefs`), `type ==
  "robo-felipe-tamagotchi"`, semver `X.Y.Z`, `host == github.com`,
  `port == 443`, `bin`/`littlefs` com prefixo
  `/{repo}/releases/download/v{version}/` e sufixos `firmware.img`/
  `filesystem.img`, `bin != littlefs`. Slug do repo lido de
  `git remote get-url origin`.
- `ota/manifest/update_manifest.py` — seta version/bin/littlefs para a
  tag e **valida antes de escrever**: um manifest inválido nunca é
  commitado.

### Dependabot

Três entradas semanais, grupo único (`patterns: ["*"]`) em cada:
`github-actions` `/`; `npm` com `directories: ["/", "/core",
"/packages/contract"]`; `gradle` `/android`.

### Secret único requerido

`OTA_RSA_PRIVATE_KEY` — chave privada RSA-4096 (gerada conforme política
do ADR-020) nas settings do repo. Sem ele, `release.yml` falha no step
de assinatura; `ci.yml` não depende de secrets.

## Testing Decisions

### O que faz um bom teste

CI é a própria ferramenta de teste; o que se valida é que os workflows
parseiam, que os scripts de manifest comportam-se corretamente, e que o
manifest atual do repo passa na validação.

### Módulos testados

- **`validate_manifest.py`**: o manifest vigente (v0.1.0) passa;
  mutações que quebram cada regra (type errado, semver inválido, URL
  de outro repo, bin == littlefs) produzem erro — coberto por execução
  manual durante o desenvolvimento; pytest ficaria overkill para stdlib
  script de CI.
- **`update_manifest.py`**: round-trip local (update para tag fake →
  valida → restaura) executado durante o desenvolvimento.
- **YAML dos 3 workflows**: parse com `yaml.safe_load`.

### Prior art

- CI do submódulo `firmware/.github/workflows/build.yml` (matrix ESP-IDF,
  container `espressif/idf`) — reusada como referência para o job `build`
  do release.

## Out of Scope

- Build de firmware em PRs (CI do submódulo cobre; ver ADR-024).
- Instrumented tests Android (`connectedAndroidTest` exige
  emulador/device).
- Self-test pós-OTA no dispositivo — Spec 08.
- Deploy/hosting do Core (fora do escopo da fase-1).
- Canary/rollout por dispositivo no manifest (upgrade futuro, ADR-020).

## Further Notes

- **Referências:** ADR-024 (decisão), ADR-020 (política de assinatura e
  manifest automatizada aqui), ADR-018 (stacks do monorepo).
- **Depois de implementar**, o AGENTS.md deixa de dizer "Não há CI
  automatizado" — a seção Verificação aponta para o ADR-024 e os
  workflows.
