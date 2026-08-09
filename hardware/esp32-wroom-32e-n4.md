# Overview Técnico: ESP32-WROOM-32E-N4

O **ESP32-WROOM-32E-N4** é um módulo de conectividade multiprotocolo de alto desempenho desenvolvido pela Espressif Systems. Ele combina Wi-Fi e Bluetooth (v4.2 BR/EDR e BLE) em um único encapsulamento compacto com antena integrada na placa (PCB), sendo amplamente utilizado no desenvolvimento de dispositivos para Internet das Coisas (IoT), automação residencial e redes de sensores.

---

## 🛠️ Especificações Técnicas

### Processador e Memória
* **SoC Base:** ESP32-D0WD-V3 (revisão 301 / ECO3 — confirmado por diagnóstico)
* **Arquitetura:** Xtensa Dual-Core 32-bit LX6
* **Frequência de Clock:** Até 240 MHz
* **Memória Flash:** 4 MB @ 80 MHz (Sufixo **N4** — confirmado por diagnóstico)
* **SRAM:** 520 KB (≈333 KB livres com firmware mínimo)
* **ROM:** 448 KB
* **PSRAM:** **NÃO DISPONÍVEL** (módulo sem sufixo R2 — confirmado por diagnóstico)

### Conectividade Sem Fio
* **Wi-Fi:** 802.11 b/g/n (2.4 GHz) com velocidade de até 150 Mbps
* **Bluetooth:** Bluetooth v4.2 BR/EDR e BLE (Bluetooth Low Energy)
* **Antena:** Integrada na própria placa (PCB Trace)

### Periféricos e Interfaces (Interfaces Seriais/I/O)
O módulo disponibiliza até **26 pinos GPIO**, suportando uma vasta gama de funções:
* Interfaces de comunicação: SPI, I2C, I2S e UART
* Conversor Analógico-Digital (ADC) e Digital-Analógico (DAC)
* Sensores de toque capacitivo integrados
* Canais PWM dedicados para controle de motores e dimerização de LEDs
* Interface para cartão SD (SDIO)

### Características Elétricas e Ambientais
* **Tensão de Operação:** 3.0 V a 3.6 V
* **Corrente Mínima da Fonte:** Recomendado fonte de no mínimo 500 mA
* **Temperatura de Operação:** -40 °C a +85 °C (faixa "N" — não é a versão "H" de -40 ~ +105 °C)

---

## 🔬 Valores Confirmados por Diagnóstico (2026-07-12)

Executado via sketch de diagnóstico (Arduino IDE, core ESP32). Resultados medidos nesta placa:

| Medida | Valor | Observação |
|:---|:---|:---|
| Modelo do chip | ESP32-D0WD-V3 | Corresponde ao datasheet |
| Revisão do chip | 301 (ECO3) | Revisão mais recente, sem bugs de PSRAM |
| Núcleos | 2 | Dual-core confirmado |
| Frequência CPU | 240 MHz | |
| Flash | 4194304 bytes (4.0 MB) @ 80 MHz | |
| Heap total | 377880 bytes (~369 KB) | SRAM disponível para o app |
| Heap livre | 333144 bytes (~325 KB) | Após boot, antes de alocar |
| Mínimo livre | 327504 bytes | Pico de uso no boot |
| PSRAM | **0 bytes** | Não detectada |
| `esp_psram_is_initialized()` | `false` | API ESP-IDF confirma |

---

## 🚀 Principais Aplicações
Devido ao balanço ideal entre poder de processamento, baixo consumo de energia (modos *Deep Sleep*) e conectividade avançada, o módulo se destaca em:
* Hubs de automação residencial e Smart Home
* Câmeras de segurança IP e streaming de áudio
* Dispositivos vestíveis (*wearables*) médicos e esportivos
* Monitoramento industrial e agricultura de precisão

---

## 📚 Links Úteis e Referências
* [Datasheet Oficial da Família ESP32-WROOM-32E (PDF)](https://espressif.com)
* [Documentação ESP-IDF (Ambiente de Desenvolvimento Oficial)](https://espressif.com)
