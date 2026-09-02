package com.example.robofelipe.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// O Core é cloud-primary — estes tipos espelham os schemas Zod do
// packages/contract para serializar JSON via kotlinx.serialization.

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

// Discriminador "kind" configurado no Json (classDiscriminator = "kind").
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
