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

# Roda o Core em modo dev (Hono HTTP REST)
core-dev:
    npx -y pnpm@latest --filter core dev

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

# --- Nuvem (xiaozhi-esp32-server, Docker) ---

# Sobe a Nuvem local (base do upstream + override de config pt-BR).
# -f base primeiro, -f override depois: o override mescla sobre o base.
esp32-server-up:
    docker compose -f esp32-server/upstream/main/xiaozhi-server/docker-compose.yml \
                   -f esp32-server/docker-compose.override.yml up -d

# Valida a config do compose (merge base+override) sem subir.
esp32-server-check:
    docker compose -f esp32-server/upstream/main/xiaozhi-server/docker-compose.yml \
                   -f esp32-server/docker-compose.override.yml config

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

# --- Docs ---

# Verifica links de .md não apontam para arquivos removidos (resíduos).
# Mira em destinos de link markdown `](caminho)` — não em menções em prosa,
# assim o próprio AGENTS.md (que documenta os patterns) não falso-positiva.
check-docs:
    @echo "Verificando resíduos de branches arquivados em .md..."
    @! grep -rInE '\]\([^)]*(tutorial/|hardware/mcu/|hardware/esp32-cam/|pinout-quadrupede|esp32-wroom-32e-n4)' \
        --include='*.md' \
        --exclude-dir=firmware --exclude-dir=esp32-server --exclude-dir=node_modules --exclude-dir=.git \
        . 2>/dev/null \
      || (echo "Encontrado link para conteúdo removido em .md" && exit 1)
    @echo "OK — nenhum resíduo encontrado."
