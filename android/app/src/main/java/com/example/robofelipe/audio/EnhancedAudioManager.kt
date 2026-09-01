package com.example.robofelipe.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AcousticEchoCanceler
import android.media.MediaRecorder
import android.media.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class EnhancedAudioManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var opusEncoder: OpusEncoder? = null
    private var opusDecoder: OpusDecoder? = null
    private var streamPlayer: OpusStreamPlayer? = null
    private var audioRecord: AudioRecord? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val _audioEvents = MutableSharedFlow<AudioEvent>(replay = 0)
    val audioEvents: SharedFlow<AudioEvent> = _audioEvents.asSharedFlow()

    private val _audioPlaybackFlow = MutableSharedFlow<ByteArray>(replay = 0)

    private var isRecording = false
    private var isPlayingState = false
    private var isPlaybackSetup = false

    fun initialize(): Boolean {
        return try {
            opusEncoder = OpusEncoder(ENCODE_SAMPLE_RATE, 1, FRAME_DURATION_MS)
            opusDecoder = OpusDecoder(DECODE_SAMPLE_RATE, 1, FRAME_DURATION_MS)
            streamPlayer = OpusStreamPlayer(DECODE_SAMPLE_RATE, 1, context)
            setupAudioRecord()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio manager", e)
            false
        }
    }

    private fun setupAudioRecord() {
        val bufferSize = AudioRecord.getMinBufferSize(
            ENCODE_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        @Suppress("MissingPermission")
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            ENCODE_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
            bufferSize * 2
        )

        audioRecord?.let { record ->
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(record.audioSessionId)
                acousticEchoCanceler?.enabled = true
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)
                noiseSuppressor?.enabled = true
            }
        }
    }

    fun startRecording() {
        if (isRecording) return
        audioRecord?.let { record ->
            record.startRecording()
            isRecording = true
            scope.launch {
                val buffer = ByteArray(FRAME_SIZE_BYTES)
                while (isRecording) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val opusData = opusEncoder?.encode(buffer.copyOf(bytesRead))
                        opusData?.let { _audioEvents.emit(AudioEvent.AudioData(it)) }
                    }
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.let { record ->
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
    }

    fun playAudio(audioData: ByteArray) {
        scope.launch {
            if (!isPlayingState) {
                isPlayingState = true
                setupAudioPlayback()
            }
            _audioPlaybackFlow.emit(audioData)
        }
    }

    private fun setupAudioPlayback() {
        if (isPlaybackSetup) return
        isPlaybackSetup = true
        val pcmFlow = flow {
            _audioPlaybackFlow.collect { opus ->
                opusDecoder?.decode(opus)?.let { emit(it) }
            }
        }
        streamPlayer?.start(pcmFlow)
    }

    fun stopPlaying() {
        isPlayingState = false
        isPlaybackSetup = false
        streamPlayer?.stop()
    }

    fun isRecording(): Boolean = isRecording

    fun isPlaying(): Boolean = isPlayingState

    fun cleanup() {
        stopRecording()
        stopPlaying()
        acousticEchoCanceler?.release()
        noiseSuppressor?.release()
        audioRecord?.release()
        opusEncoder?.release()
        opusDecoder?.release()
        streamPlayer?.release()
        scope.cancel()
    }

    companion object {
        private const val TAG = "EnhancedAudioManager"
        private const val ENCODE_SAMPLE_RATE = 16000
        // TTS do EdgeTTS no xiaozhi-server saí a 24kHz, não 16kHz — a spec
        // prescreve 16kHz mas o servidor real usa 24kHz (assimetria do protocolo)
        private const val DECODE_SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_DURATION_MS = 60
        private const val FRAME_SIZE_BYTES = ENCODE_SAMPLE_RATE * FRAME_DURATION_MS / 1000 * 2
    }
}
