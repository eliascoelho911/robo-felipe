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
O smartphone (app Android já em desenvolvimento) que, nas variantes
bípede/quadrúpede, termina TLS, converte PCM, e orquestra ASR→NLP→TTS
com a nuvem (ver ADR-002). **Não se aplica ao Tamagotchi** (ADR-016
revoga o relay para essa variante).
_Avoid_: celular, gateway, broker

**Nuvem**:
Provedores externos de ASR, NLP/LLM e TTS acessados via HTTPS. Trocáveis
sem mudar o firmware. No Tamagotchi o acesso parte direto do robô; nas
variantes anteriores parte do relay.
_Avoid_: servidor, backend

**xiaozhi-esp32**:
Projeto de referência (GitHub `78/xiaozhi-esp32`) de robôs conversacionais
Espressif, em ESP32-S3. Inspira o protocolo e a UX conversacional do
Tamagotchi.
_Avoid_: xiaozhi, o firmware de referência

**Plataforma**:
O host onde o Tamagotchi encarna — hoje o app Android de laboratório,
depois o CoreS3. Detecta Triggers, envia Batches ao Core e executa
Planos de Ações.
_Avoid_: device, cliente, app (sozinho)

**Trigger**:
Evento detectado pela Plataforma, com timestamp e payload. Iniciais:
`voz` (áudio após a hot key), `sacudida`, `toque de botão`.
_Avoid_: evento, input, comando

**Batch**:
Envelope versionado com um ou mais Triggers enviado ao Core.
_Avoid_: pacote, mensagem, request

**Core**:
Subsistema nosso, auto-hospedado em TypeScript, que processa Batches e
responde com Planos de Ações. Pode chamar a Nuvem. Ver ADR-018.
_Avoid_: core embarcado, firmware core, backend, cérebro

**Ação**:
Efeito que a Plataforma sabe executar — `falar`, `dançar`,
`expressar emoção`, `ficar tonto`, entre outras.
_Avoid_: comando, output

**Plano de Ações**:
Resposta do Core — lista ordenada de uma ou mais Ações.
_Avoid_: output, resposta, resultado

**ESP-HI**:
Design de referência (makerworld + oshwhub) de cão robô baixo-custo em
ESP32-C3. Forneceu o modelo 3D do chassi e o gait do quadrúpede
(arquivado); **não** define o MCU do Robô Felipe.
_Avoid_: o cão, o modelo 3D
