# 02 — Abordagem de treinamento do modelo KWS "Hey Felipe"

## Type
grilling

## Status
open

## Assignee
unclaimed

## Blocked by
none

## Question

Como treinar e deployar o modelo KWS (keyword spotting) para a palavra
de ativação "Hey Felipe" no ESP32-WROOM-32E-N4?

ADR-005 especifica: TFLM (TensorFlow Lite Micro), modelo int8
quantizado, ~20–40 KB de pesos, 16 kHz / 16-bit / mono, janela de ~1s
com hop de 200–500ms, features MFCC ou log-mel spectrogram, arquitetura
DS-CNN ou CNN pequena.

Opções de obtenção do modelo:
1. **Edge Impulse** — treina e exporta diretamente para ESP32 (C++).
   Dataset de áudio com augmentation (ruído de servo + ambiente).
2. **ML Commons / TFLite Micro examples** — modelos de referência
  ("yes/no") adaptados.
3. **Treinamento próprio** com dataset custom + TFLM export manual.

Recomendação preliminar: Edge Impulse (workflow mais rápido, export
direto para ESP-IDF, augmentation embutida). Dataset: ~500–1000
amostras de "Hey Felipe" com vozes diferentes + ruído de servo.

Decidir: ferramenta, dataset, arquitetura do modelo, e como integrar
o modelo exportado ao firmware ESP-IDF (ADR-014).
