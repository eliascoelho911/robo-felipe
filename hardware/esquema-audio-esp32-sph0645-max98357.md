# Esquema Técnico: ESP32-WROOM-32E-N4 + GY-SPH0645 + MAX98357A

> Esquema de interligação do subsistema de áudio do robô Felipe.
> Pinagem e specs validados contra `BOM-audio.md` e
> `esp32-wroom-32e-n4.md` (diagnóstico de hardware confirmado).
> Diagramas Mermaid gerados conforme a skill `mermaid-diagram-generator`.

---

## 1. Diagrama de Blocos do Sistema

Fluxo de potência e dados entre os três módulos e o alto-falante.

```mermaid
flowchart TD
    subgraph PWR["Alimentação"]
        VBUS["5 V (USB) ou LiPo"]
        RAIL33["Trilho 3,3 V<br/>(LDO onboard do ESP32)"]
        VBUS --> RAIL33
    end

    subgraph MCU["Controle — MCU"]
        ESP["ESP32-WROOM-32E-N4<br/>Xtensa dual-core 240 MHz<br/>Flash 4 MB · sem PSRAM<br/>I2S0 (RX) + I2S1 (TX)"]
    end

    subgraph IN["Entrada — Captura"]
        MIC["GY-SPH0645<br/>(SPH0645LM4H-B)<br/>I2S MEMS Mic · I2S Slave<br/>24-bit / 18 efetivos · 65 dB SNR"]
    end

    subgraph OUT["Saída — Playback"]
        AMP["MAX98357A<br/>I2S Filterless Class-D<br/>Mono (L+R)/2 · 3 W @ 4Ω<br/>92% eficiência · sem MCLK"]
        SPK[("Speaker<br/>8 Ω · 3 W · 40–50 mm")]
    end

    RAIL33 -->|3,3 V| ESP
    RAIL33 -->|3,3 V| MIC
    VBUS -->|5 V| AMP

    MIC -->|I2S RX · 48 kHz<br/>captura| ESP
    ESP -->|decimação FIR ×3<br/>48 → 16 kHz| ESP
    ESP -->|I2S TX · 16 kHz<br/>TTS playback| AMP
    AMP -->|PWM bridge-tied| SPK
```

---

## 2. Esquema de Interligação (pin-to-pin)

Pinagem fı́sica GPIO ↔ pino de cada breakout. Os rótulos `SCL/SDA` do
GY-SPH0645 são apenas nomes alternativos do fabricante — o barramento é
**I2S, não I2C**.

```mermaid
flowchart LR
    subgraph ESP["ESP32-WROOM-32E-N4"]
        direction TB
        P26["GPIO 26"]
        P25["GPIO 25"]
        P27["GPIO 27"]
        P14["GPIO 14"]
        P13["GPIO 13"]
        P15["GPIO 15"]
        P33["3V3"]
        PGND["GND"]
        P5V["5V (VBUS)"]
    end

    subgraph MIC["GY-SPH0645 (SPH0645LM4H)"]
        direction TB
        M_BCLK["BCLK / SCL"]
        M_WS["LRCLK / WS"]
        M_DOUT["DOUT / SDA"]
        M_SEL["SEL / L-R"]
        M_VCC["VCC"]
        M_GND["GND"]
    end

    subgraph AMP["MAX98357A"]
        direction TB
        A_BCLK["BCLK"]
        A_LRC["LRC"]
        A_DIN["DIN"]
        A_GAIN["GAIN_SLOT"]
        A_SD["SD_MODE"]
        A_VIN["VIN"]
        A_GND["GND"]
        A_OUTP["OUT+"]
        A_OUTM["OUT-"]
    end

    SPK[("Speaker<br/>8 Ω 3 W")]

    P26 ---|"BCLK (master out)"| M_BCLK
    P25 ---|"WS / LRCLK (master out)"| M_WS
    M_DOUT ---|"DOUT (data in)"| P27
    P33 ---|"VDD 3,3 V"| M_VCC
    PGND ---|"GND"| M_GND
    PGND ---|"SEL = GND → canal L"| M_SEL

    P14 ---|"BCLK (master out)"| A_BCLK
    P13 ---|"LRC / WS (master out)"| A_LRC
    P15 ---|"DIN (data out)"| A_DIN
    A_GAIN -.-|"desconectado<br/>(9 dB default)"| NC(("NC"))
    P33 ---|"SD_MODE = VDD<br/>→ mix (L+R)/2"| A_SD
    P5V ---|"VIN 5 V"| A_VIN
    PGND ---|"GND"| A_GND
    A_OUTP --- SPK
    A_OUTM --- SPK
```

---

## 3. Fluxo de Sinais I2S e Conversão de Sample Rate

