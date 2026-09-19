# Robô Felipe

Robô conversacional de voz para o sobrinho de 8 anos do autor. Variante
atual em desenvolvimento: o **Tamagotchi** — um pet de bolso, com display,
microfone e alto-falante, autocontido (sem pernas, sem servos, com câmera — ver ADR-017),
em **ESP32-S3 com PSRAM** (ver ADR-016), processando voz direto com a
nuvem sem relay de smartphone.

O projeto passou anteriormente por duas variantes de corpo de robô de
chão (um bípede do kit ACEBOTT e um quadrúpede de chassi 3D inspirado no
ESP-HI), ambas apoiadas em **ESP32-WROOM-32E-N4** e na arquitetura de voz
com relay de smartphone (KWS local + ASR/TTS em nuvem via app, ADRs
001-007). Essas variantes estão arquivadas nos branches `quadrupede` e
`main`; os ADRs originais permanecem como registro histórico dessa
arquitetura.

## Language

**Robô Felipe**:
O robô conversacional objeto deste projeto — um pet de bolso (Tamagotchi)
com interação por voz em português.
_Avoid_: Felipe (ambíguo com o sobrinho), o robô, o cachorro, o bichinho

**Sobrinho**:
O usuário final — criança de 8 anos. Refina requisitos de UX
(vocabulário, paciência de latência, robustez de manuseio).
_Avoid_: criança, usuário

**Tamagotchi**:
Variante de corpo atual — pet de bolso com display, microfone e
alto-falante e câmera, sem pernas e sem servos. Autocontido: processa
voz direto com a nuvem (termina TLS no próprio ESP32-S3 com PSRAM), sem
depender de um smartphone relay. Ver ADR-016; a câmera entrou no escopo
via ADR-017.
_Avoid_: o bichinho, o pet

**Bípede**:
Variante de corpo anterior (arquivada no branch `main`) — kit ACEBOTT de
4 servos, 2 pernas, marcha por keyframes, em ESP32-WROOM-32E-N4 com
relay de smartphone. Mantida como histórico; tutorial e sketches originais
foram removidos deste branch.
_Avoid_: robô antigo, versão 1

**Quadrúpede**:
Variante de corpo anterior (arquivada no branch `quadrupede`) — cão de 4
patas, chassi 3D inspirado no ESP-HI, locomoção por N servos, em
ESP32-WROOM-32E-N4 com relay de smartphone. Pipeline de voz reusada do
bípede. Pinout e modelo 3D do chassi foram removidos deste branch.
_Avoid_: cão robô, doggo, robô novo

**Relay**:
O smartphone que, nas variantes bípede/quadrúpede, termina TLS, converte
PCM, e orquestra ASR→NLP→TTS com a nuvem (ver ADR-002). **Não se aplica
ao Tamagotchi** (ADR-016 revoga o relay para essa variante).
_Avoid_: celular, gateway, broker

**Nuvem**:
O servidor auto-hospedado do projeto (fork do xiaozhi-esp32-server,
ADR-022) com seus provedores plugáveis de ASR, LLM e TTS. Orquestra o
pipeline de voz e **hospeda o comportamento do pet** — estado, stats,
decay, estágios e tools (ADR-018, ADR-023). Os provedores são trocáveis
sem mudar o firmware; o comportamento é código nosso no fork.
_Avoid_: servidor, backend, core

**xiaozhi-esp32**:
Projeto de referência (GitHub `78/xiaozhi-esp32`) de robôs conversacionais
Espressif, em ESP32-S3. Inspira o protocolo e a UX conversacional do
Tamagotchi; o fork nosso é o firmware do device (ADR-021).
_Avoid_: xiaozhi, o firmware de referência

**xiaozhi-esp32-server**:
Projeto de referência (GitHub `xinnan-tech/xiaozhi-esp32-server`) de
servidor conversacional para robôs xiaozhi — ASR/LLM/TTS plugáveis,
protocolo WSS+Opus com o device, plugins com acesso ao `conn`. O fork
nosso é a Nuvem e o host do comportamento do pet (ADR-018, ADR-022).
_Avoid_: o servidor upstream, xiaozhi-server upstream

**Plataforma**:
O host onde o Tamagotchi encarna — o firmware do CoreS3 (ADR-021).
Detecta Triggers e executa as Ações recebidas da Nuvem; não decide
comportamento.
_Avoid_: device, cliente, app (sozinho), Android

**Trigger**:
Evento detectado pelo device, com timestamp e payload. Iniciais:
`voice` (áudio da fala, que segue o pipeline de voz), `shake`
(sacudida), `button` (toque de botão). Os não-vozeados vão à Nuvem como
mensagens no protocolo WSS do xiaozhi (ADR-018, ADR-023).
_Avoid_: evento, input, comando

**Ação**:
Efeito que o device sabe executar — `speak`, `dance`,
`express_emotion`, `get_dizzy`, `sleep`, entre outras. Chegam ao device
via mensagens `pet_action` no protocolo WSS do xiaozhi (ADR-018, ADR-023).
_Avoid_: comando, output

**ESP-HI**:
Design de referência (makerworld + oshwhub) de cão robô baixo-custo em
ESP32-C3. Forneceu o modelo 3D do chassi e o gait do quadrúpede
(arquivado); **não** define o MCU do Robô Felipe.
_Avoid_: o cão, o modelo 3D
