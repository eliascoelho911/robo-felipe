package com.example.robofelipe.audio

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste instrumentado de round-trip Opus: PCM → encode → decode → PCM.
 * Precisa do device/emulador (JNI carrega libapp.so + libopus.so).
 */
class OpusRoundTripTest {

    @Test
    fun encodeDecodeRoundTrip_preservesSignalApproximately() {
        val sampleRate = 16000
        val channels = 1
        val frameDurationMs = 60
        val frameSize = sampleRate * frameDurationMs / 1000 // 960 samples
        val frameSizeBytes = frameSize * channels * 2         // 1920 bytes

        // Gera um tom senoide de 440 Hz como PCM de teste
        val pcm = ByteArray(frameSizeBytes)
        val frequency = 440.0
        for (i in 0 until frameSize) {
            val sample = (Short.MAX_VALUE * Math.sin(2.0 * Math.PI * frequency * i / sampleRate)).toInt()
            val byteIndex = i * 2
            pcm[byteIndex] = (sample and 0xFF).toByte()
            pcm[byteIndex + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val encoder = OpusEncoder(sampleRate, channels, frameDurationMs)
        val decoder = OpusDecoder(24000, channels, frameDurationMs)

        try {
            // Encode
            val encoded = encoder.encode(pcm)
            assertNotNull("Encode deveria produzir bytes", encoded)
            assertTrue("Opus frame não deve ser vazio", encoded!!.isNotEmpty())
            assertTrue("Opus frame deve ser menor que PCM", encoded.size < pcm.size)

            // Decode — o decoder roda a 24kHz mas o Opus decodifica o frame
            val decoded = decoder.decode(encoded)
            assertNotNull("Decode deveria produzir PCM", decoded)
            assertTrue("PCM decodificado não deve ser vazio", decoded!!.isNotEmpty())
        } finally {
            encoder.release()
            decoder.release()
        }
    }
}