Por que dois periféricos I2S: o mic captura a **48 kHz** (dentro da spec
2,048–4,096 MHz de BCLK do SPH0645LM4H) e o amp reproduz **16 kHz**
(TTS). O ESP32 faz decimação FIR ×3 no firmware entre os dois caminhos.

```mermaid
flowchart LR
    MIC["GY-SPH0645<br/>48 kHz"] -->|"I2S0 RX<br/>BCLK GPIO26 · WS GPIO25<br/>DIN GPIO27"| DSP["ESP32<br/>buffer +<br/>decimação FIR ×3"]
    DSP -->|"I2S1 TX<br/>BCLK GPIO14 · WS GPIO13<br/>DOUT GPIO15"| AMP["MAX98357A<br/>16 kHz"]
    AMP --> SPK["Speaker 8Ω"]
```

---

## 4. Diagrama de Temporização I2S

Sequência de sinais no barramento (ESP32 é master em ambos os canais).

```mermaid
sequenceDiagram
    participant ESP as ESP32 (I2S Master)
    participant MIC as GY-SPH0645 (Slave)
    participant AMP as MAX98357A (Slave)

    Note over ESP: Master gera BCLK + WS nos 2 barramentos
    rect rgb(235, 248, 255)
    ESP->>MIC: BCLK 3,072 MHz + WS 48 kHz
    MIC-->>ESP: DOUT 24-bit (18 efetivos) MSB-first
    ESP->>ESP: Decimação FIR ×3 (48 → 16 kHz)
    end
    rect rgb(255, 245, 235)
    ESP->>AMP: BCLK 1,024 MHz + WS 16 kHz
    ESP->>AMP: DIN 16-bit PCM (TTS)
    AMP->>AMP: Voice-mode IIR (LRCLK < 30 kHz — auto)
    AMP-->>Speaker: PWM Class-D bridge-tied (até 3 W)
    end
```

---

## 5. Tabela de Pinagem (resumo)

| Função | GPIO ESP32 | Pino GY-SPH0645 | Pino MAX98357A |
|:---|:---|:---|:---|
| Mic BCLK (master out) | GPIO 26 | BCLK / SCL | — |
| Mic WS / LRCLK (master out) | GPIO 25 | LRCLK / WS | — |
| Mic DOUT (data in) | GPIO 27 | DOUT / SDA | — |
| Mic SEL (canal L) | GND | SEL / L-R | — |
| Mic VDD | 3V3 | VCC | — |
| Amp BCLK (master out) | GPIO 14 | — | BCLK |
| Amp LRC / WS (master out) | GPIO 13 | — | LRC |
| Amp DIN (data out) | GPIO 15 | — | DIN |
| Amp GAIN_SLOT | desconectado | — | GAIN (9 dB default) |
| Amp SD_MODE | 3V3 | — | SD_MODE → mix (L+R)/2 |
| Amp VIN | 5V (VBUS) | — | VIN |
| Amp GND | GND | — | GND |
| Amp OUT+/OUT− | — | — | Speaker 8 Ω (bridge-tied, sem GND) |

---

## 6. Notas de Integração

- **Sem MCLK**: o MAX98357A não requer MCLK — poupa 1 GPIO (confirmado no
  datasheet).
- **Strapping pin GPIO 15**: também é `MTDO`. O DIN do MAX98357A é entrada
  com pull-down interno no amp, mantendo o pino baixo no boot — sem conflito
  de strapping. Evite pull-up externo neste pino.
- **GPIO 12 (MTDI) NÃO usado**: permanece livre, evitando o problema de
  tensão de flash no boot.
- **PSRAM ausente** (confirmado por diagnóstico): buffers de áudio residem na
  SRAM (~325 KB livres). Um buffer de anel de 16 kHz · 16-bit · 250 ms ≈
  8 KB — folga ampla.
- **Compartilhamento de BCLK/WS**: só é possível entre mic e amp se rodarem
  na mesma sample rate. Como diferem (48 kHz vs 16 kHz), usam-se periféricos
  I2S separados (I2S0 = RX, I2S1 = TX).
- **Ganho do amp**: GAIN_SLOT desconectado = 9 dB (volume moderado). Subir
  para 12 dB (GND direto) se o TTS ficar baixo a 1 m.
- **Speaker bridge-tied**: OUT+ e OUT− vão direto ao speaker, **sem GND** e
  sem filtro (Filterless Class D).

---

## 7. Referências

- `hardware/esp32-wroom-32e-n4.md` — specs e diagnóstico do MCU
- `hardware/BOM-audio.md` — pinagem I2S e specs de SPH0645LM4H / MAX98357A
- Datasheet SPH0645LM4H-B (Knowles) — Adafruit CDN
- Datasheet MAX98357A (Maxim/Analog Devices) — Adafruit CDN
