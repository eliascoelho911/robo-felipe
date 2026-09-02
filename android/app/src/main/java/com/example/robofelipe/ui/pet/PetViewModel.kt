package com.example.robofelipe.ui.pet

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.robofelipe.data.Action
import com.example.robofelipe.data.Emotion
import com.example.robofelipe.data.PetActionEvent
import com.example.robofelipe.data.PetConfig
import com.example.robofelipe.data.PetRepository
import com.example.robofelipe.data.PetStateSnapshot
import com.example.robofelipe.data.PlanoDeAcoes
import com.example.robofelipe.data.Stage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

sealed class PetAnimation {
    object Idle : PetAnimation()
    data class Dance(val durationMs: Long) : PetAnimation()
    data class ExpressEmotion(val emotion: Emotion) : PetAnimation()
    data class GetDizzy(val intensity: Double) : PetAnimation()
    data class Sleep(val durationMs: Long) : PetAnimation()
    data class Speak(val text: String) : PetAnimation()
}

data class PetUiState(
    val mood: Emotion = Emotion.happy,
    val stage: Stage = Stage.Filhote,
    val stats: Map<String, Double> = emptyMap(),
    val sickness: Double = 0.0,
    val ageDays: Long = 0,
    val animation: PetAnimation = PetAnimation.Idle,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

// TTS nativo Android pt-BR — usado quando speak vem do Plano (fluxo
// não-vozeado), não do WSS (fluxo vozeado, já reproduzido via Opus).
interface PetTts {
    fun speak(text: String)
    fun shutdown()
}

class AndroidPetTts(context: Context) : PetTts {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
                isReady = true
            }
        }
    }

    override fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pet_${System.currentTimeMillis()}")
        }
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

// Frases de resposta por tool — feedback TTS para botões não-vozeados.
private val TOOL_SPEAK: Map<String, String> = mapOf(
    "feed" to "Que delícia!",
    "play" to "Yay! Vamos brincar!",
    "rest" to "Boa noite...",
    "clean" to "Tô limpinho!",
    "cuddle" to "Que carinho!",
    "heal" to "Obrigado!",
    "train" to "Que legal!",
    "dance" to "Vamos dançar!",
    "express_emotion" to "Hmm!",
    "get_dizzy" to "Uuuu...",
)

