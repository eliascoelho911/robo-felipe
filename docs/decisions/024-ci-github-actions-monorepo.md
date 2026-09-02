# ADR-024: CI com GitHub Actions no monorepo — jobs por stack, release OTA assinada e Dependabot

## Status

Accepted

## Date

2026-09-01

## Context

O repositório `robo-felipe` é um monorepo multi-linguagem (TypeScript, Kotlin, ESP-IDF C/C++, Python/Docker) cujo [AGENTS.md](../../AGENTS.md) até aqui declarava: **"Não há CI automatizado. A verificação é por inspeção e comandos locais."** Cada mudança dependia do desenvolvedor (humano ou agente) rodar manualmente `pnpm biome check`, `pnpm test`, `./gradlew test`, `docker compose config` e `just check-docs` — e reportar honestamente o que rodou. Esse modelo quebra conforme o número de agentes e sessões cresce: nada garante que um PR que mexe no contrato não quebre o Core e o Android ao mesmo tempo, ou que um `manifest.json` de OTA aponte para um asset inexistente.

Fatos que moldam a decisão:

1. **O firmware já tem CI própria.** O submódulo `firmware/` (fork de `78/xiaozhi-esp32`) tem `firmware/.github/workflows/build.yml` com matrix de builds no container `espressif/idf:v6.0.2`. Duplicá-la no repo principal seria custo sem benefício.
2. **O ADR-020 estabelece OTA pull com assinatura RSA-4096**, manifest no branch default e binários no GitHub Releases — mas a "política de build" da época era manual (`openssl dgst -sign ...` na máquina do desenvolvedor). Sem automação, cada release depende de um humano guardar a chave privada com segurança e executar os passos na ordem certa.
3. **O repo é privado e pequeno** (1 dev + agentes), o custo de runner não é restrição, e o GitHub Actions é nativo ao `eliascoelho911/robo-felipe` já hospedado no GitHub.

### Pesquisa

Consultas Context7 (2026-09-01) confirmaram os padrões de cada ferramenta:

- **Biome** recomenda `biome ci` (modo CI: falha em qualquer issue, sem tentar corrigir) em vez de `biome check .`.
- **pnpm** em CI: `pnpm install --frozen-lockfile` (falha se o lockfile dessincronizar) + cache da store via `pnpm/action-setup@v4` e `actions/setup-node@v4` com `cache: pnpm`.
- **Dependabot** suporta `directories` (lista com glob) para monorepos npm, e `groups` com `patterns: ["*"]` para agrupar PRs e reduzir ruído.

## Decision

**Adotar GitHub Actions no repo principal com um workflow `ci.yml` de jobs paralelos por stack (TS, Android, Docker, manifest OTA), um workflow `release.yml` acionado por tag semver que builda, assina com RSA-4096 e publica a release OTA, e Dependabot semanal para GitHub Actions, npm e Gradle. O firmware continua com CI própria no submódulo — sem job de build de firmware no CI do repo principal.**

### Workflow `ci.yml` — push em `tamagotchi` + PRs

`permissions: contents: read` no topo (princípio do menor privilégio). Quatro jobs paralelos em `ubuntu-24.04`, cada um só roda o que é da sua stack:

| Job | O que valida |
|:--|:--|
| `ts` | `pnpm install --frozen-lockfile` → `biome ci .` → `pnpm -r test` (core + contract) → `pnpm --filter contract gen` (valida que a geração de JSON Schema executa; os schemas são gitignored, então não há diff a verificar) |
| `android` | `./gradlew lint test assembleDebug` (inclui build nativo NDK/CMake do Opus; JDK 17 temurin) |
| `docker` | `git submodule update --init esp32-server/upstream` → `docker compose config` (valida o override da Nuvem sem subir container) |
| `ota-manifest` | `python3 ota/manifest/validate_manifest.py` |

A detecção de stack por job (path filters) foi deliberadamente **não** adotada no MVP: o repo é pequeno, os jobs são rápidos, e rodar tudo sempre dá sinal de integridade total a cada PR.

### Workflow `release.yml` — tag `v*.*.*`

Dois jobs:

1. **`build`** (container `espressif/idf:v6.0.2`, igual ao CI do firmware): checkout com submódulo `firmware` → `python3 scripts/build.py m5stack/cores3-felipe --name cores3-felipe --language pt-BR` → checa que a versão do app descriptor (magic `0xABCD5432`, offset `0x10–0x30`) bate com a tag → assina `firmware.bin` e `generated_assets.bin` com o secret `OTA_RSA_PRIVATE_KEY` (`openssl dgst -sign ... -sha256`, prepend de 512 B, conforme política do ADR-020) → upload-artifact. O check de versão falha por padrão até a fork definir `project VERSION` — intencional, força o alinhamento antes da primeira release real.
2. **`release`** (needs build, `contents: write`): `gh release create` com `firmware.img` + `filesystem.img` → `python3 ota/manifest/update_manifest.py <tag>` → commit bot e push para `tamagotchi`. O `update_manifest.py` valida o manifest resultante **antes** de escrever — um manifest inválido nunca é commitado.

A chave privada RSA-4096 vive só em GitHub secret (`OTA_RSA_PRIVATE_KEY`); nunca toca o repo. A pública permanece tracked em `ota/keys/`.

### Scripts de manifest (`ota/manifest/`)

- **`validate_manifest.py`** (stdlib only): chaves obrigatórias, `type == "robo-felipe-tamagotchi"`, semver `X.Y.Z`, `host github.com`, `port 443`, `bin`/`littlefs` apontando para `/{repo}/releases/download/v{version}/` com sufixos `firmware.img`/`filesystem.img`. O slug do repo é lido de `git remote get-url origin` — o script não contém o nome do repo hardcoded.
- **`update_manifest.py`**: seta `version`/`bin`/`littlefs` para uma tag e valida antes de escrever. Usado pelo `release.yml`; também serve localmente.

