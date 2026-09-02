package com.example.robofelipe.ui.pet

import com.example.robofelipe.data.Action
import com.example.robofelipe.data.Emotion
import com.example.robofelipe.data.PetActionEvent
import com.example.robofelipe.data.PetRepository
import com.example.robofelipe.data.PetStateSnapshot
import com.example.robofelipe.data.PlanoDeAcoes
import com.example.robofelipe.data.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakePetRepository
    private lateinit var fakeTts: FakePetTts
    private lateinit var petActionFlow: MutableSharedFlow<PetActionEvent>
    private lateinit var viewModel: PetViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePetRepository()
        fakeTts = FakePetTts()
        petActionFlow = MutableSharedFlow(extraBufferCapacity = 10)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private suspend fun createViewModel(): PetViewModel {
        viewModel = PetViewModel(
            repository = fakeRepository,
            coreUrl = "http://localhost:3000",
            petId = "felipe",
            platformId = "android-test",
            petActionEvents = petActionFlow.asSharedFlow(),
            tts = fakeTts,
        )
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun loadState_appliesStateFromRepository() = runTest(testDispatcher) {
        fakeRepository.fetchStateResult = sampleState(mood = "excited", health = 90.0)

        val vm = createViewModel()

        assertEquals(Emotion.excited, vm.uiState.value.mood)
        assertEquals(90.0, vm.uiState.value.stats["health"]!!, 0.01)
        assertEquals(Stage.Jovem, vm.uiState.value.stage)
    }

    @Test
    fun loadState_errorSetsErrorMessage() = runTest(testDispatcher) {
        fakeRepository.fetchStateError = RuntimeException("conexão recusada")

        val vm = createViewModel()

        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.errorMessage!!.contains("conexão recusada"))
    }

    @Test
    fun feed_callsToolAndAppliesState() = runTest(testDispatcher) {
        fakeRepository.fetchStateResult = sampleState(mood = "happy")
        fakeRepository.callToolResult = sampleState(mood = "happy", fullness = 85.0)
        val vm = createViewModel()

        vm.feed()
        advanceUntilIdle()

        assertEquals(85.0, vm.uiState.value.stats["fullness"]!!, 0.01)
    }

    @Test
    fun play_callsToolAndAppliesState() = runTest(testDispatcher) {
        fakeRepository.callToolResult = sampleState(mood = "playful", fullness = 60.0)
        val vm = createViewModel()

        vm.play()
        advanceUntilIdle()

        assertEquals(Emotion.playful, vm.uiState.value.mood)
        assertTrue("play não dispara TTS hardcoded", fakeTts.spokenTexts.isEmpty())
    }

    @Test
    fun feed_sendsManualTriggerViaBatch() = runTest(testDispatcher) {
        fakeRepository.sendBatchResult = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.Speak("Que delícia!")),
        )
        fakeRepository.callToolResult = sampleState(mood = "happy", fullness = 85.0)
        val vm = createViewModel()

        vm.feed()
        advanceUntilIdle()

        assertTrue(fakeTts.spokenTexts.contains("Que delícia!"))
    }

    @Test
    fun executePlano_speakAction_triggersTtsAndSpeakAnimation() = runTest(testDispatcher) {
        val vm = createViewModel()
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.Speak("Oi, sobrinho!")),
        )

        vm.executePlano(plano)
        advanceUntilIdle()

        assertTrue(fakeTts.spokenTexts.contains("Oi, sobrinho!"))
        assertTrue(vm.uiState.value.animation is PetAnimation.Idle)
    }

    @Test
    fun executePlano_danceAction_setsDanceThenIdle() = runTest(testDispatcher) {
        val vm = createViewModel()
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.Dance(durationMs = 1000)),
        )

        // executePlano é suspend — launch para observar estado intermediário
        launch { vm.executePlano(plano) }
        runCurrent() // roda até o primeiro delay — animation deve ser Dance

        assertEquals(PetAnimation.Dance(1000), vm.uiState.value.animation)

        advanceUntilIdle() // completa o delay — animation volta a Idle

        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
        assertTrue("dance não dispara TTS", fakeTts.spokenTexts.isEmpty())
    }

    @Test
    fun executePlano_expressEmotionAction_setsAnimationAndMood() = runTest(testDispatcher) {
        val vm = createViewModel()
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.ExpressEmotion(Emotion.sad)),
        )

        vm.executePlano(plano)
        advanceUntilIdle()

        assertEquals(Emotion.sad, vm.uiState.value.mood)
        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
    }

    @Test
    fun executePlano_getDizzyAction_setsGetDizzyThenIdle() = runTest(testDispatcher) {
        val vm = createViewModel()
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.GetDizzy(intensity = 0.5)),
        )

        launch { vm.executePlano(plano) }
        runCurrent()

        assertTrue(vm.uiState.value.animation is PetAnimation.GetDizzy)

        advanceUntilIdle()
        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
    }

    @Test
    fun executePlano_sleepAction_setsSleepThenIdle() = runTest(testDispatcher) {
        val vm = createViewModel()
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.Sleep(durationMs = 2000)),
        )

        launch { vm.executePlano(plano) }
        runCurrent()

        assertTrue(vm.uiState.value.animation is PetAnimation.Sleep)

        advanceUntilIdle()
        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
    }

    @Test
    fun executePlano_appliesStateFromPlano() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = PetStateSnapshot(
            stage = Stage.Adulto,
            mood = "tired",
            health = 60.0,
            sickness = 10.0,
            ageDays = 30,
            stats = mapOf("energy" to 20.0),
            lastInteraction = 2000,
        )
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.Dance(500)),
            state = state,
        )

        vm.executePlano(plano)
        advanceUntilIdle()

        assertEquals(Emotion.tired, vm.uiState.value.mood)
        assertEquals(Stage.Adulto, vm.uiState.value.stage)
        assertEquals(20.0, vm.uiState.value.stats["energy"]!!, 0.01)
        assertEquals(10.0, vm.uiState.value.sickness, 0.01)
    }

    @Test
    fun executePlano_multipleActionsExecutesInOrder() = runTest(testDispatcher) {
        val vm = createViewModel()
        val plano = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(
                Action.Speak("Primeiro"),
                Action.Dance(500),
                Action.Speak("Segundo"),
            ),
        )

        vm.executePlano(plano)
        advanceUntilIdle()

        assertEquals(2, fakeTts.spokenTexts.size)
        assertEquals("Primeiro", fakeTts.spokenTexts[0])
        assertEquals("Segundo", fakeTts.spokenTexts[1])
    }

    @Test
    fun handlePetAction_danceEvent_setsDanceThenIdle() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handlePetAction(PetActionEvent.Dance(durationMs = 1000))
        runCurrent()

        assertTrue(vm.uiState.value.animation is PetAnimation.Dance)

        advanceUntilIdle()
        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
    }

    @Test
    fun handlePetAction_expressEmotionEvent_setsMood() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handlePetAction(PetActionEvent.ExpressEmotion(Emotion.scared))
        advanceUntilIdle()

        assertEquals(Emotion.scared, vm.uiState.value.mood)
    }

    @Test
    fun handlePetAction_getDizzyEvent_setsGetDizzyThenIdle() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handlePetAction(PetActionEvent.GetDizzy(intensity = 0.9))
        runCurrent()

        assertTrue(vm.uiState.value.animation is PetAnimation.GetDizzy)

        advanceUntilIdle()
        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
    }

    @Test
    fun handlePetAction_sleepEvent_setsSleepThenIdle() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handlePetAction(PetActionEvent.Sleep(durationMs = 3000))
        runCurrent()

        assertTrue(vm.uiState.value.animation is PetAnimation.Sleep)

        advanceUntilIdle()
        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
    }

    @Test
    fun clearError_removesErrorMessage() = runTest(testDispatcher) {
        fakeRepository.fetchStateError = RuntimeException("erro inicial")
        val vm = createViewModel()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.clearError()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun sendButtonTrigger_executesPlanoFromRepository() = runTest(testDispatcher) {
        val vm = createViewModel()
        fakeRepository.sendBatchResult = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.Speak("Oi! Que bom te ver!")),
        )

        vm.sendButtonTrigger()
        advanceUntilIdle()

        assertTrue(fakeTts.spokenTexts.contains("Oi! Que bom te ver!"))
    }

    @Test
    fun sendShakeTrigger_executesPlanoFromRepository() = runTest(testDispatcher) {
        val vm = createViewModel()
        fakeRepository.sendBatchResult = PlanoDeAcoes(
            version = 1,
            batchId = "b1",
            actions = listOf(Action.GetDizzy(0.8)),
        )

        vm.sendShakeTrigger()
        advanceUntilIdle()

        assertEquals(PetAnimation.Idle, vm.uiState.value.animation)
    }

    @Test
    fun parseMood_invalidMoodDefaultsToHappy() = runTest(testDispatcher) {
        fakeRepository.fetchStateResult = sampleState(mood = "invalid_mood")
        val vm = createViewModel()

        // mood inválido cai para happy (fallback de parseMood)
        assertEquals(Emotion.happy, vm.uiState.value.mood)
    }

    private fun sampleState(
        mood: String = "happy",
        health: Double = 100.0,
        fullness: Double = 50.0,
    ) = PetStateSnapshot(
        stage = Stage.Jovem,
        mood = mood,
        health = health,
        sickness = 0.0,
        ageDays = 5,
        stats = mapOf("fullness" to fullness, "energy" to 80.0, "happiness" to 70.0, "health" to health),
        lastInteraction = 1000,
    )
}