class PetViewModel(
    private val appContext: Context?,
    private val repository: PetRepository,
    private val coreUrl: String,
    private val petId: String,
    private val platformId: String,
    private val petActionEvents: SharedFlow<PetActionEvent>,
    private val tts: PetTts?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PetUiState())
    val uiState: StateFlow<PetUiState> = _uiState.asStateFlow()

    init {
        loadState()
        observePetActions()
    }

    fun loadState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val state = repository.fetchState(coreUrl, petId)
                applyState(state)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Erro ao buscar estado: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun feed() = callTool("feed")
    fun play() = callTool("play")
    fun rest() = callTool("rest")
    fun clean() = callTool("clean")
    fun cuddle() = callTool("cuddle")

    private fun callTool(tool: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            try {
                val state = repository.callTool(coreUrl, petId, tool)
                applyState(state)
                // Fala local — feedback TTS para botão não-vozeado
                TOOL_SPEAK[tool]?.let { text ->
                    tts?.speak(text)
                    executeSpeakAnimation(text)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    fun sendButtonTrigger() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            try {
                val plano = repository.sendButtonTrigger(
                    coreUrl, petId, platformId,
                )
                executePlano(plano)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    fun sendShakeTrigger() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            try {
                val plano = repository.sendShakeTrigger(
                    coreUrl, petId, platformId,
                )
                executePlano(plano)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    // Plano executor — itera actions[] do Core e executa cada Action
    internal suspend fun executePlano(plano: PlanoDeAcoes) {
        for (action in plano.actions) {
            when (action) {
                is Action.Speak -> {
                    tts?.speak(action.text)
                    executeSpeakAnimation(action.text)
                }
                is Action.Dance -> {
                    _uiState.update { it.copy(animation = PetAnimation.Dance(action.durationMs)) }
                    kotlinx.coroutines.delay(action.durationMs)
                }
                is Action.ExpressEmotion -> {
                    _uiState.update { it.copy(animation = PetAnimation.ExpressEmotion(action.emotion)) }
                    kotlinx.coroutines.delay(600)
                    _uiState.update { it.copy(mood = action.emotion) }
                }
                is Action.GetDizzy -> {
                    _uiState.update { it.copy(animation = PetAnimation.GetDizzy(action.intensity)) }
                    kotlinx.coroutines.delay(2000)
                }
                is Action.Sleep -> {
                    _uiState.update { it.copy(animation = PetAnimation.Sleep(action.durationMs)) }
                    kotlinx.coroutines.delay(action.durationMs)
                }
            }
        }
        _uiState.update { it.copy(animation = PetAnimation.Idle) }
        plano.state?.let { applyState(it) }
    }

    private suspend fun executeSpeakAnimation(text: String) {
        _uiState.update { it.copy(animation = PetAnimation.Speak(text)) }
        val estimatedMs = maxOf(1500L, text.length * 80L)
        kotlinx.coroutines.delay(estimatedMs)
        _uiState.update { it.copy(animation = PetAnimation.Idle) }
    }

    private fun observePetActions() {
        viewModelScope.launch {
            petActionEvents.collect { event ->
                handlePetAction(event)
            }
        }
    }

    // pet_action do fluxo vozeado (xiaozhi-server → WSS → animação)
    internal fun handlePetAction(event: PetActionEvent) {
        viewModelScope.launch {
            when (event) {
                is PetActionEvent.Dance -> {
                    _uiState.update { it.copy(animation = PetAnimation.Dance(event.durationMs)) }
                    kotlinx.coroutines.delay(event.durationMs)
                    _uiState.update { it.copy(animation = PetAnimation.Idle) }
                }
                is PetActionEvent.ExpressEmotion -> {
                    _uiState.update { it.copy(animation = PetAnimation.ExpressEmotion(event.emotion)) }
                    kotlinx.coroutines.delay(600)
                    _uiState.update { it.copy(animation = PetAnimation.Idle, mood = event.emotion) }
                }
                is PetActionEvent.GetDizzy -> {
                    _uiState.update { it.copy(animation = PetAnimation.GetDizzy(event.intensity)) }
                    kotlinx.coroutines.delay(2000)
                    _uiState.update { it.copy(animation = PetAnimation.Idle) }
                }
                is PetActionEvent.Sleep -> {
                    _uiState.update { it.copy(animation = PetAnimation.Sleep(event.durationMs)) }
                    kotlinx.coroutines.delay(event.durationMs)
                    _uiState.update { it.copy(animation = PetAnimation.Idle) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun applyState(state: PetStateSnapshot) {
        _uiState.update {
            it.copy(
                mood = parseMood(state.mood),
                stage = state.stage,
                stats = state.stats,
                sickness = state.sickness,
                ageDays = state.ageDays,
            )
        }
    }

    private fun parseMood(mood: String): Emotion =
        runCatching { Emotion.valueOf(mood) }.getOrDefault(Emotion.happy)

    override fun onCleared() {
        tts?.shutdown()
    }

    companion object {
        fun factory(
            app: Application,
            petActionEvents: SharedFlow<PetActionEvent>,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val context = app.applicationContext
                val config = PetConfig(context)
                val tts = AndroidPetTts(context)
                return PetViewModel(
                    appContext = context,
                    repository = PetRepository(),
                    coreUrl = config.coreUrl,
                    petId = config.petId,
                    platformId = config.platformId,
                    petActionEvents = petActionEvents,
                    tts = tts,
                ) as T
            }
        }
    }
}
