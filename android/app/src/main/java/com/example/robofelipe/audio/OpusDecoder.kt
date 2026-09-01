package com.example.robofelipe.audio

import android.util.Log

class OpusDecoder(
    private val sampleRate: Int,
    private val channels: Int,
    frameSizeMs: Int,
) {
    private var nativeDecoderHandle: Long = 0L
    private val frameSize: Int = (sampleRate * frameSizeMs) / 1000

    init {
        nativeDecoderHandle = nativeInitDecoder(sampleRate, channels)
        if (nativeDecoderHandle == 0L) {
            throw IllegalStateException("Failed to initialize Opus decoder")
        }
    }

    fun decode(opusData: ByteArray): ByteArray? {
        val maxPcmSize = frameSize * channels * 2
        val pcmBuffer = ByteArray(maxPcmSize)
        val decodedBytes = nativeDecodeBytes(
            nativeDecoderHandle, opusData, opusData.size, pcmBuffer, maxPcmSize
        )
        return if (decodedBytes > 0) {
            if (decodedBytes < pcmBuffer.size) pcmBuffer.copyOf(decodedBytes) else pcmBuffer
        } else {
            null
        }
    }

    fun release() {
        if (nativeDecoderHandle != 0L) {
            nativeReleaseDecoder(nativeDecoderHandle)
            nativeDecoderHandle = 0
        }
    }

    @Suppress("removal")
    protected fun finalize() = release()

    private external fun nativeInitDecoder(sampleRate: Int, channels: Int): Long
    private external fun nativeDecodeBytes(
        decoderHandle: Long,
        inputBuffer: ByteArray,
        inputSize: Int,
        outputBuffer: ByteArray,
        maxOutputSize: Int,
    ): Int
    private external fun nativeReleaseDecoder(decoderHandle: Long)

    companion object {
        private const val TAG = "OpusDecoder"

        init {
            System.loadLibrary("app")
        }
    }
}
