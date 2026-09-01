package com.example.robofelipe.ui.voice

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.robofelipe.audio.AudioEvent
import com.example.robofelipe.audio.EnhancedAudioManager
import com.example.robofelipe.network.WebSocketEvent
import com.example.robofelipe.network.WebSocketManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

data class VoiceUiState(
    val serverUrl: String = "ws://192.168.1.100:8000/xiaozhi/v1/",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val transcript: String? = null,
    val llmResponse: String? = null,
    val errorMessage: String? = null,
)

class VoiceViewModel(
    private val appContext: Context?,
    private val webSocketManager: WebSocketManager,
) : ViewModel() {

    private val gson = Gson()
    private var audioManager: EnhancedAudioManager? = null

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        observeWebSocketEvents()
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            webSocketManager.events.collect { event ->
                handleWebSocketEvent(event)
            }
        }
    }

    internal fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.HelloReceived ->
                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED, errorMessage = null) }

            is WebSocketEvent.Connected ->
                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED, errorMessage = null) }

            is WebSocketEvent.Disconnected ->
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        isListening = false,
                        isSpeaking = false,
                    )
                }

            is WebSocketEvent.TextMessage -> handleTextMessage(event.message)
            is WebSocketEvent.BinaryMessage -> audioManager?.playAudio(event.data)
            is WebSocketEvent.Error -> _uiState.update { it.copy(errorMessage = event.error) }
        }
    }

    // Parser para mensagens do protocolo xiaozhi (stt, llm, tts, pet_action)
    internal fun handleTextMessage(text: String) {
        val json = gson.fromJson(text, JsonObject::class.java) ?: return
        when (json.get("type")?.asString) {
            "hello" -> { /* handshake tratado pelo WebSocketManager */ }
            "stt" -> {
                val text = json.get("text")?.asString
                if (text != null) _uiState.update { it.copy(transcript = text) }
            }
            "llm" -> {
                val text = json.get("text")?.asString
                if (text != null) _uiState.update { it.copy(llmResponse = text) }
            }
            "tts" -> {
                val state = json.get("state")?.asString
                when (state) {
                    "start" -> _uiState.update { it.copy(isSpeaking = true) }
                    "stop" -> _uiState.update { it.copy(isSpeaking = false) }
                    else -> {}
                }
            }
            "pet_action" -> Log.d(TAG, "pet_action recebido (handler no spec 06): $text")
            else -> {}
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
    }

    fun connect() {
        val state = _uiState.value
        _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING, errorMessage = null) }
        audioManager = EnhancedAudioManager(appContext!!).also { it.initialize() }
        observeAudioEvents()
        webSocketManager.connect(state.serverUrl, DEVICE_ID)
    }

    private fun observeAudioEvents() {
        viewModelScope.launch {
            audioManager?.audioEvents?.collect { event ->
                when (event) {
                    is AudioEvent.AudioData -> webSocketManager.sendBinaryMessage(event.data)
                    is AudioEvent.Error -> _uiState.update { it.copy(errorMessage = event.message) }
                }
            }
        }
    }

    fun startListening() {
        val state = _uiState.value
        // Abort TTS se o Robô Felipe estiver falando
        if (state.isSpeaking) {
            webSocketManager.sendAbort()
            audioManager?.stopPlaying()
            _uiState.update { it.copy(isSpeaking = false) }
        }
        webSocketManager.sendStartListening()
        audioManager?.startRecording()
        _uiState.update { it.copy(isListening = true) }
    }

    fun stopListening() {
        audioManager?.stopRecording()
        webSocketManager.sendStopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    fun disconnect() {
        audioManager?.cleanup()
        audioManager = null
        webSocketManager.disconnect()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                isListening = false,
                isSpeaking = false,
            )
        }
    }

    override fun onCleared() {
        audioManager?.cleanup()
        webSocketManager.cleanup()
    }

    companion object {
        private const val TAG = "VoiceViewModel"
        private const val DEVICE_ID = "robo-felipe-tamagotchi"

        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VoiceViewModel(app.applicationContext, WebSocketManager()) as T
        }
    }
}