class FakePetTts : PetTts {
    val spokenTexts = mutableListOf<String>()

    override fun speak(text: String) {
        spokenTexts.add(text)
    }

    override fun shutdown() {}
}

class FakePetRepository : PetRepository() {
    var fetchStateResult: PetStateSnapshot = PetStateSnapshot(
        stage = Stage.Filhote,
        mood = "happy",
        health = 100.0,
        sickness = 0.0,
        ageDays = 0,
        stats = mapOf("fullness" to 50.0, "energy" to 80.0, "happiness" to 70.0, "health" to 100.0),
        lastInteraction = 0,
    )
    var fetchStateError: Exception? = null

    var callToolResult: PetStateSnapshot = fetchStateResult
    var callToolError: Exception? = null

    var sendBatchResult: PlanoDeAcoes = PlanoDeAcoes(
        version = 1,
        batchId = "default",
        actions = emptyList(),
    )
    var sendBatchError: Exception? = null

    override fun fetchState(coreUrl: String, petId: String): PetStateSnapshot {
        fetchStateError?.let { throw it }
        return fetchStateResult
    }

    override fun callTool(
        coreUrl: String,
        petId: String,
        tool: String,
        emotion: Emotion?,
    ): PetStateSnapshot {
        callToolError?.let { throw it }
        return callToolResult
    }

    override fun sendBatch(coreUrl: String, batch: com.example.robofelipe.data.Batch): PlanoDeAcoes {
        sendBatchError?.let { throw it }
        return sendBatchResult
    }

    override fun sendManualTrigger(
        coreUrl: String,
        petId: String,
        platformId: String,
        payload: Map<String, String>,
    ): PlanoDeAcoes {
        sendBatchError?.let { throw it }
        return sendBatchResult
    }

    // sendButtonTrigger/sendShakeTrigger no PetRepository chamam client.sendBatch
    // diretamente — precários overrider para evitar chamadas de rede reais.
    override fun sendButtonTrigger(
        coreUrl: String,
        petId: String,
        platformId: String,
    ): PlanoDeAcoes {
        sendBatchError?.let { throw it }
        return sendBatchResult
    }

    override fun sendShakeTrigger(
        coreUrl: String,
        petId: String,
        platformId: String,
        intensity: Double,
    ): PlanoDeAcoes {
        sendBatchError?.let { throw it }
        return sendBatchResult
    }
}
