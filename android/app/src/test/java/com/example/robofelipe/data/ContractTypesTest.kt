package com.example.robofelipe.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTypesTest {
    // Mesma config do CoreApiClient — discriminador "kind" para a sealed class Action
    private val json = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun speakAction_roundTrips() {
        val action = Action.Speak(text = "Oi, sobrinho!")

        val encoded = json.encodeToString(Action.serializer(), action)
        assertTrue("deve ter discriminador kind=speak", encoded.contains(""""kind":"speak""""))
        assertTrue("deve conter o texto", encoded.contains("Oi, sobrinho!"))

        val decoded = json.decodeFromString<Action>(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun danceAction_roundTrips() {
        val action = Action.Dance(durationMs = 3000)

        val encoded = json.encodeToString(Action.serializer(), action)
        assertTrue(encoded.contains(""""kind":"dance""""))

        val decoded = json.decodeFromString<Action>(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun expressEmotionAction_roundTrips() {
        val action = Action.ExpressEmotion(emotion = Emotion.excited)

        val encoded = json.encodeToString(Action.serializer(), action)
        assertTrue(encoded.contains(""""kind":"express_emotion""""))
        assertTrue(encoded.contains(""""emotion":"excited""""))

        val decoded = json.decodeFromString<Action>(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun getDizzyAction_roundTrips() {
        val action = Action.GetDizzy(intensity = 0.75)

        val encoded = json.encodeToString(Action.serializer(), action)
        assertTrue(encoded.contains(""""kind":"get_dizzy""""))

        val decoded = json.decodeFromString<Action>(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun sleepAction_roundTrips() {
        val action = Action.Sleep(durationMs = 5000)

        val encoded = json.encodeToString(Action.serializer(), action)
        assertTrue(encoded.contains(""""kind":"sleep""""))

        val decoded = json.decodeFromString<Action>(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun actionList_decodesMixedTypes() {
        val jsonStr = """{"version":1,"batchId":"b1","actions":[{"kind":"speak","text":"Oi!"},{"kind":"dance","durationMs":2000}]}"""

        val plano = json.decodeFromString<PlanoDeAcoes>(jsonStr)

        assertEquals(2, plano.actions.size)
        assertTrue(plano.actions[0] is Action.Speak)
        assertTrue(plano.actions[1] is Action.Dance)
        assertEquals("Oi!", (plano.actions[0] as Action.Speak).text)
        assertEquals(2000L, (plano.actions[1] as Action.Dance).durationMs)
    }

    @Test
    fun planoDeAcoes_withState_roundTrips() {
        val state = PetStateSnapshot(
            stage = Stage.Jovem,
            mood = "happy",
            health = 80.0,
            sickness = 0.0,
            ageDays = 5,
            stats = mapOf("fullness" to 70.0, "energy" to 90.0),
            lastInteraction = 1000,
        )
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "batch-123",
            actions = listOf(Action.Speak("Tudo bem?")),
            state = state,
        )

        val encoded = json.encodeToString(PlanoDeAcoes.serializer(), plano)
        val decoded = json.decodeFromString<PlanoDeAcoes>(encoded)

        assertEquals(plano.batchId, decoded.batchId)
        assertEquals(1, decoded.actions.size)
        assertTrue(decoded.actions[0] is Action.Speak)
        assertNotNull(decoded.state)
        assertEquals(Stage.Jovem, decoded.state!!.stage)
        assertEquals(80.0, decoded.state!!.health, 0.01)
    }

    @Test
    fun planoDeAcoes_withoutState_hasNullState() {
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "batch-456",
            actions = listOf(Action.Dance(1000)),
        )

        val encoded = json.encodeToString(PlanoDeAcoes.serializer(), plano)
        val decoded = json.decodeFromString<PlanoDeAcoes>(encoded)

        assertNull(decoded.state)
    }

    @Test
    fun batch_roundTrips() {
        val batch = Batch(
            version = 1,
            batchId = "batch-789",
            platformId = "android-robo-felipe",
            petId = "felipe",
            triggers = listOf(
                Trigger(
                    id = "trigger-1",
                    kind = TriggerKind.button,
                    timestamp = 5000,
                    payload = mapOf("source" to "screen"),
                ),
            ),
        )

        val encoded = json.encodeToString(Batch.serializer(), batch)
        val decoded = json.decodeFromString<Batch>(encoded)

        assertEquals(batch.batchId, decoded.batchId)
        assertEquals(1, decoded.triggers.size)
        assertEquals(TriggerKind.button, decoded.triggers[0].kind)
        assertEquals("screen", decoded.triggers[0].payload["source"])
    }

    @Test
    fun triggerKind_serializesAsLowercase() {
        val trigger = Trigger(
            id = "t1",
            kind = TriggerKind.shake,
            timestamp = 0,
        )

        val encoded = json.encodeToString(Trigger.serializer(), trigger)

        assertTrue("shake deve serializar como lowercase", encoded.contains(""""kind":"shake""""))
    }

    @Test
    fun emotionEnum_serializesAllValues() {
        for (emotion in Emotion.entries) {
            val action = Action.ExpressEmotion(emotion)
            val encoded = json.encodeToString(Action.serializer(), action)
            val decoded = json.decodeFromString<Action>(encoded)
            assertEquals(emotion, (decoded as Action.ExpressEmotion).emotion)
        }
    }

    @Test
    fun stageEnum_serializesAllValues() {
        for (stage in Stage.entries) {
            val snapshot = PetStateSnapshot(
                stage = stage,
                mood = "happy",
                health = 100.0,
                sickness = 0.0,
                ageDays = 0,
                stats = emptyMap(),
                lastInteraction = 0,
            )
            val encoded = json.encodeToString(PetStateSnapshot.serializer(), snapshot)
            val decoded = json.decodeFromString<PetStateSnapshot>(encoded)
            assertEquals(stage, decoded.stage)
        }
    }
}
