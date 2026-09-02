package com.example.robofelipe.data

import com.example.robofelipe.network.CoreApiClient

// PetViewModel consome este repositório sem saber de OkHttp nem de
// rotas do Core — só chama operações de domínio.
open class PetRepository(
    private val client: CoreApiClient = CoreApiClient(),
) {
    open fun fetchState(coreUrl: String, petId: String): PetStateSnapshot =
        client.fetchState(coreUrl, petId)

    open fun callTool(
        coreUrl: String,
        petId: String,
        tool: String,
        emotion: Emotion? = null,
    ): PetStateSnapshot =
        client.callTool(coreUrl, petId, tool, emotion)

    open fun sendBatch(coreUrl: String, batch: Batch): PlanoDeAcoes =
        client.sendBatch(coreUrl, batch)

    open fun sendManualTrigger(
        coreUrl: String,
        petId: String,
        platformId: String,
        payload: Map<String, String> = emptyMap(),
    ): PlanoDeAcoes =
        client.sendBatch(
            coreUrl,
            client.buildManualBatch(petId, platformId, payload),
        )

    open fun sendButtonTrigger(
        coreUrl: String,
        petId: String,
        platformId: String,
    ): PlanoDeAcoes =
        client.sendBatch(
            coreUrl,
            client.buildButtonBatch(petId, platformId),
        )

    open fun sendShakeTrigger(
        coreUrl: String,
        petId: String,
        platformId: String,
        intensity: Double = 0.8,
    ): PlanoDeAcoes =
        client.sendBatch(
            coreUrl,
            client.buildShakeBatch(petId, platformId, intensity),
        )
}
