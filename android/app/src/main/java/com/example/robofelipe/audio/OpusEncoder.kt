package com.example.robofelipe.audio

import android.util.Log

class OpusEncoder(
    private val sampleRate: Int,
    private val channels: Int,
    frameSizeMs: Int,
) {
    private var nativeEncoderHandle: Long = 0L
    private val frameSize: Int = (sampleRate * frameSizeMs) / 1000

    init {
        nativeEncoderHandle = nativeInitEncoder(sampleRate, channels, OPUS_APPLICATION_VOIP)
        if (nativeEncoderHandle == 0L) {
            throw IllegalStateException("Failed to initialize Opus encoder")
        }
    }

    fun encode(pcmData: ByteArray): ByteArray? {
        val frameBytes = frameSize * channels * 2
        if (pcmData.size != frameBytes) {
            Log.e(TAG, "Input buffer size must be $frameBytes bytes (got ${pcmData.size})")
            return null
        }
        val outputBuffer = ByteArray(frameBytes)
        val encodedBytes = nativeEncodeBytes(
            nativeEncoderHandle, pcmData, pcmData.size, outputBuffer, outputBuffer.size
        )
        return if (encodedBytes > 0) outputBuffer.copyOf(encodedBytes) else null
    }

    fun release() {
        if (nativeEncoderHandle != 0L) {
            nativeReleaseEncoder(nativeEncoderHandle)
            nativeEncoderHandle = 0
        }
    }

    @Suppress("removal")
    protected fun finalize() = release()

    private external fun nativeInitEncoder(sampleRate: Int, channels: Int, application: Int): Long
    private external fun nativeEncodeBytes(
        encoderHandle: Long,
        inputBuffer: ByteArray,
        inputSize: Int,
        outputBuffer: ByteArray,
        maxOutputSize: Int,
    ): Int
    private external fun nativeReleaseEncoder(encoderHandle: Long)

    companion object {
        private const val TAG = "OpusEncoder"
        private const val OPUS_APPLICATION_VOIP = 2048

        init {
            System.loadLibrary("app")
        }
    }
}
