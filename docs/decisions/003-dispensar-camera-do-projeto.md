# ADR-003: Dispensar câmera (visão computacional) do projeto

## Status
Accepted

## Date
2026-07-12

## Context

Em uma fase anterior da discussão arquitetural, considerou-se usar uma
placa **ESP32-CAM** sobressalente (módulo Ai-Thinker com OV2640, 4 MB de
PSRAM, SD card) como segundo MCU do robô, dedicado a visão computacional
— detecção de rosto, desvio por imagem, segue-linha por câmera.

A motivação para considerar visão era:
- O ESP32-CAM já estava disponível (custo zero de aquisição).
- A PSRAM do CAM permitiria buffers de frame (150 KB cada) que o
  ESP32-WROOM-32E-N4 principal (sem PSRAM) não comporta.
- Visão abriria capacidades que o ultrassom do tutorial não entrega
  (reconhecimento de gestos, leitura de QR, mapeamento).

Porém, ao detalhar a pinagem do ESP32-CAM, descobriu-se um conflito
fundamental:

```
Câmera OV2640 consome:  GPIO 0,5,18,19,21,22,23,25,26,27,32,34,35,36,39
SD card consome:        GPIO 2,4,12,13,14,15
PSRAM consome (interna): GPIO 16,17
Sobram:                 quase nenhum (apenas GPIO 33 e UART0 1/3)
```

O ESP32-CAM **não tem pinos suficientes** para I2S (mic + speaker)
concomitante com a câmera ativa. E a função primária que se quer do robô
é **voz** (ver ADR-005 e ADR-006), não visão.

Além disso, a PSRAM do CAM é **interna ao chip dele** — não estende a
RAM do ESP32-WROOM-32E-N4 principal. Os dois MCUs são ilhas de memória
separadas; o CAM só ajuda se a tarefa pesada rodar nele mesmo.

## Decision

**Dispensar a câmera e a visão computacional do escopo do robô-felipe.**
O ESP32-CAM sobressalente não será integrado. O robô usa o **ultrassom**
do tutorial original para percepção de distância/obstáculos, e a
**voz** (KWS + ASR/TTS) como modalidade principal de interação.

## Alternatives Considered

### Manter ESP32-CAM para visão + voz no MCU principal

- **Prós:**
  - Ganho de capacidade de visão (seguir rosto, desvio por imagem).
  - Stream de vídeo para o app do celular.
- **Contras:**
  - **Pinos insuficientes no CAM** para I2S junto com câmera — voz teria
    que ficar no MCU principal de qualquer forma.
  - **Complexidade de dois firmwares** (CAM + ACEBOTT) + protocolo
    inter-MCU (UART) + coordenação de estados.
  - **A PSRAM do CAM não alivia a RAM do ACEBOTT** — cada chip é uma
    ilha; o CAM só ajuda se a tarefa rodar nele.
  - **Programação do CAM** exige adaptador USB-UART (FTDI/CP2102) +
    botão de boot — fricção de desenvolvimento.
  - **Consumo e espaço** — mais uma placa, mais fiação, mais peso num
    robô bípede pequeno.
- **Rejeitada:** a voz é a prioridade do produto, e a câmera **impede**
  o I2S no CAM. O custo (dois firmwares, programador, coordenação) é
  alto para uma capacidade (visão) que não está no escopo atual.

### Substituir o MCU principal por um ESP32 com PSRAM + câmera integrada

(ex.: ESP32-S3 com PSRAM e câmera, ou placa com ESP32-WROVER)

- **Prós:**
  - Um só chip, pinagem unificada, PSRAM para frames.
- **Contras:**
  - **Incompatível com o kit ACEBOTT** — a placa-mãe do robô é soldada
    para o módulo WROOM-32E; trocar o SoC requer redesenho de hardware.
  - **ESP32-S3 tem pinagem diferente** do ESP32 clássico; servos e
    shields do kit podem não mapear.
  - **Foge do escopo** — o robô-felipe é uma extensão do kit ACEBOTT,
    não um redesign de hardware.
- **Rejeitada:** mudar o SoC é um redesign de hardware fora do escopo.

### Visão somente no app mobile (câmera do celular)

- **Prós:**
  - Zero hardware no robô — processa no celular, que já é o relay
    (ver ADR-002).
  - Câmera do celular é muito superior à OV2640.
- **Contras:**
  - **Robô não vê por si mesmo** — depende do celular apontado para ele
    ou para o ambiente. Não serve para desvio de obstáculos autônomo.
  - **Atravessa o propósito** — se a visão está no celular, não é o
    robô que enxerga; é o usuário assistindo.
- **Rejeitada:** não atende ao caso de uso de percepção embarcada. Pode
  ser revisitada se o produto evoluir para um modo "telepresença com
  câmera do celular".

## Consequences

### Positivas

- **Pinagem simplificada** no MCU principal — todos os GPIOs livres
  ficam disponíveis para I2S (mic/speaker), I2C, servos e ultrassom,
  sem partilha com câmera.
- **Um só firmware** — sem coordenação inter-MCU, sem protocolo UART
  entre chips, sem programador FTDI para o CAM.
- **RAM do MCU principal não precisa cobrir buffers de imagem** — os
  ~333 KB são dedicados a voz + motores + rede.
- **Consumo e peso reduzidos** — uma placa a menos no robô.
- **Ultrassom mantém função de obstáculo** do tutorial original, que
  já funciona e é suficiente para desvio simples.

### Negativas

- **Sem visão computacional** — o robô não reconhece rostos, não lê
  QR, não faz segue-linha por imagem. Percepção fica limitada a
  distância (ultrassom) e voz.
- **Stream de vídeo indisponível** — o app de controle não recebe
  imagem do robô.
- **ESP32-CAM sobressalente ocioso** — hardware disponível não
  aproveitado (poderia ser usado em outro projeto).

### Notas

- Se a visão se tornar requisito futuro, revisitar: trocar o módulo
  principal por uma variante com PSRAM (N4R2) + câmera integrada via
  redesiño de hardware, ou reintroduzir o ESP32-CAM como nó de visão
  dedicado — mas então a voz precisa caber no MCU principal sem
  PSRAM, o que já está endereçado por ADR-005 e ADR-006.
- O ultrassom (Trig/Echo) permanece como sensor de distância, herdado
  do tutorial ACEBOTT Lição 3 e 4.
