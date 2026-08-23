# ADR-001: Usar C/C++ como linguagem do firmware

## Status
Accepted

## Date
2026-07-12

## Context

O projeto **robo-felipe** é um robô conversacional de voz construído sobre
o módulo **ESP32-WROOM-32E-N4** (chip ESP32-D0WD-V3 rev. 301, 4 MB de
flash, 520 KB de SRAM — ~333 KB livres — **sem PSRAM**). O projeto
originou-se de um kit bípede ACEBOTT (variante arquivada), cuja
biblioteca de controle dos 4 servos (`ACB_Biped_Robot.h`) escrita em
C/C++ para o Arduino IDE — com sequências de keyframes em arrays
`PROGMEM` e um player `Servo_PROGRAM_Run()` bloqueante baseado em
`delay()` — motivou a escolha de linguagem documentada nesta ADR.

O robô, na variante de corpo da época (bípede/quadrúpede), executava:
marcha para frente/trás/esquerda/direita, dança, seguidor por ultrassom,
desvio de obstáculos, controle via serial, web (AP + HTTP) e app mobile
(WiFi). Pretendia-se **adicionar** (subsistema de voz, reutilizado pela
variante Tamagotchi — ver ADR-016):

- **Microfone** MEMS I2S (SPH0645LM4H — ver `hardware/audio/BOM-audio.md`
  para análise de datasheets; o INMP441 é alternativa de orçamento)
  para captura de áudio.
- **Saída de som** via amp I2S (ex.: MAX98357A) para playback.
- **Display** para status/expressões (OLED SSD1306 via I2C, dado o
  orçamento de RAM — TFT framebuffer consumiria ~115 KB dos 333 KB
  disponíveis).

Os requisitos de tempo real são os fator determinante:

1. **4 servos com timing de keyframes** (na variante de corpo da época)
   — cada passo de animação depende de `delay()` preciso entre
   atualizações de PWM. Jitter de milissegundos destrói a estabilidade
   da marcha.
2. **Áudio I2S bidirecional** — captura e playback simultâneos via DMA.
   Pausas no processamento causam xruns (clics/dropouts audíveis).
3. **WiFi AP ativo** concorrente com servos e áudio — o stack de rede
   (`lwIP`) roda em tasks do FreeRTOS e precisa de CPU sem bloquear o
   motion.
4. **Orçamento de RAM apertado** — ~333 KB no total, dos quais ~70–80 KB
   são consumidos pelo WiFi assim que o AP sobe. Sobram ~250 KB para
   tudo o mais.

A escolha de linguagem precisa acomodar essas quatro restrições
simultaneamente.

## Decision

**Usar C/C++ com Arduino-ESP32 core e FreeRTOS** como linguagem do
firmware. Refatorar a lógica existente do tutorial para um design
multi-task não-bloqueante, mantendo a biblioteca `ACB_Biped_Robot` e
estendendo-a.

Justificativas diretas:

- **Biblioteca existente é C/C++** — reusar `ACB_Biped_Robot.h` (kit de
  origem) sem reescrita. Portar para MicroPython exigiria reimplementar o
  player de keyframes e as tabelas `PROGMEM` em Python puro, perdendo a
  vantagem do kit.
- **FreeRTOS dá concorrência determinística** — 2 cores do ESP32
  alocados por task: Core 0 (PRO_CPU) para rede + áudio I2S (DMA),
  Core 1 (APP_CPU) para motores + sensores + display + máquina de
  estados. `delay()` vira `vTaskDelay()` e o player de servo deixa de
  bloquear o áudio.
- **I2S maduro no ESP-IDF/Arduino** — driver DMA bidirecional de áudio
  estável, com controle de ring buffers e prioridade de task.
- **Controle total de memória** — `heap_caps_malloc(...,
  MALLOC_CAP_INTERNAL)` para buffers de DMA, tamanhos de stack por
  task, e sem interpretador consumindo ~150 KB de RAM.
- **ISR com latência previsível** — leitura de ultrassom e possíveis
  encoders futuros usam ISRs em C, sem a camada de trampolines do
  MicroPython.

## Alternatives Considered

### MicroPython (variante ESP32_GENERIC)

- **Prós:**
  - Curva de aprendizado baixa, REPL interativo, iteração rápida.
  - `machine` (Pin, PWM, I2C, SPI) já é implementado em C no firmware.
  - Bom para prototipar lógica de estado e comunicação.
- **Contras:**
  - **Interpretador consome ~150 KB dos 333 KB livres** — com WiFi ativo
    (+70–80 KB) sobram menos de ~100 KB para áudio, display e servos.
    Inviável para I2S bidirecional sem PSRAM.
  - **Garbage Collector causa pausas de milissegundos** — jitter no
    player de servos e xruns no áudio I2S. Não há como desligar o GC
    de forma segura em um sistema que aloca continuamente.
  - **`ACB_Biped_Robot` precisaria ser reescrita** em Python puro (as
    tabelas `PROGMEM` e o player bloqueante), ou recompilada como
    user C module — o que anula a vantagem da "simplicidade" do
    MicroPython e nos coloca de volta no mundo C/C++ anyway.
  - **I2S bidirecional tem suporte limitado** e instável no MicroPython
    para ESP32; a comunidade reporta cliques e dropouts mesmo em
    setups mais simples.
