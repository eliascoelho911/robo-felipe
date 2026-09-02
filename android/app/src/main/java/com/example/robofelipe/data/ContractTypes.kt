package com.example.robofelipe.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Tipos do contrato Batch/Plano de Ações, espelhando os schemas Zod do
// packages/contract. O Core é a fonte da verdade; estas classes apenas
// serializam/deserializam JSON para comunicação HTTPS.

@Serializable
enum class TriggerKind {
    voice,
    shake,
    button,
    manual,
}

@Serializable
data class Trigger(
    val id: String,
    val kind: TriggerKind,
    val timestamp: Long,
    val payload: Map<String, String> = emptyMap(),
)

@Serializable
data class Batch(
    val version: Int = 1,
    val batchId: String,
    val platformId: String,
    val petId: String,
    val triggers: List<Trigger>,
)

@Serializable
enum class Emotion {
    happy,
    sad,
    sleepy,
    bored,
    excited,
    hungry,
    tired,
    dirty,
    dizzy,
    scared,
    playful,
    curious,
    mischievous,
}

// O discriminador "kind" é configurado no Json (classDiscriminator).
// Cada subclasse usa @SerialName para o valor do discriminador.
@Serializable
sealed class Action {
    @Serializable
    @SerialName("speak")
    data class Speak(
        val text: String,
    ) : Action()

    @Serializable
    @SerialName("dance")
    data class Dance(
        val durationMs: Long,
    ) : Action()

    @Serializable
    @SerialName("express_emotion")
    data class ExpressEmotion(
        val emotion: Emotion,
    ) : Action()

    @Serializable
    @SerialName("get_dizzy")
    data class GetDizzy(
        val intensity: Double,
    ) : Action()

    @Serializable
    @SerialName("sleep")
    data class Sleep(
        val durationMs: Long,
    ) : Action()
}

@Serializable
enum class Stage {
    Filhote,
    Jovem,
    Adulto,
}

@Serializable
data class PetStateSnapshot(
    val stage: Stage,
    val mood: String,
    val health: Double,
    val sickness: Double,
    val ageDays: Long,
    val stats: Map<String, Double>,
    val lastInteraction: Long,
)

@Serializable
data class PlanoDeAcoes(
    val version: Int = 1,
    val batchId: String,
    val actions: List<Action>,
    val state: PetStateSnapshot? = null,
)

// Resposta de POST /pet/:id/:tool e GET /pet/:id/state — mesmo formato
// que PetStateSnapshot, mas o endpoint de tool retorna o estado mutado.
@Serializable
data class MoodResponse(
    val mood: String,
)
