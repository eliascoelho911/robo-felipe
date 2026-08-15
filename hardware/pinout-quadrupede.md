# Pinout do Robô Felipe — Quadrúpede

> Pinagem consolidada do MCU principal (ESP32-WROOM-32E-N4) para o cão
> robô. Derivada dos ADRs 008–014 e do `BOM-audio.md`.
> O ESP32-CAM é um nó separado (ver ADR-012) — não partilha GPIO com o
> WROOM.

---

## Alocação de GPIO (14 alocados, 12 livres)

| GPIO | Função | Subsistema | Periférico | Notas |
|:---:|:---|:---|:---|:---|
| 4 | Ultrassom TRIG | Sensores | GPIO output | Realocado (era 13 no bípede — conflito com amp) |
| 5 | Servo — pata FE (front-left) | Locomoção | LEDC (servo_dog_ctrl) | ⚠️ Strapping (VDD_SDIO). Pull-up interno → HIGH no boot. Servo é HiZ, OK |
| 13 | Amp LRC / WS (I2S1 TX) | Áudio | I2S1 | 🔁 Era ultrassom TRIG no bípede |
| 14 | Amp BCLK (I2S1 TX) | Áudio | I2S1 | 🔁 Era ultrassom ECHO no bípede |
| 15 | Amp DIN (I2S1 TX) | Áudio | I2S1 | ⚠️ Strapping (MTDO). Pull-down interno do amp → LOW no boot, OK |
| 16 | Servo — pata FE (front-right) | Locomoção | LEDC (servo_dog_ctrl) | |
| 17 | Servo — pata FD (back-left) | Locomoção | LEDC (servo_dog_ctrl) | |
| 18 | Servo — pata FD (back-right) | Locomoção | LEDC (servo_dog_ctrl) | |
| 21 | I2C SDA | I2C (multi-drop) | I2C | OLED SSD1306 + MPU6050 |
| 22 | I2C SCL | I2C (multi-drop) | I2C | OLED SSD1306 + MPU6050 |
| 25 | Mic WS / LRCLK (I2S0 RX) | Áudio | I2S0 | |
| 26 | Mic BCLK (I2S0 RX) | Áudio | I2S0 | |
| 27 | Mic DOUT (I2S0 RX) | Áudio | I2S0 | |
| 34 | Ultrassom ECHO | Sensores | GPIO input | Input-only (sem output/pull). Ideal para ECHO |

---

## GPIO livres (12)

| GPIO | Usabilidade | Observação |
|:---:|:---|:---|
| 0 | ⚠️ Strapping (BOOT) | Botão de boot. Evitar output |
| 1 | UART0 TX | Reservar para flash/console |
| 2 | ⚠️ Strapping | Evitar HIGH no boot |
| 3 | UART0 RX | Reservar para flash/console |
| 12 | ⚠️ Strapping (MTDI) | **DEVE ser LOW no boot** — evitar output HIGH |
| 19 | ✅ Output-capable | Livre para expansão |
| 23 | ✅ Output-capable | Livre para expansão |
| 32 | ✅ Output-capable | ADC1_CH4, touch. Livre |
| 33 | ✅ Output-capable | ADC1_CH5, touch. Livre |
| 35 | ✅ Input-only | ADC1_CH7 |
| 36 | ✅ Input-only | VP, ADC1_CH0 (ruidoso) |
| 39 | ✅ Input-only | VN, ADC1_CH3 (ruidoso) |

---

## Strapping pins — restrições de boot

| GPIO | Nome | Restrição | Uso atual | Seguro? |
|:---:|:---|:---|:---|:---|
| 0 | BOOT | HIGH = flash boot; LOW = download | livre | n/a |
| 2 | — | LOW no boot (pull-down) | livre | n/a |
| 5 | VDD_SDIO | HIGH = flash timing default | Servo FE | ✅ HiZ, pull-up ganha |
| 12 | MTDI | **DEVE ser LOW** — HIGH → 1.8V flash falha | livre | evitar output HIGH |
| 15 | MTDO | LOW muda clock de boot (ainda boota) | Amp DIN | ✅ pull-down do amp |

---

## Realocação do ultrassom (bípede → cão)

O bípede usava TRIG→GPIO 13, ECHO→GPIO 14 (`tutorial/GUIA_POR_LICAO.md:13-14`).
No cão, GPIO 13 e 14 são BCLK/LRC do amp I2S (ver `BOM-audio.md:184`).
Realocação:

- **TRIG → GPIO 4** (output, non-strapping, livre)
- **ECHO → GPIO 34** (input-only, ideal para leitura, poupa GPIO output-capable)

---

## Subsistema de áudio (inalterado do bípede)

Ver `BOM-audio.md` e `esquema-audio-esp32-sph0645-max98357.md` para detalhes.

| Função | GPIO | Componente |
|:---|:---:|:---|
| Mic BCLK (master out) | 26 | SPH0645LM4H |
| Mic WS / LRCLK (master out) | 25 | SPH0645LM4H |
| Mic DOUT (data in) | 27 | SPH0645LM4H |
| Amp BCLK (master out) | 14 | MAX98357A |
| Amp LRC / WS (master out) | 13 | MAX98357A |
| Amp DIN (data out) | 15 | MAX98357A |

---

## Subsistema de locomoção (servo_dog_ctrl)

Ver ADR-010 e ADR-013. Pins passados em runtime ao `servo_dog_ctrl_init()`:

```c
servo_dog_ctrl_config_t cfg = {
    .fl_gpio_num = GPIO_NUM_5,   // Front-Left
    .fr_gpio_num = GPIO_NUM_16,  // Front-Right
    .bl_gpio_num = GPIO_NUM_17,  // Back-Left
    .br_gpio_num = GPIO_NUM_18,  // Back-Right
};
```

Ângulos neutros precisam recalibração para o chassi adaptado (defaults
do ESP-HI são 70/110/110/70 — ver ADR-013).

---

## I2C (multi-drop) — OLED + IMU

| GPIO | Função | Dispositivos |
|:---:|:---|:---|
| 21 | SDA | OLED SSD1306 128×64 + MPU6050 (IMU) |
| 22 | SCL | OLED SSD1306 128×64 + MPU6050 (IMU) |

Endereços I2C (a confirmar nos datasheets):
- OLED SSD1306: 0x3C (ou 0x3D)
- MPU6050: 0x68 (ou 0x69 se AD0=HIGH)

---

## ESP32-CAM (nó separado — ADR-012)

O ESP32-CAM é um MCU autônomo na LAN, não partilha GPIO com o WROOM.
Ver `hardware/esp32-cam-datasheet.md` para pinout do CAM (15 GPIO para
OV2640, 9 expostos, UART 16/17 para programação).

Conexão ao sistema:
- WiFi STA (mesmo router do WROOM)
- Stream de vídeo para serviço de backend (arquitetura a definir)
- Sem conexão física ao WROOM

---

## Resumo por subsistema

| Subsistema | GPIO usados | Quantidade |
|:---|:---|:---:|
| Áudio (I2S0 RX + I2S1 TX) | 25,26,27,13,14,15 | 6 |
| Locomoção (4 servos) | 5,16,17,18 | 4 |
| I2C (OLED + IMU) | 21,22 | 2 |
| Sensores (ultrassom) | 4,34 | 2 |
| **Total alocado** | | **14** |
| **Livres** | | **12** |
