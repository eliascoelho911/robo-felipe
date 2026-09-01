package com.example.robofelipe.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class OpusStreamPlayer(
    private val sampleRate: Int,
    private val channels: Int,
    frameSizeMs: Int,
    private val context: Context? = null,
) {
    private val channelConfig =
        if (channels == 1) android.media.AudioFormat.CHANNEL_OUT_MONO
        else android.media.AudioFormat.CHANNEL_OUT_STEREO

    @Suppress("unused")
    private val frameSize: Int = (sampleRate * frameSizeMs) / 1000

    private val audioTrack: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            android.media.AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(
                sampleRate, channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT
            ) * 2
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private var isPlaying = false
    private var playbackJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(pcmFlow: Flow<ByteArray?>) {
        if (isPlaying) return
        playbackJob?.cancel()
        if (!requestAudioFocus()) {
            Log.e(TAG, "Failed to acquire audio focus")
        }
        isPlaying = true
        if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack.play()
        } else {
            isPlaying = false
            return
        }
        playbackJob = playerScope.launch {
            pcmFlow.collect { pcmData ->
                if (isPlaying && pcmData != null) {
                    audioTrack.write(pcmData, 0, pcmData.size)
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.stop()
        }
        abandonAudioFocus()
    }

    fun release() {
        stop()
        audioTrack.release()
        playerScope.cancel()
    }

    fun isCurrentlyPlaying(): Boolean =
        isPlaying && audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(): Boolean {
        val ctx = context ?: return true
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        val ctx = context ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build()
            )
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }

    companion object {
        private const val TAG = "OpusStreamPlayer"
    }
}
