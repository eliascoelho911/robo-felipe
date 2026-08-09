# Guia de Montagem e Programação por Lição

Robô Bípede ACEBOTT QD021 (ESP32)

## Mapa de GPIOs

| Componente | GPIO | Fios |
|------------|------|------|
| Coxa esquerda | GPIO5 | Vermelho=5V, Marrom=GND, Laranja=Sinal |
| Panturrilha esquerda | GPIO16 | Vermelho=5V, Marrom=GND, Laranja=Sinal |
| Panturrilha direita | GPIO17 | Vermelho=5V, Marrom=GND, Laranja=Sinal |
| Coxa direita | GPIO18 | Vermelho=5V, Marrom=GND, Laranja=Sinal |
| Sensor TRIG (branco) | GPIO13 | — |
| Sensor ECHO (azul) | GPIO14 | — |
| Sensor VCC (vermelho) | 5V | — |
| Sensor GND (preto) | GND | — |

## Regras de movimento dos servos

| Servo | GPIO | Posição | Regra |
|-------|------|---------|-------|
| Coxa esquerda | GPIO5 | Coxa Esq. | Maior ângulo → gira para o lado interno |
| Panturrilha esq. | GPIO16 | Perna Esq. | Maior ângulo → gira para o lado interno |
| Panturrilha dir. | GPIO17 | Perna Dir. | Maior ângulo → gira para o lado externo |
| Coxa direita | GPIO18 | Coxa Dir. | Maior ângulo → gira para o lado externo |

---

# Lição 1 — Introdução ao Robô Bípedo (montagem das pernas)

## Objetivo
Montar as pernas e centralizar os servos em 90°. Ao fim desta lição o robô terá as duas pernas montadas com servos calibrados.

## Montagem (Passos 1–7)

### Passo 1 — Identificar os pés esquerdo e direito
- Observe a inclinação do furo do calcanhar:
  - Inclinado para a **direita** → pé **esquerdo**
  - Inclinado para a **esquerda** → pé **direito**
- Remova a película protetora das peças de acrílico
- Os furos dos calcanhares ficam voltados para o interior

### Passo 2 — Montar a estrutura dos pés
- Peças: acrílico do pé esq/dir, placa de fixação do servo da perna (×2), suporte acrílico do pé (×2), porca M3 (×4), parafuso M3×10 (×4)
- O suporte acrílico do pé é mais curto que o da coxa

### Passo 3 — Instalar os servos das pernas
- Peças: 2 estruturas dos pés, 2 servos, 4 parafuso M2×10, 4 porca M2

### Passo 4 — Instalar o braço curto do servo
- Peças: 2 braço curto, 2 placa de fixação, 4 parafuso autoatarraxante M1.7×6
- A saliência do braço aponta para o furo circular
- Faça duas vezes (um para cada perna)

### Passo 5 — Instalar o braço longo do servo
- Peças: 2 braço longo, 2 placa de fixação, 4 parafuso autoatarraxante M1.7×6
- Use os furos externos da peça

### Passo 6 — Montar o suporte das coxas
- Peças: 2 placa de fixação montada, 2 meio braço, 2 suporte das pernas, 4 porca M3, 4 parafuso M3×10
- O braço do servo fica voltado para fora

### Passo 7 — Fixar os servos das coxas
- Peças: 2 servo, 1 placa de fixação da coxa, 4 porca M2, 4 parafuso M2×10
- Os eixos dos servos apontam para o lado da largura da peça acrílica

## ⚠️ Centralização dos servos (Passos 8–10)

> CRÍTICO: Os servos devem estar em 90° antes da fixação mecânica.
> O programa `servo_90.ino` já está gravado na ESP32. Conecte cada servo temporariamente ao seu GPIO e ligue a alimentação para posicioná-lo em 90°.

### Passo 8 — Fixar as coxas
- Conecte: **coxa esquerda → GPIO5**, **coxa direita → GPIO18**
- Ligue a alimentação → servos vão para 90°
- Fixe a estrutura da perna no suporte com M2.5×4
- Nunca force o servo após energizá-lo

### Passo 9 — Instalar a perna esquerda
- Conecte: **panturrilha esquerda → GPIO16** (90°)
- Fixe com M2.5×4

### Passo 10 — Instalar a perna direita
- Conecte: **panturrilha direita → GPIO17** (90°)
- Fixe com M2.5×4
- **Desligue a alimentação** ao concluir

### Passo 11 — Parafusos das pernas
- 2 parafuso M3×10 + 2 porca travante M3
- Não aperte demais — a articulação deve girar livremente

## Programa da Lição 1
- `sketches/Lição1/servo_90/servo_90.ino` — centraliza os 4 servos em 90° (já gravado)
- `sketches/Lição1/Hello_esp32/Hello_esp32.ino` — teste básico da placa

---

# Lição 2 — Movimentos básicos (estrutura superior)

## Objetivo
Completar a montagem (estrutura superior, ESP32, bateria) e testar os movimentos básicos: frente, trás, esquerda, direita, e controle por porta serial.

## Montagem (Passos 12–15)

### Passo 12 — Instalar os espaçadores
- 4 parafuso M3×10 + 4 espaçador duplo M3×25

### Passo 13 — Instalar o suporte da placa ESP32
- 1 placa acrílica da placa-mãe, 4 coluna de cobre M3×10, 4 parafuso M3×6
- Face impressa da placa para cima

### Passo 14 — Instalar a caixa de baterias
- 4 parafuso M3×10 + 4 porca M3 + 1 caixa de baterias
- O lado do cabo fica voltado para o conector de alimentação da ESP32

### Passo 15 — Fixar a ESP32
- 1 ESP32 + 3 parafuso M3×6
- Use apenas três parafusos

