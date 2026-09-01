package com.example.robofelipe.ui.voice

import com.example.robofelipe.network.WebSocketEvent
import com.example.robofelipe.network.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var viewModel: VoiceViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        webSocketManager = WebSocketManager()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        webSocketManager.cleanup()
    }

    private suspend fun createViewModel(): VoiceViewModel {
        viewModel = VoiceViewModel(null, webSocketManager)
        // Deixa o collector do init{} iniciar
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun initialState_isDisconnected() = runTest(testDispatcher) {
        val vm = createViewModel()

        assertEquals(ConnectionState.DISCONNECTED, vm.uiState.value.connectionState)
        assertFalse(vm.uiState.value.isListening)
        assertFalse(vm.uiState.value.isSpeaking)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun connectedEvent_updatesStateToConnected() = runTest(testDispatcher) {
        val vm = createViewModel()

        webSocketManager._events.emit(WebSocketEvent.Connected)
        advanceUntilIdle()

        assertEquals(ConnectionState.CONNECTED, vm.uiState.value.connectionState)
    }

    @Test
    fun helloReceivedEvent_updatesStateToConnected() = runTest(testDispatcher) {
        val vm = createViewModel()

        webSocketManager._events.emit(WebSocketEvent.HelloReceived)
        advanceUntilIdle()

        assertEquals(ConnectionState.CONNECTED, vm.uiState.value.connectionState)
    }

    @Test
    fun disconnectedEvent_updatesStateToDisconnected() = runTest(testDispatcher) {
        val vm = createViewModel()
        webSocketManager._events.emit(WebSocketEvent.Connected)
        advanceUntilIdle()

        webSocketManager._events.emit(WebSocketEvent.Disconnected)
        advanceUntilIdle()

        assertEquals(ConnectionState.DISCONNECTED, vm.uiState.value.connectionState)
        assertFalse(vm.uiState.value.isListening)
        assertFalse(vm.uiState.value.isSpeaking)
    }

    @Test
    fun errorEvent_setsErrorMessage() = runTest(testDispatcher) {
        val vm = createViewModel()

        webSocketManager._events.emit(WebSocketEvent.Error("test error"))
        advanceUntilIdle()

        assertEquals("test error", vm.uiState.value.errorMessage)
    }

    @Test
    fun sttMessage_updatesTranscript() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handleTextMessage("""{"type":"stt","text":"oi felipe"}""")

        assertEquals("oi felipe", vm.uiState.value.transcript)
    }

    @Test
    fun llmMessage_updatesLlmResponse() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handleTextMessage("""{"type":"llm","text":"ola sobrinho"}""")

        assertEquals("ola sobrinho", vm.uiState.value.llmResponse)
    }

    @Test
    fun ttsStart_setsIsSpeaking() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handleTextMessage("""{"type":"tts","state":"start"}""")

        assertTrue(vm.uiState.value.isSpeaking)
    }

    @Test
    fun ttsStop_clearsIsSpeaking() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.handleTextMessage("""{"type":"tts","state":"start"}""")

        vm.handleTextMessage("""{"type":"tts","state":"stop"}""")

        assertFalse(vm.uiState.value.isSpeaking)
    }

    @Test
    fun ttsSentenceStart_doesNotChangeIsSpeaking() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.handleTextMessage("""{"type":"tts","state":"sentence_start"}""")

        assertFalse(vm.uiState.value.isSpeaking)
    }

    @Test
    fun startListening_setsIsListening() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.startListening()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isListening)
    }

    @Test
    fun stopListening_clearsIsListening() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.startListening()
        advanceUntilIdle()

        vm.stopListening()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isListening)
    }

    @Test
    fun startListening_whileSpeaking_abortsTts() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.handleTextMessage("""{"type":"tts","state":"start"}""")
        assertTrue(vm.uiState.value.isSpeaking)

        vm.startListening()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSpeaking)
        assertTrue(vm.uiState.value.isListening)
    }

    @Test
    fun updateServerUrl_changesUrl() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.updateServerUrl("ws://10.0.0.1:8000/xiaozhi/v1/")

        assertEquals("ws://10.0.0.1:8000/xiaozhi/v1/", vm.uiState.value.serverUrl)
    }
}
