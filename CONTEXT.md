# Robô Felipe

Robô conversacional de voz para o sobrinho de 8 anos do autor. Pivotou do
corpo bípede (kit ACEBOTT) para o corpo de cão quadrúpede (chassi 3D
ESP-HI), mantendo a arquitetura de voz existente (KWS local + ASR/TTS em
nuvem via relay smartphone) e o MCU ESP32-WROOM-32E-N4.

## Language

**Robô Felipe**:
O robô conversacional objeto deste projeto — um cão robô quadrúpede com
interação por voz em português.
_Avoid_: Felipe (ambíguo com o sobrinho), o robô, o cachorro

**Sobrinho**:
O usuário final — criança de 8 anos. Refina requisitos de UX
(vocabulário, paciência de latência, robustez de manuseio).
_Avoid_: criança, usuário

**Bípede**:
Variante de corpo anterior — kit ACEBOTT de 4 servos, 2 pernas, marcha
por keyframes. Mantida como referência de firmware e tutorial.
_Avoid_: robô antigo, versão 1

**Quadrúpede**:
Variante de corpo atual — cão de 4 patas, chassi 3D inspirado no ESP-HI,
locomoção por N servos. Pipeline de voz reusada do bípede.
_Avoid_: cão robô, doggo, robô novo

**Relay**:
O smartphone (app Android já em desenvolvimento) que termina TLS,
converte PCM, e orquestra ASR→NLP→TTS com a nuvem. Ver ADR-002.
_Avoid_: celular, gateway, broker

**Nuvem**:
Provedores externos de ASR, NLP/LLM e TTS acessados via HTTPS pelo relay.
Trocáveis sem mudar o firmware.
_Avoid_: servidor, backend

**xiaozhi-esp32**:
Projeto de referência (GitHub `78/xiaozhi-esp32`) de robôs conversacionais
Espressif, em ESP32-S3. Inspira o protocolo e a UX conversacional, **não**
o hardware — o Robô Felipe mantém o WROOM-32E-N4 e o relay-smartphone.
_Avoid_: xiaozhi, o firmware de referência

**ESP-HI**:
Design de referência (makerworld + oshwhub) de cão robô baixo-custo em
ESP32-C3. Fornece o modelo 3D do chassi e o gait de quadrúpede; **não**
define o MCU do Robô Felipe.
_Avoid_: o cão, o modelo 3D