## Ligações elétricas (servos)

Conecte os 4 servos conforme o mapa de GPIOs no topo deste guia:
- Coxa esquerda → GPIO5
- Panturrilha esquerda → GPIO16
- Panturrilha direita → GPIO17
- Coxa direita → GPIO18
- Todos: Vermelho=5V, Marrom=GND, Laranja=Sinal

## Programas da Lição 2

| Programa | Ação |
|----------|------|
| `sketches/Lição2/Move_Forward/Move_Forward.ino` | Andar para frente |
| `sketches/Lição2/Move_Backward/Move_Backward.ino` | Andar para trás |
| `sketches/Lição2/Turn_Left/Turn_Left.ino` | Girar à esquerda |
| `sketches/Lição2/Turn_Right/Turn_Right.ino` | Girar à direita |
| `sketches/Lição2/Serial_Control/Serial_Control.ino` | Controle por porta serial |

### Controle por porta serial
Após gravar `Serial_Control.ino`, abra o monitor serial (115200 baud) e digite:
- `forward` → avança
- `backward` → recua
- `left` → gira esquerda
- `right` → gira direita

> Nota: o robô não consegue levantar bem as pernas sozinho no início. Você pode pressionar a palma do pé alternadamente com a mão para observar/ajudar o movimento.

---

# Lição 3 — Função de Seguimento (sensor ultrassônico)

## Objetivo
Montar o sensor ultrassônico e programar o robô para seguir objetos.

## Montagem (Passos 16–17)

### Passo 16 — Montar o sensor ultrassônico
- 1 sensor ultrassônico, 1 suporte, 4 porca M2, 4 parafuso M2×10
- Os pinos do sensor ficam voltados para cima

### Passo 17 — Fixar o sensor ultrassônico
- 1 suporte do sensor, 1 suporte da ESP32, 1 porca M3, 1 parafuso M3×10

### Passo 18 — Unir a parte superior e inferior
- 4 parafuso M3×10

### Passo 19 — Colocar as almofadas
- 8 almofadas (4 em cada pé)

## Ligações elétricas (sensor)
- TRIG (branco) → GPIO13
- ECHO (azul) → GPIO14
- VCC (vermelho) → 5V
- GND (preto) → GND

## Programa da Lição 3
- `sketches/Lição3/Move_Follow/Move_Follow.ino`

### Como funciona
- Distância < 15 cm → robô recua
- Distância 15–20 cm → robô para
- Distância 20–35 cm → robô avança
- Outro → para

Teste: coloque a mão à frente do sensor e aproxime/afaste para ver o robô seguir.

---

# Lição 4 — Função de Desvio de Obstáculos

## Objetivo
O robô detecta obstáculos e desvia sozinho.

## Programa da Lição 4
- `sketches/Lição4/Move_Avoid/Move_Avoid.ino`

### Como funciona
- Distância ≤ 15 cm → para e recua 6 vezes
- Distância 15–20 cm → escolhe aleatoriamente virar esquerda ou direita (10 vezes)
- Distância > 20 cm → avança

---

# Lição 5 — Movimentos de Dança (1)

## Programa da Lição 5
- `sketches/Lição5/Move_Dance1/Move_Dance1.ino`

O robô balança os tornozelos esquerda e direita, repetindo 4 vezes.

---

# Lição 6 — Movimentos de Dança (2)

## Programa da Lição 6
- `sketches/Lição6/Move_Dance2/Move_Dance2.ino`

Sequência mais complexa: passo a passo 4x, balanço de tornozelos 4x, passos espaciais 4x.

---

# Lição 7 — Controle Web

## Programa da Lição 7
- `sketches/Lição7/Biped_Robot_Web/Biped_Robot_Web.ino`

### Como usar
1. Grave o programa na ESP32
2. Conecte-se ao WiFi **`Biped_Robot`** (senha: `12345678`)
3. Abra o navegador em **`192.168.4.1`**
4. Use a interface web para controlar o robô

> O nome/senha do WiFi podem ser personalizados no código (útil para distinguir vários robôs).

---

# Lição 8 — Controle por APP

## Programa da Lição 8
- `sketches/Lição8/Biped_Robot_App/Biped_Robot_App.ino`

### Como usar
1. Baixe o app ACEBOTT (App Store iOS ou Google Play Android)
2. Grave `Biped_Robot_App.ino` na ESP32
3. Conecte-se ao WiFi **`Biped_Robot`** (senha: `12345678`)
4. Abra o app, selecione o robô bípede → Control
5. Toque no ícone de conexão (canto superior direito)

### Controles do app
- Lado esquerdo do painel: frente/trás/esquerda/direita
- Lado direito: grupo de ações (chute esq/dir, pisada esq/dir, correr, dançar, seguir, evitar obstáculos)
- Canto superior direito: controle por giroscópio do celular

---

# Comandos rápidos

Gravar um sketch (substitua `<caminho>` e `<porta>`):
```bash
arduino-cli compile --fqbn esp32:esp32:esp32 "<caminho>.ino"
arduino-cli upload -p /dev/ttyUSB0 --fqbn esp32:esp32:esp32 "<caminho>.ino"
```

Exemplo — gravar Lição 2 (frente):
```bash
arduino-cli compile --fqbn esp32:esp32:esp32 "sketches/Lição2/Move_Forward/Move_Forward.ino"
arduino-cli upload -p /dev/ttyUSB0 --fqbn esp32:esp32:esp32 "sketches/Lição2/Move_Forward/Move_Forward.ino"
```

Monitor serial (115200 baud) — use Ctrl+A para sair:
```bash
screen /dev/ttyUSB0 115200
```