- **Rejeitada:** a combinação de RAM escassa + GC não-determinístico +
  áudio em tempo real + biblioteca existente em C torna o MicroPython
  inadequado para este projeto especificamente. Útil para prototipagem
  isolada, não para o firmware final.

### MicroPython + módulos nativos em C (abordagem híbrida single-MCU)

- **Prós:**
  - Partes críticas (servos, I2S) em C compilado no firmware; lógica de
    alto nível em Python.
  - Mantém o REPL para iteração de comportamento.
- **Contras:**
  - **Exige recompilar o firmware MicroPython** a cada mudança de
    módulo C — perde-se a vantagem do "upload rápido de script".
  - **Ainda paga o custo de RAM do interpretador** (~150 KB) mesmo que
    o caminho quente seja C.
  - **GC continua rodando** sobre os objetos Python que envolvem as
    chamadas C — pausas permanecem.
  - **Complexidade de build alta** — toolchain MicroPython + user C
    modules para ESP32 é trabalhosa de manter.
- **Rejeitada:** mesma restrição de RAM do MicroPython puro, mais a
  complexidade de manter dois toolchains. Se vamos escrever a parte
  crítica em C de qualquer forma, faz sentido ir direto para C/C++.

### Rust (no_std, framework embassy ou esp-rs)

- **Prós:**
  - Garantias de segurança de memória em compile time.
  - Concorrência via `async`/`await` determinístico (sem GC).
  - Ecossistema `esp-rs` em maturação.
- **Contras:**
  - **`ACB_Biped_Robot` teria que ser reescrita** — não há binding
    Arduino-ESP32 estável para essa biblioteca específica do kit.
  - **Suporte a I2S bidirecional no esp-rs é incipiente** comparado ao
    ESP-IDF.
  - **Curva de aprendizado** — Rust no embedded ainda é nicho; o
    tutorial de referência e a comunidade do kit são em Arduino C++.
  - **Toolchain nightly** em parte do esp-rs — fricção de setup.
- **Rejeitada:** tecnicamente atraente para um projeto do zero, mas o
  custo de reescrita da biblioteca existente e a imaturidade do suporte
  a I2S no esp-rs tornam o risco alto para o escopo atual.

### Dois MCUs (ESP32 em C/C++ para baixo nível + MCU secundário em MicroPython para lógica)

- **Prós:**
  - Separação limpa: tempo real num chip, lógica/telemetria no outro.
  - MicroPython roda com folga no segundo MCU.
- **Contras:**
  - **Mais hardware** — custo, espaço, peso e consumo no robô (que já é
    restrito pelo kit de origem).
  - **Link de comunicação (UART/I2C) vira ponto de falha** e fonte de
    latência.
  - **Overkill** para o escopo — o ESP32 dual-core com FreeRTOS já
    comporta as duas funções em paralelo.
- **Rejeitada:** desnecessária para o escopo. Pode ser reconsiderada se
  no futuro o robô precisar de visão computacional ou ML pesado.

## Consequences

### Positivas

- **Reuso da `ACB_Biped_Robot`** (kit de origem) e compatibilidade com
  o tutorial de origem — baixa fricção para quem aprendeu com o kit.
- **RAM sob controle:** ~333 KB livres são suficientes para WiFi + 4
  servos + OLED + I2S in/out com folga de ~150 KB, desde que buffers
  sejam fixos e evite-se alocação dinâmica em loops quentes.
- **Tempo real determinístico:** FreeRTOS + ISRs em C garantem timing
  de servos e áudio sem GC pauses.
- **Toolchain simples:** Arduino IDE (ou PlatformIO) já configurado
  para o kit; sem toolchain nightly ou build de firmware custom.
- **Caminho de migração para ESP-IDF puro** preservado, caso o projeto
  cresça além do Arduino core.

### Negativas

- **Curva de aprendizado de FreeRTOS** para quem só usou `loop()` +
  `delay()` do Arduino. Exige entender tasks, queues, semáforos e
  pinning de core.
- **Refatoração do player de servo é obrigatória** — `Servo_PROGRAM_Run()`
  bloqueante do tutorial precisa virar uma task não-bloqueante, senão o
  áudio engasga durante a marcha. Esta é a mudança mais delicada.
- **Gerenciamento manual de memória** — sem GC, vazamentos são
  possíveis. Disciplina: alocar uma vez no setup, evitar `new`/`malloc`
  em loops, usar pools estáticos.
- **Build/flash mais lento** que MicroPython para mudanças de lógica
  pura — compensado pela estabilidade do resultado final.

### Notas

- A refatoração non-blocking do `Servo_PROGRAM_Run` é o **ponto
  crítico** e deve ser tratada primeiro (ver ADR-002, a escrever).
- Se no futuro o projeto precisar de framebuffer de display maior ou
  áudio de maior qualidade, reconsiderar o hardware: migrar para
  **ESP32-WROOM-32E-N4R2** (com 2 MB de PSRAM) mantém o mesmo footprint
  e a mesma base de código — apenas `MALLOC_CAP_SPIRAM` passa a ser
  utilizável. Esta ADR não precisa mudar.
- MicroPython permanece uma opção válida para **prototipagem isolada**
  de subsistemas (ex.: testar um sensor I2C novo numa bancada), mas não
  para o firmware integrado do robô.
