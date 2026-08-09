/*
 * stream_mic_serial.ino  (v4 - DECIMATION + DC REMOVAL)
 * ------------------------------------------------------------------
 * Captura audio do GY-SPH0645 (I2S 48 kHz STEREO 32-bit) e faz stream
 * via Serial USB em 16 kHz 16-bit mono (decimado por 3).
 *
 * Pinagem:
 *   GY-SPH0645   ->  ESP32-WROOM-32E
 *   VCC          ->  3V3
 *   GND          ->  GND
 *   SEL (L-R)    ->  GND          (canal esquerdo)
 *   BCLK (SCL)   ->  GPIO 26
 *   WS (LRCLK)   ->  GPIO 25
 *   DOUT (SDA)   ->  GPIO 27
 *
 * Pipeline:
 *   1. I2S STEREO 48 kHz 32-bit -> BCLK 3,072 MHz (spec OK)
 *   2. Extrai canal L (indice par)
 *   3. Shift >> 16 (32-bit -> 16-bit)
 *   4. Remove DC offset (high-pass filter one-pole)
 *   5. Decima por 3 (48 kHz -> 16 kHz)
 *   6. Envia 16-bit 16 kHz mono = 32.000 bytes/s (cabe em 921600 baud)
 * ------------------------------------------------------------------
 */

#include <ESP_I2S.h>

#define I2S_BCLK  26
#define I2S_WS    25
#define I2S_DIN   27

#define SAMPLE_RATE_IN   48000   // captura
#define SAMPLE_RATE_OUT  16000   // stream (= 48000 / 3)
#define DECIMATION       3       // 48 kHz / 3 = 16 kHz

#define BLOCK_FRAMES     256     // frames stereo lidos por vez
#define FRAME_BYTES_32   8       // 4 bytes L + 4 bytes R
#define BLOCK_BYTES_READ (BLOCK_FRAMES * FRAME_BYTES_32)  // 2048

#define BLOCK_OUT_SAMPLES (BLOCK_FRAMES / DECIMATION)  // 85 amostras out
#define BLOCK_OUT_BYTES   (BLOCK_OUT_SAMPLES * 2)      // 170 bytes

I2SClass i2s;

void setup() {
  Serial.begin(921600);
  while (!Serial) { ; }
  Serial.println();
  Serial.println(F("[boot] v4 - 48kHz capture, 16kHz stream, DC-removed"));

  i2s.setPins(I2S_BCLK, I2S_WS, -1, I2S_DIN);

  bool ok = i2s.begin(I2S_MODE_STD,
                      SAMPLE_RATE_IN,
                      I2S_DATA_BIT_WIDTH_32BIT,
                      I2S_SLOT_MODE_STEREO,
                      -1,
                      I2S_ROLE_MASTER);

  if (!ok) {
    Serial.println(F("[erro] I2S falhou."));
    while (true) { delay(1); }
  }

  Serial.println(F("[boot] OK. Stream: 16 kHz, 16-bit, mono, PCM raw."));
  Serial.println(F("[boot] ffmpeg: -f s16le -ar 16000 -ac 1 -i audio.raw audio.wav"));
  Serial.println();
}

void loop() {
  int32_t stereo[BLOCK_FRAMES * 2];

  size_t lidos = i2s.readBytes((char *)stereo, BLOCK_BYTES_READ);
  if (lidos != BLOCK_BYTES_READ) return;

  // DC blocker: one-pole high-pass filter
  // y[n] = x[n] - x[n-1] + alpha * y[n-1]
  // alpha = 0.995 (corte ~24 Hz a 16 kHz)
  static int16_t prev_x = 0;
  static int16_t prev_y = 0;
  const float alpha = 0.995;

  int16_t out16[BLOCK_OUT_SAMPLES];
  int out_idx = 0;

  for (int i = 0; i < BLOCK_FRAMES; i++) {
    // Extrai canal L e trunca para 16-bit
    int16_t x = (int16_t)(stereo[i * 2] >> 16);

    // Remove DC offset
    int16_t y = (int16_t)(x - prev_x + alpha * prev_y);
    prev_x = x;
    prev_y = y;

    // Decima por 3: so guarda 1 a cada 3 amostras
    if (i % DECIMATION == 0 && out_idx < BLOCK_OUT_SAMPLES) {
      out16[out_idx++] = y;
    }
  }

  if (out_idx > 0) {
    Serial.write((const uint8_t *)out16, out_idx * 2);
  }
}
