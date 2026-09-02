# Justfile do robo-felipe — recipes multi-linguagem.
# Instale `just` na sua máquina (https://github.com/casey/just).
# O workspace TS usa pnpm; android/ usa Gradle; firmware/ usa ESP-IDF;
# esp32-server/ usa Docker. Este arquivo orquestra todos.

# default: lista as recipes
default:
    @just --list

# --- TypeScript (workspace pnpm) ---

# Instala dependências do workspace TS
install:
    npx -y pnpm@latest install

# Lint/format checa todo o workspace TS
lint:
    npx -y pnpm@latest run lint

# Formata todo o workspace TS
format:
    npx -y pnpm@latest run format

# Testa tudo (vitest)
test:
    npx -y pnpm@latest -r test

# Roda o Core em modo dev (Hono HTTP REST). Padrão :8090; customizável
# com `just core-dev <porta>`. Node 24 no PATH: better-sqlite3 13
# segfaulta no dlopen com o node 22 desta máquina (AGENTS.md, Project
# Learnings).
core-dev port="8090":
    PATH="$HOME/.local/opt/node-v24.3.0-linux-x64/bin:$PATH" CORE_PORT={{port}} npx -y pnpm@latest --filter core dev

# Testa só o Core
core-test:
    npx -y pnpm@latest --filter core test

# Testa só o contrato (packages/contract)
contract-test:
    npx -y pnpm@latest --filter contract test

# Gera JSON Schema a partir dos schemas Zod do contrato
contract-gen:
    npx -y pnpm@latest --filter contract gen

# --- Android (Plataforma atual) ---

# Testes unitários do app Android
android-test:
    cd android && ./gradlew test

# Build debug do app Android
android-build:
    cd android && ./gradlew assembleDebug

# Lint do app Android
android-lint:
    cd android && ./gradlew lint

# Builda e instala o app no device/emulador conectado (adb detecta via Gradle)
android-install:
    cd android && ./gradlew installDebug

# Lista os devices/emuladores conectados
android-devices:
    adb devices

# --- Nuvem (xiaozhi-esp32-server, Docker) ---

# Sobe a Nuvem local (base do upstream + override de config pt-BR).
# --project-directory esp32-server faz os paths relativos (./config, ./data,
# ./models) resolverem contra esp32-server/, e não contra o dir do base do
# upstream (ver cabeçalho do override para o porquê).
# -f base primeiro, -f override depois: o override mescla sobre o base.
esp32-server-up:
    docker compose --project-directory esp32-server \
                   -f esp32-server/upstream/main/xiaozhi-server/docker-compose.yml \
                   -f esp32-server/docker-compose.override.yml up -d

# Valida a config do compose (merge base+override) sem subir.
esp32-server-check:
    docker compose --project-directory esp32-server \
                   -f esp32-server/upstream/main/xiaozhi-server/docker-compose.yml \
                   -f esp32-server/docker-compose.override.yml config

# Para a Nuvem e remove contêineres (redes/volumes nomeados preservados).
esp32-server-down:
    docker compose --project-directory esp32-server \
                   -f esp32-server/upstream/main/xiaozhi-server/docker-compose.yml \
                   -f esp32-server/docker-compose.override.yml down

# Recria a Nuvem: down + up, forçando recriação dos contêineres
# (útil após mudar o override ou a config montada).
esp32-server-recreate:
    just esp32-server-down
    just esp32-server-up

# --- Firmware (ESP-IDF, dentro de firmware/) ---

# Build do firmware da variante felipe (exige ESP-IDF sourced).
# WAKE_WORD_DISABLED e LANGUAGE_PT_BR já vêm no config.json da variante;
# --language pt-BR garante o locale sem depender de menuconfig.
firmware-build:
    cd firmware && python3 scripts/build.py m5stack/cores3-felipe --name cores3-felipe --language pt-BR

# Regenera lang_config.h do locale pt-BR (o build da CMake faz isso
# automaticamente; esta recipe é para rodar à mão após editar language.json).
firmware-locales:
    cd firmware && python3 scripts/gen_lang.py --language pt-BR --output main/assets/lang_config.h

# --- OTA (manifest, ADR-020) ---

# Valida o manifest OTA (formato, semver, URLs de Release deste repo).
ota-manifest-check:
    python3 ota/manifest/validate_manifest.py

