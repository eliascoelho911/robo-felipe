package com.example.robofelipe.data

// Eventos de pet_action vindos do xiaozhi-server (fluxo vozeado). O
// VoiceViewModel emite; o PetViewModel coleta e dispara animações.
// speak não vem via pet_action — no fluxo vozeado, speak é o TTS do LLM.
sealed class PetActionEvent {
    data class Dance(val durationMs: Long = 3000) : PetActionEvent()
    data class ExpressEmotion(val emotion: Emotion) : PetActionEvent()
    data class GetDizzy(val intensity: Double = 0.5) : PetActionEvent()
    data class Sleep(val durationMs: Long = 5000) : PetActionEvent()
}
