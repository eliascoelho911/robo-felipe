# 07: CI com GitHub Actions no monorepo

**What to build:** Workflows GitHub Actions que dão guardrails automáticos
ao monorepo e automatizam a release OTA. O `ci.yml` roda em todo push
para `tamagotchi` e PR com 4 jobs paralelos: `ts` (pnpm frozen-lockfile →
`biome ci` → `pnpm -r test` → `contract gen`), `android`
(`./gradlew lint test assembleDebug`), `docker` (`docker compose config`
da Nuvem com submódulo `esp32-server/upstream` inicializado) e
`ota-manifest` (`python3 ota/manifest/validate_manifest.py`).

O `release.yml` roda em tag `v*.*.*`: job `build` no container
`espressif/idf:v6.0.2` builda a board `m5stack/cores3-felipe`, confere a
versão do app descriptor contra a tag, assina `firmware.bin` e
`generated_assets.bin` com o secret `OTA_RSA_PRIVATE_KEY` (RSA-4096,
prepend de 512 B conforme ADR-020) e sobe artefatos; job `release` cria a
GitHub Release com `firmware.img`/`filesystem.img` e commita o manifest
atualizado (via `update_manifest.py`, que valida antes de escrever) no
branch `tamagotchi`.

O `dependabot.yml` abre PRs semanais agrupados para github-actions, npm
(raiz, core, contract) e gradle.

Scripts de suporte: `ota/manifest/validate_manifest.py` (valida type,
semver, host/port, URLs de release) e `ota/manifest/update_manifest.py`
(atualiza para a tag, valida antes de escrever), ambos stdlib-only. Recipe
`just ota-manifest-check` na raiz para validação local.

**Blocked by:** (nenhum)

**Status:** ready-for-agent

- [ ] `.github/workflows/ci.yml` com jobs `ts`, `android`, `docker`, `ota-manifest` (push `tamagotchi` + PR; `permissions: contents: read`).
- [ ] Job `ts` usa `pnpm install --frozen-lockfile`, `biome ci .`, `pnpm -r test`, `pnpm --filter contract gen`.
- [ ] Job `android` roda `./gradlew lint test assembleDebug --stacktrace` com JDK 17.
- [ ] Job `docker` valida `docker compose config` com o submódulo `esp32-server/upstream` inicializado.
- [ ] Job `ota-manifest` roda `validate_manifest.py` e o manifest atual do repo passa.
- [ ] `.github/workflows/release.yml` builda cores3-felipe no container `espressif/idf:v6.0.2` em tag `v*.*.*`.
- [ ] Release confere a versão do app descriptor (magic `0xABCD5432`) contra a tag.
- [ ] Release assina `firmware.bin` e `generated_assets.bin` com `OTA_RSA_PRIVATE_KEY` e publica `firmware.img`/`filesystem.img` como assets.
- [ ] Release atualiza `ota/manifest/manifest.json` via `update_manifest.py` (valida antes de escrever) e commita no branch default.
- [ ] `.github/dependabot.yml` com 3 entradas semanais agrupadas (github-actions, npm com directories, gradle).
- [ ] `ota/manifest/validate_manifest.py` e `update_manifest.py` stdlib-only, validados localmente.
- [ ] Recipe `just ota-manifest-check` no Justfile.
- [ ] ADR-024 em `docs/decisions/` seguindo o formato dos existentes.
- [ ] AGENTS.md atualizado: mapa com `.github/`, referência ao ADR-024, seção Verificação sem o "Não há CI automatizado".
- [ ] Demoable: abrir um PR que quebre um teste vitest → CI fica vermelho no job `ts`; editar o manifest com URL errada → job `ota-manifest` falha.
- [ ] Demoable (requer secret): push de tag `vX.Y.Z` com `OTA_RSA_PRIVATE_KEY` configurado → Release criada com assets assinados e manifest atualizado no branch default.
