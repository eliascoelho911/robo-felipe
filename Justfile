# Justfile do robo-felipe — recipes multi-linguagem.
# Instale `just` na sua máquina (https://github.com/casey/just).
# O workspace TS usa pnpm; firmware/ usa ESP-IDF;
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

# --- Nuvem (xiaozhi-esp32-server, Docker) ---

# Sobe a Nuvem local (stack completo do upstream + override de config pt-BR).
# compose_all = websocket + console (manager-api/web :8002) + MySQL + Redis,
# exigidos pela ativação de device (código de 6 dígitos vive no Redis) e pelo
# registro do device no MySQL.
# --project-directory esp32-server faz os paths relativos (./config, ./data,
# ./models) resolverem contra esp32-server/, e não contra o dir do base do
# upstream (ver cabeçalho do override para o porquê).
# -f base primeiro, -f override depois: o override mescla sobre o base —
# o mount de config/.config.yaml entra junto do ./data do compose_all
# (volumes acumulam por target) e o !override mantém 8091/8003 no host.
esp32-server-up:
    docker compose --project-directory esp32-server \
                   -f esp32-server/upstream/main/xiaozhi-server/docker-compose_all.yml \
                   -f esp32-server/docker-compose.override.yml up -d

# Valida a config do compose (merge stack completo+override) sem subir.
esp32-server-check:
    docker compose --project-directory esp32-server \
                   -f esp32-server/upstream/main/xiaozhi-server/docker-compose_all.yml \
                   -f esp32-server/docker-compose.override.yml config

# Para a Nuvem e remove contêineres (redes/volumes nomeados preservados).
esp32-server-down:
    docker compose --project-directory esp32-server \
                   -f esp32-server/upstream/main/xiaozhi-server/docker-compose_all.yml \
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
# bash -c porque a recipe roda em sh (dash), que não tem `source`, e o
# export.sh do ESP-IDF é bash-script;
# instalado sem sudo (sistema não tem cmake).
firmware-build:
    bash -c 'source "$HOME/esp/esp-idf/export.sh" && export PATH="$HOME/.local/opt/bin:$PATH" && cd firmware && python3 scripts/build.py m5stack/cores3-felipe --name cores3-felipe --language pt-BR'

# Instala a imagem buildada no CoreS3 (exige ESP-IDF sourced e o device
# em modo download). O CoreS3 entra em download via USB-Serial-JTAG;
# se o flash falhar, segure o botão do meio e dê um toque no reset.
# Porta default vem de .vscode/arduino.json.
# idf.py via python3 (e não o console-script) porque o cache do projeto
# gravou o intérprete com que o build.py o rodou; o mismatch
# python/python3 faz o idf.py abortar.
firmware-flash port="/dev/ttyACM0":
    bash -c 'source "$HOME/esp/esp-idf/export.sh" && export PATH="$HOME/.local/opt/bin:$PATH" && cd firmware && python3 "$IDF_PATH/tools/idf.py" -p {{port}} flash'

# Regenera lang_config.h do locale pt-BR (o build da CMake faz isso
# automaticamente; esta recipe é para rodar à mão após editar language.json).
firmware-locales:
    cd firmware && python3 scripts/gen_lang.py --language pt-BR --output main/assets/lang_config.h

# --- OTA (manifest, ADR-020) ---

# Valida o manifest OTA (formato, semver, URLs de Release deste repo).
ota-manifest-check:
    python3 ota/manifest/validate_manifest.py