### Dependabot

Três entradas semanais, cada uma com um único grupo (`patterns: ["*"]`) para reduzir ruído: `github-actions` em `/`, `npm` com `directories: ["/", "/core", "/packages/contract"]`, `gradle` em `/android`.

### Firmware fica no submódulo

A CI do repo principal **não** builda firmware em pushes/PRs comuns. O submódulo `firmware/` tem pipeline própria com matrix de boards, e o custo de inicializar submódulos + container IDF em cada PR não se paga. A build de firmware só acontece no `release.yml`, onde é o produto.

## Alternatives Considered

### Continuar sem CI (verificação manual por agente)

- **Prós:** zero custo de setup; o fluxo atual funciona para 1 dev.
- **Contras:** nada garante que um agente rode (ou rode tudo) o que o AGENTS.md pede; regressões no contrato ou no manifest OTA só aparecem no dispositivo; release OTA manual tem passos de assinatura fáceis de errar.
- **Rejeitada:** a produtividade com agentes multiplica o volume de PRs; guardrails automáticos são exatamente o que escala nesse modelo.

### CI de firmware no repo principal (job ESP-IDF em cada PR)

- **Prós:** sinal de build do firmware em PRs que tocam `firmware/` ou `ota/`.
- **Contras:** inicializar submódulo + container `espressif/idf` custa ~10 min por PR; a CI do submódulo já cobre a build de boards com `--select-changed`; PRs do repo principal raramente tocam firmware.
- **Rejeitada:** duplicação de pipeline e custo sem sinal novo. A build volta no `release.yml`, onde é necessária.

### Path filters por job (rodar só o que mudou)

- **Prós:** economiza minutos de runner.
- **Contras:** PR que mexe em `packages/contract/` precisa validar Core E Android; filtros mal calibrados escondem quebras; o repo é pequeno e os jobs são rápidos.
- **Rejeitada para o MVP:** rodar tudo sempre dá sinal completo. Pode ser revisitado se os tempos crescerem (ex.: NDK build lento incomodando).

### CI externa (Drone, GitLab CI, Woodpecker)

- **Prós:** runners self-hosted sem limite de minutos.
- **Contras:** infra extra a manter para um repo que já vive no GitHub (Releases são parte do fluxo OTA do ADR-020); o custo do Actions private (3000 min/mês no plano free) é irrisório para este volume.
- **Rejeitada:** GitHub Actions é nativo ao onde o repo e as Releases já vivem.

## Consequences

### Positivas

- **Guardrails automáticos em todo PR** — lint, testes e build das 4 stacks rodam sem depender da disciplina do agente; o AGENTS.md deixa de confiar em autorrelato.
- **Release OTA reprodutível e segura** — tag → build → assinatura RSA-4096 com chave só em secret → Release com assets → manifest atualizado e validado. Elimina a política manual do ADR-020.
- **Manifest OTA validado continuamente** — `validate_manifest.py` em cada PR impede que um manifest apontando para asset/versão errada chegue ao branch default (de onde o pet lê).
- **Dependabot agrupado** — Actions, npm e Gradle atualizadas em PRs semanais de baixo ruído.
- **Contrato verificado dos dois lados** — o job `ts` roda testes do contract e do core; o job `android` garante que o app compila contra o contrato vigente.

### Negativas

- **Custo de runner em repo privado** — ~15–25 min por PR no total (NDK/CMake domina). Absorvido pela cota gratuita no volume atual.
- **Secret de longa duração** — `OTA_RSA_PRIVATE_KEY` vive no GitHub; se o repo/ org for comprometido, o atacante assina firmware. Mesma exposição já aceita no ADR-020 (CI secret), com rotação de chave por release major como mitigação.
- **Release depende da versão do app no firmware** — o check tag↔app-descriptor falha até a fork setar `project VERSION` no CMakeLists; a primeira tag `v*` vai falhar de propósito até isso ser corrigido no fork.
- **Commit bot no branch default** — o `release.yml` faz push de commit do manifest para `tamagotchi`; se houver push concorrente durante a release, o push falha e precisa retry manual (aceitável no ritmo atual).
- **Sem instrumented tests Android** — `connectedAndroidTest` exige emulador/device; fora do MVP de CI (custo/complexidade de emulador não se paga agora).

## Notas

- **Firmware**: CI própria do submódulo permanece a fonte de verdade de build de boards; este ADR não a altera.
- **Self-test pós-OTA**: o que a CI **não** cobre (WiFi real, I2S, display, KWS no hardware físico) é especificado na [Spec 08](../specs/08-ota-self-test-pos-atualizacao.md) e no [Ticket 08](../tickets/08-ota-self-test-pos-atualizacao.md) — a verificação no device é o complemento da verificação em CI.
- **Configuração única necessária** para ativar a release OTA: adicionar o secret `OTA_RSA_PRIVATE_KEY` (chave privada RSA-4096 gerada conforme ADR-020) nas settings do repo.
- **Referências**: [ADR-018](018-tamagotchi-comportamento-mora-no-core-typescript.md) (stacks do monorepo), [ADR-020](020-tamagotchi-ota-pull-com-esp32fota.md) (política de assinatura e manifest que este ADR automatiza), [ADR-021](021-tamagotchi-firmware-xiaozhi-esp32-com-customizacoes.md) (board cores3-felipe), docs do Biome/pnpm/Dependabot via Context7 (2026-09-01).
