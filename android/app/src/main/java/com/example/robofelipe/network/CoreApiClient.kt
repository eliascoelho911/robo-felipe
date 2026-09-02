package com.example.robofelipe.network

import com.example.robofelipe.data.Batch
import com.example.robofelipe.data.Emotion
import com.example.robofelipe.data.PetStateSnapshot
import com.example.robofelipe.data.PlanoDeAcoes
import com.example.robofelipe.data.Trigger
import com.example.robofelipe.data.TriggerKind
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
class CoreApiClient(
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun fetchState(coreUrl: String, petId: String): PetStateSnapshot {
        val request = Request.Builder()
            .url("${coreUrl.trimEnd('/')}/pet/$petId/state")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("fetchState failed: ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("fetchState: corpo vazio")
            return json.decodeFromString<PetStateSnapshot>(body)
        }
    }

    fun callTool(
        coreUrl: String,
        petId: String,
        tool: String,
        emotion: Emotion? = null,
    ): PetStateSnapshot {
        val body = if (emotion != null) {
            """{"emotion":"${emotion.name}"}"""
        } else {
            "{}"
        }

        val request = Request.Builder()
            .url("${coreUrl.trimEnd('/')}/pet/$petId/$tool")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("callTool($tool) failed: ${response.code}")
            }
            val responseBody = response.body?.string()
                ?: throw IOException("callTool: corpo vazio")
            return json.decodeFromString<PetStateSnapshot>(responseBody)
        }
    }

    fun sendBatch(
        coreUrl: String,
        batch: Batch,
    ): PlanoDeAcoes {
        val request = Request.Builder()
            .url("${coreUrl.trimEnd('/')}/batch")
            .post(json.encodeToString(Batch.serializer(), batch).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("sendBatch failed: ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("sendBatch: corpo vazio")
            return json.decodeFromString<PlanoDeAcoes>(body)
        }
    }

    // Botões de tool na UI (alimentar, brincar) enviam manual trigger;
    // o Core mapeia `manual` no Batch endpoint.
    fun buildManualBatch(
        petId: String,
        platformId: String,
        payload: Map<String, String> = emptyMap(),
    ): Batch = Batch(
        version = 1,
        batchId = UUID.randomUUID().toString(),
        platformId = platformId,
        petId = petId,
        triggers = listOf(
            Trigger(
                id = UUID.randomUUID().toString(),
                kind = TriggerKind.manual,
                timestamp = System.currentTimeMillis(),
                payload = payload,
            ),
        ),
    )

    // Saudação do botão push — Core retorna `[speak{"Oi! Que bom te ver!"}]`.
    fun buildButtonBatch(
        petId: String,
        platformId: String,
    ): Batch = Batch(
        version = 1,
        batchId = UUID.randomUUID().toString(),
        platformId = platformId,
        petId = petId,
        triggers = listOf(
            Trigger(
                id = UUID.randomUUID().toString(),
                kind = TriggerKind.button,
                timestamp = System.currentTimeMillis(),
            ),
        ),
    )

    // Shake do acelerômetro — Core retorna `[get_dizzy]`.
    fun buildShakeBatch(
        petId: String,
        platformId: String,
        intensity: Double = 0.8,
    ): Batch = Batch(
        version = 1,
        batchId = UUID.randomUUID().toString(),
        platformId = platformId,
        petId = petId,
        triggers = listOf(
            Trigger(
                id = UUID.randomUUID().toString(),
                kind = TriggerKind.shake,
                timestamp = System.currentTimeMillis(),
                payload = mapOf("intensity" to intensity.toString()),
            ),
        ),
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
