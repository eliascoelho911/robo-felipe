package com.example.robofelipe.data

import com.example.robofelipe.network.CoreApiClient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreApiClientTest {
    private val client = CoreApiClient()
    private val json = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun buildManualBatch_hasCorrectStructure() {
        val batch = client.buildManualBatch(
            petId = "felipe",
            platformId = "android-1",
            payload = mapOf("source" to "test"),
        )

        assertEquals(1, batch.version)
        assertEquals("felipe", batch.petId)
        assertEquals("android-1", batch.platformId)
        assertNotNull(batch.batchId)
        assertEquals(1, batch.triggers.size)

        val trigger = batch.triggers[0]
        assertEquals(TriggerKind.manual, trigger.kind)
        assertNotNull(trigger.id)
        assertEquals("test", trigger.payload["source"])
    }

    @Test
    fun buildManualBatch_generatesUniqueBatchIds() {
        val batch1 = client.buildManualBatch("felipe", "android-1")
        val batch2 = client.buildManualBatch("felipe", "android-1")

        assertTrue("batchIds devem ser únicos", batch1.batchId != batch2.batchId)
        assertTrue("trigger ids devem ser únicos", batch1.triggers[0].id != batch2.triggers[0].id)
    }

    @Test
    fun buildButtonBatch_hasButtonTrigger() {
        val batch = client.buildButtonBatch(
            petId = "felipe",
            platformId = "android-1",
        )

        assertEquals(1, batch.version)
        assertEquals("felipe", batch.petId)
        assertEquals(1, batch.triggers.size)
        assertEquals(TriggerKind.button, batch.triggers[0].kind)
    }

    @Test
    fun buildShakeBatch_hasShakeTriggerWithIntensity() {
        val batch = client.buildShakeBatch(
            petId = "felipe",
            platformId = "android-1",
            intensity = 0.9,
        )

        assertEquals(TriggerKind.shake, batch.triggers[0].kind)
        assertEquals("0.9", batch.triggers[0].payload["intensity"])
    }

    @Test
    fun buildShakeBatch_defaultIntensityIs08() {
        val batch = client.buildShakeBatch("felipe", "android-1")

        assertEquals("0.8", batch.triggers[0].payload["intensity"])
    }

    @Test
    fun builtBatch_serializesToJson() {
        val batch = client.buildButtonBatch("felipe", "android-1")

        val encoded = json.encodeToString(Batch.serializer(), batch)

        assertTrue(encoded.contains(""""petId":"felipe""""))
        assertTrue(encoded.contains(""""platformId":"android-1""""))
        assertTrue(encoded.contains(""""kind":"button""""))
        assertTrue(encoded.contains(""""version":1"""))
    }

    @Test
    fun builtBatch_roundTripsThroughSerialization() {
        val original = client.buildShakeBatch("felipe", "android-1", 0.5)

        val encoded = json.encodeToString(Batch.serializer(), original)
        val decoded = json.decodeFromString<Batch>(encoded)

        assertEquals(original.batchId, decoded.batchId)
        assertEquals(original.petId, decoded.petId)
        assertEquals(1, decoded.triggers.size)
        assertEquals(TriggerKind.shake, decoded.triggers[0].kind)
        assertEquals("0.5", decoded.triggers[0].payload["intensity"])
    }
}
