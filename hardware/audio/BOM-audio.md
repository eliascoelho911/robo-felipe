# BOM - Componentes de Áudio (verificado por datasheet)

> Baseado nos ADRs 001, 005, 006 e verificação direta dos datasheets dos
> fabricantes via docling. Datasheets processados:
> - SPH0645LM4H-B (Knowles) — Adafruit CDN
> - ICS-43434 (TDK/Invensense) — Adafruit CDN
> - MAX98357A/B (Maxim/Analog Devices) — Adafruit CDN

---

## Microfone: SPH0645LM4H-B

### Recomendação principal
**Adafruit I2S MEMS Microphone Breakout - SPH0645LM4H** (Adafruit #3421, USD 6.95)

### Por que o SPH0645LM4H e não o INMP441

O ADR-001 citava o INMP441 como exemplo ilustrativo de microfone I2S
MEMS. Após ler os datasheets oficiais (SPH0645LM4H e ICS-43434 via
Adafruit CDN; INMP441 não disponível em fonte primária), a recomendação
definitiva é o **SPH0645LM4H** por três razões:

1. **SNR superior**: 65 dB(A) no SPH0645LM4H vs ~61 dB(A) no INMP441
   (fontes secundárias — não consegui obter o datasheet oficial do
   INMP441). Os 4 dB extras de SNR melhoram diretamente a precisão do
   KWS em ambientes ruidosos — que é exatamente o caso de um robô
   conversacional com atuadores zumbindo ao lado.

2. **Em produção, de fornecedor confiável**: O SPH0645LM4H é o
   substituto oficial do ICS-43434 (que foi descontinuado — confirmado
   na página da Adafruit #6049). O INMP441 é amplamente vendido no
   AliExpress, mas tem risco de partes clone/segunda linha com specs
   inconsistentes.

3. **O problema dos 16 kHz é trivial de contornar** (ver abaixo).

### Specs verificadas do SPH0645LM4H (do datasheet)

| Parâmetro | Valor | Fonte no datasheet |
|:---|:---|:---|
| Interface | I2S, 24-bit, 2's complement, MSB first | Sec. "Data Format" |
| Precisão efetiva | 18 bits (bits não usados = zero) | Sec. "Data Format" |
| SNR | 65 dB(A) típico | Tabela 2, linha "Signal to Noise Ratio" |
| Sensibilidade | −26 dBFS (típico, 94 dB SPL @ 1 kHz) | Tabela 2 |
| AOP (Acoustic Overload Point) | 120 dB SPL (10% THD) | Tabela 2 |
| THD | 1% @ 110 dB SPL | Tabela 2 |
| Diretividade | Omnidirectional | Tabela 2 |
| VDD | 1.8 V (teste), 1.6–3.6 V (máx) | Tabela 2 / Abs Max |
| Corrente ativa | 600 µA típico | Tabela 2 |
| Corrente sleep | 10 µA | Tabela 2 |
| Porta acústica | Bottom ported | Descrição |
| L/R select | Sim (pino SEL: GND = left, VDD = right) | Pinout |
| I2S role | Slave (master fornece BCLK + WS) | Sec. "I2S Interface" |
| Oversampling ratio | Fixo em 64 (WS = BCLK/64) | Sec. "I2S Interface" |
| Clock range (BCLK) | 2.048–4.096 MHz | Sec. "I2S Interface" |
| **Sample rate nativo** | **32–64 kHz** | Sec. "I2S Interface" |
| Modos | Active (CLK > 900 kHz), Sleep (CLK < 900 kHz) | Sec. "Operating Modes" |

### O problema dos 16 kHz e como resolver

O SPH0645LM4H suporta oficialmente sample rates de **32 a 64 kHz**
(BCLK 2.048–4.096 MHz). O ADR-005 especifica 16 kHz para a pipeline
KWS/VAD. Para 16 kHz nativo seria preciso BCLK = 1.024 MHz, que está
abaixo do mínimo especificado de 2.048 MHz.

**Solução: capturar a 48 kHz e fazer downsample para 16 kHz no firmware.**

- Capturar a 48 kHz (BCLK = 3.072 MHz — dentro da spec do mic)
- Decimar por 3 (48 kHz → 16 kHz) com filtro anti-alias FIR simples
- Custo de CPU: ~1–2% de um core — trivial
- Já é prática padrão em pipelines TinyML (Edge Impulse faz exatamente isto)
- **Benefício extra**: sample rate maior no I2S = melhor headroom de
  anti-aliasing no ADC do que a 16 kHz direto

Alternativamente, capturar a 32 kHz (mínimo garantido do mic) e
decimar por 2 (32 → 16 kHz).

> Nota: A Adafruit diz no guide que o BCLK "pode rodar um pouco mais
> lento que 2 MHz e ainda funcionar". Mas isto não é garantido pelo
> datasheet — não confiar nisso para produção.

### Breakout da Adafruit (pinout)

| Pino breakout | Função | Conexão ESP32 |
|:---|:---|:---|
| 3V | VDD (1.6–3.6 V) | 3.3 V |
| GND | Ground | GND |
| BCLK | Bit clock (I2S master out) | GPIO 26 |
| DOUT | Data out (I2S data in) | GPIO 27 |
| LRCLK (WS) | Word select (I2S WS out) | GPIO 25 |
| SEL | L/R channel select | GND (left) |

### Alternativa: INMP441 (orçamento)

Se custo ou disponibilidade local (AliExpress/Mercado Livre Brasil)
forem fator decisivo, o INMP441 permanece aceitável:
- ~USD 2–5 no AliExpress (vs USD 6.95 da Adafruit)
- 24-bit I2S, suporta 16 kHz diretamente (amplo uso em projetos ESP32)
- SNR ~61 dB(A) — 4 dB pior que o SPH0645LM4H, mas funcional
- Risco: partes clone/segunda linha no AliExpress
- Pinout I2S idêntico (BCLK, DOUT, LRCLK, SEL, VDD, GND)

### Descartado: ICS-43434

**DISCONTINUADO** — confirmado na página Adafruit #6049:
> "Please note: The ICS43434 has been discontinued. SPH0645LM4H is a
> drop-in replacement!"

Embora tivesse sido ideal (modo low-power suportando 16 kHz direto a
230 µA), não é mais comprável de fonte confiável.

---

## Amplificador: MAX98357A (confirmado)

### Recomendação
**Adafruit I2S 3W Class D Amplifier Breakout - MAX98357A** (Adafruit #3006, USD 5.95)

### Por que o MAX98357A é confirmado como a melhor escolha

O datasheet confirma todos os requisitos do projeto com folga:

1. **16 kHz explicitamente suportado**:
   - LRCLK Range 2: 15.2–16.8 kHz (nominal 16 kHz) — Tabela no datasheet
   - **Voice Mode IIR Lowpass Filter** ativa automaticamente quando
     LRCLK < 30 kHz — otimizada para voz, perfeito para TTS

2. **Sem MCLK**: Confirmado no datasheet — "eliminates the need for the
   external MCLK signal". Poupa 1 GPIO no ESP32.

3. **16-bit suportado**: "16/24/32-bit data for I2S and left-justified
   modes"

4. **Mono (L+R)/2**: Configurável via pino SD_MODE — pega os dois canais
   e faz mix mono. Ideal para um speaker único.

5. **Filterless Class D**: Não precisa de filtro de saída — speaker
   conectado direto nos pinos OUT+ e OUT−.

6. **Eficiência 92%** — excelente para bateria.

7. **THD+N: 0.02% @ 1W** — qualidade de áudio muito boa.

### Specs verificadas do MAX98357A (do datasheet)

| Parâmetro | Valor | Fonte no datasheet |
|:---|:---|:---|
| Tipo | PCM input Class D power amplifier | Descrição |
| Formatos | I2S (MAX98357A) / Left-justified (MAX98357B) | Descrição |
| Bit depths | 16/24/32-bit (I2S mode) | Descrição |
| Sample rates | 8 kHz, 16 kHz, 48 kHz, 96 kHz (ranges explícitos) | Tabela LRCLK Ranges |
| MCLK | Não requerido | Descrição |
| Filtro DAC | Voice mode IIR (LRCLK < 30 kHz), Audio mode FIR (30–50 kHz), Audio mode FIR (>50 kHz) | Tabela DAC Filters |
| Saída | Mono (configurável: L, R, ou (L+R)/2) | Descrição |
| Potência @ 5V | 3.2 W @ 4Ω (10% THD), 1.8 W @ 8Ω (10% THD) | Tabela Electrical |
| Potência @ 3.7V (LiPo) | 0.93 W @ 8Ω (10% THD) | Tabela Electrical |
| Potência @ 5V (1% THD) | 2.5 W @ 4Ω, 1.4 W @ 8Ω | Tabela Electrical |
| THD+N | 0.02% @ 1W, 1 kHz | Tabela Electrical |
| DR (Dynamic Range) | 105 dB | Tabela Electrical |
| Eficiência | 92% @ 8Ω, 10% THD, 1 kHz | Tabela Electrical |
| VDD | 2.7–5.5 V | Abs Max |
| Corrente quiescente | 2.4 mA @ 3.7 V | Tabela Electrical |
| Corrente shutdown | 0.6 µA típico | Tabela Electrical |
| Corrente standby | 340 µA (sem BCLK) | Tabela Electrical |
| Gains selecionáveis | 3, 6, 9, 12, 15 dB (pino GAIN_SLOT) | Tabela Gain |
| Switching freq | 330 kHz (Class D) | Tabela Electrical |
| Proteção | Thermal shutdown + over-current (2.8 A limit) | Tabela Electrical |

### Atenção: MAX98357A vs MAX98357B

- **MAX98357A**: suporta formato **I2S** ← este é o que precisamos
- **MAX98357B**: suporta formato **left-justified** (não I2S)
- Ambos suportam TDM
- A breakout da Adafruit (#3006) é a versão **A** — a correta para ESP32

### Breakout da Adafruit (pinout)

| Pino breakout | Função | Conexão ESP32 |
|:---|:---|:---|
| VIN | VDD (2.7–5.5 V) | 5 V (USB) ou bateria |
| GND | Ground | GND |
| DIN | Data in (I2S data out do ESP32) | GPIO 15 |
| BCLK | Bit clock (I2S BCLK out) | GPIO 14 |
| LRC | Left/Right clock (I2S WS out) | GPIO 13 |
| GAIN | Gain select (3/6/9/12/15 dB) | Ver tabela abaixo |
| SD | Shutdown / mode (L, R, ou mix) | Ver tabela abaixo |
| OUT+ / OUT− | Speaker (bridge tied, sem GND) | Speaker 8Ω |

### Configuração dos pinos GAIN e SD_MODE

**GAIN_SLOT** (define ganho e canal em I2S mode):
| GAIN_SLOT conexão | Ganho | Canal de saída |
|:---|:---|:---|
| GND via 100kΩ | 15 dB | — |
| GND direto | 12 dB | — |
| Desconectado | 9 dB (default) | — |
| VDD direto | 6 dB | — |
| VDD via 100kΩ | 3 dB | — |

**SD_MODE** (shutdown / seleção de canal):
| SD_MODE conexão | Estado | Saída |
|:---|:---|:---|
| 0 V (GND) | Shutdown | Mudo (0.6 µA) |
| ~0.77 V | Active | Canal esquerdo (L) |
| ~1.4 V | Active | Canal direito (R) |
| VDD | Active | Mix mono (L+R)/2 |

**Recomendação para o robô:**
- GAIN_SLOT: **desconectado** (9 dB — default, volume moderado)
- SD_MODE: **VDD** (mix mono (L+R)/2 — pois só temos 1 speaker)

### Alternativas descartadas

| Alternativa | Por que descartada |
|:---|:---|
| MAX98357B | Formato left-justified, não I2S — não serve para ESP32 I2S |
| PCM5102 (Adafruit #6250) | É **DAC line-level**, não amplifier. Precisaria de amp externo. 2 canais stereo (desperdiça para mono). Útil só se precisar de saída line-out para outro equipamento. |
| MAX98306 | Stereo analog input (não I2S). Precisaria de DAC antes. |
| PAM8302 | Analog input (não I2S). Mesmo problema. |
| UDA1334A | I2S stereo DAC line-out — não amplifier. Similar ao PCM5102. |

---

## Speaker (alto-falante)

### Recomendação
**8 Ω, 3 W, 40–50 mm** (alto-falante de amplo alcance comum)

### Por que 8 Ω e não 4 Ω

Do datasheet do MAX98357A:
- **8 Ω @ 5 V**: 1.8 W (10% THD) ou 1.4 W (1% THD)
- **4 Ω @ 5 V**: 3.2 W (10% THD) ou 2.5 W (1% THD)
- **8 Ω @ 3.7 V (LiPo)**: 0.93 W (10% THD)

Para um robô conversacional de bolso:
- 0.93 W a 1.4 W é volume suficiente para TTS indoor a ~1 m de distância
- 8 Ω reduz a corrente de pico vs 4 Ω (2.8 A limit no chip) — mais seguro
- 8 Ω é o impedance mais comum em speakers de 40–50 mm
- Speaker de 4 Ω extrairia mais potência, mas aquece mais o amp e
  drena mais bateria — troca desfavorável para um robô pequeno

### Specs do speaker recomendado

| Parâmetro | Valor |
|:---|:---|
| Impedância | 8 Ω |
| Potência nominal | 3 W (suporta os picos do MAX98357A) |
| Diâmetro | 40–50 mm (cabe no corpo do robô) |
| Sensibilidade | > 85 dB @ 1 W / 1 m (eficiência razoável) |
| Faixa de frequência | 300 Hz – 7 kHz mínimo (suficiente para voz TTS) |

> Speaker de 28–40 mm também funciona se espaço for crítico, mas a
> resposta em graves é pior. Para voz TTS, isto é aceitável — a maioria
> da energia de voz está entre 300 Hz e 3 kHz.

---

## Resumo da BOM

| Componente | Modelo | Fornecedor | Preço aprox. |
|:---|:---|:---|:---|
| Microfone I2S | SPH0645LM4H breakout | Adafruit #3421 | USD 6.95 |
| Amplificador I2S | MAX98357A breakout | Adafruit #3006 | USD 5.95 |
| Speaker | 8 Ω 3 W 40–50 mm | Qualquer loja de eletrônica | USD 1–3 |
| **Total áudio** | | | **~USD 14–16** |

### Se orçamento for crítico (alternativa AliExpress)

| Componente | Modelo | Preço aprox. |
|:---|:---|:---|
| Microfone I2S | INMP441 breakout | USD 2–5 |
| Amplificador I2S | MAX98357A breakout | USD 2–4 |
| Speaker | 8 Ω 3 W 40 mm | USD 1–2 |
| **Total áudio** | | **~USD 5–11** |

> Risco do AliExpress: partes clone/segunda linha podem ter specs piores
> que o datasheet oficial. O MAX98357A clone é geralmente funcional,
> mas o INMP441 clone pode ter SNR pior que o esperado.

---

## Pinagem I2S no ESP32 (planejada)

| Função | GPIO ESP32 | Pino do componente |
|:---|:---|:---|
| **Mic — BCLK** | GPIO 26 | BCLK (SPH0645 / INMP441) |
| **Mic — WS (LRCLK)** | GPIO 25 | LRCLK / WS |
| **Mic — DOUT (data in)** | GPIO 27 | DOUT |
| **Mic — SEL** | GND | SEL (left channel) |
| **Amp — BCLK** | GPIO 14 | BCLK (MAX98357A) |
| **Amp — LRC (WS)** | GPIO 13 | LRC |
| **Amp — DIN (data out)** | GPIO 15 | DIN |
| **Amp — GAIN** | desconectado | 9 dB (default) |
| **Amp — SD_MODE** | 3.3 V (ou GPIO p/ controle) | (L+R)/2 mix |
| **Amp — OUT+/OUT−** | — | Speaker 8Ω |

> Nota: O ESP32 tem 2 periféricos I2S (I2S0 e I2S0). Pode-se usar o
> mesmo periférico I2S para mic e amp (RX e TX no mesmo controlador),
> economizando recursos. Os pinos BCLK e WS podem ser compartilhados
> entre mic e amp **se rodarem na mesma sample rate**. Como o plano é
> capturar a 48 kHz (mic) e fazer playback a 16 kHz (TTS), usarão
> periféricos I2S separados ou reconfiguração dinâmica.

---

## ADRs atualizados

- **ADR-001**: Exemplo "INMP441" atualizado para "SPH0645LM4H"
  (o INMP441 era apenas ilustrativo no contexto, não uma decisão)
- **ADR-005**: Captura I2S alterada de 16 kHz direto para 48 kHz +
  decimação FIR por 3 → 16 kHz; pipeline atualizado com estágio de
  decimação; spec do modelo KWS mantém 16 kHz (a decimação acontece
  antes do ring buffer de inferência)
- **ADR-006**: Sem mudança — o stream para o relay continua PCM 16 kHz
  (a decimação é interna ao firmware, antes do path de rede)
