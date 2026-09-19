# 07: CI com GitHub Actions no monorepo

**What to build:** Workflows GitHub Actions que dão guardrails automáticos
ao monorepo e automatizam a release OTA. O `ci.yml` roda em todo push
para `tamagotchi` e PR com jobs paralelos: `docker` (`docker compose
config` da Nuvem com submódulo `esp32-server/upstream` inicializado,
base `compose_all`) e `ota-manifest`
(`python3 ota/manifest/validate_manifest.py`). (Quando o repo tinha
stacks TS e Android, havia também os jobs `ts` e `android` — removidos
junto com as stacks, ADR-018.)

O `release.yml` roda em tag `v*.*.*`: job `build` no container
`espressif/idf:v6.0.2` builda a board `m5stack/cores3-felipe`, confere a
versão do app descriptor contra a tag, assina `firmware.bin` e
`generated_assets.bin` com o secret `OTA_RSA_PRIVATE_KEY` (RSA-4096,
prepend de 512 B conforme ADR-020) e sobe artefatos; job `release` cria a
GitHub Release com `firmware.img`/`filesystem.img` e commita o manifest
atualizado (via `update_manifest.py`, que valida antes de escrever) no
branch `tamagotchi`.

O `dependabot.yml` abre PRs semanais agrupados para github-actions.
(Entradas npm/gradle removidas junto com as stacks.)

Scripts de suporte: `ota/manifest/validate_manifest.py` (valida type,
semver, host/port, URLs de release) e `ota/manifest/update_manifest.py`
(atualiza para a tag, valida antes de escrever), ambos stdlib-only. Recipe
`just ota-manifest-check` na raiz para validação local.

**Blocked by:** (nenhum)

**Status:** done

- [x] `.github/workflows/ci.yml` com jobs `docker`, `ota-manifest` (push `tamagotchi` + PR; `permissions: contents: read`).
- [x] Job `docker` valida `docker compose config` com o submódulo `esp32-server/upstream` inicializado (base `compose_all`).
- [x] Job `ota-manifest` roda `validate_manifest.py` e o manifest atual do repo passa.
- [x] `.github/workflows/release.yml` builda cores3-felipe no container `espressif/idf:v6.0.2` em tag `v*.*.*`.
- [x] Release confere a versão do app descriptor (magic `0xABCD5432`) contra a tag.
- [x] Release assina `firmware.bin` e `generated_assets.bin` com `OTA_RSA_PRIVATE_KEY` e publica `firmware.img`/`filesystem.img` como assets.
- [x] Release atualiza `ota/manifest/manifest.json` via `update_manifest.py` (valida antes de escrever) e commita no branch default.
- [x] `.github/dependabot.yml` com entrada semanal agrupada (github-actions).
- [x] `ota/manifest/validate_manifest.py` e `update_manifest.py` stdlib-only, validados localmente.
- [x] Recipe `just ota-manifest-check` no Justfile.
- [x] ADR-024 em `docs/decisions/` seguindo o formato dos existentes.
- [x] AGENTS.md atualizado: mapa com `.github/`, referência ao ADR-024, seção Verificação sem o "Não há CI automatizado".
- [x] Demoable: editar o manifest com URL errada → job `ota-manifest` falha; quebrar o compose → job `docker` falha.
- [x] Demoable (requer secret): push de tag `vX.Y.Z` com `OTA_RSA_PRIVATE_KEY` configurado → Release criada com assets assinados e manifest atualizado no branch default.
