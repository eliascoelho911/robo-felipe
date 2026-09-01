package com.example.robofelipe.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: WebSocketManager

    @Before
    fun setup() {
        manager = WebSocketManager(scope = testScope)
    }

    @Test
    fun helloMessage_hasCorrectFormat() {
        val json = Gson().fromJson(manager.buildHelloMessage(), JsonObject::class.java)

        assertEquals("hello", json.get("type").asString)
        assertEquals(1, json.get("version").asInt)
        assertEquals("websocket", json.get("transport").asString)

        val audioParams = json.getAsJsonObject("audio_params")
        assertEquals("opus", audioParams.get("format").asString)
        assertEquals(16000, audioParams.get("sample_rate").asInt)
        assertEquals(1, audioParams.get("channels").asInt)
        assertEquals(60, audioParams.get("frame_duration").asInt)
    }

    @Test
    fun listenStartMessage_hasCorrectFormat() {
        manager.handleTextMessage("""{"type":"hello","transport":"websocket","session_id":"test-session-123"}""")

        val json = Gson().fromJson(manager.buildListenStartMessage(), JsonObject::class.java)

        assertEquals("test-session-123", json.get("session_id").asString)
        assertEquals("listen", json.get("type").asString)
        assertEquals("start", json.get("state").asString)
        assertEquals("auto", json.get("mode").asString)
    }

    @Test
    fun listenStopMessage_hasCorrectFormat() {
        manager.handleTextMessage("""{"type":"hello","transport":"websocket","session_id":"test-session-456"}""")

        val json = Gson().fromJson(manager.buildListenStopMessage(), JsonObject::class.java)

        assertEquals("test-session-456", json.get("session_id").asString)
        assertEquals("listen", json.get("type").asString)
        assertEquals("stop", json.get("state").asString)
    }

    @Test
    fun abortMessage_hasCorrectFormat() {
        manager.handleTextMessage("""{"type":"hello","transport":"websocket","session_id":"test-session-789"}""")

        val json = Gson().fromJson(manager.buildAbortMessage(), JsonObject::class.java)

        // spec 05: abort não leva session_id
        assertNull(json.get("session_id"))
        assertEquals("abort", json.get("type").asString)
        assertEquals("user_interrupt", json.get("reason").asString)
    }

    @Test
    fun abortMessage_withCustomReason() {
        manager.handleTextMessage("""{"type":"hello","transport":"websocket","session_id":"s1"}""")

        val json = Gson().fromJson(manager.buildAbortMessage("wake_word_detected"), JsonObject::class.java)

        assertEquals("wake_word_detected", json.get("reason").asString)
    }

    @Test
    fun listenStart_withoutSessionId_omitsField() {
        // Sem handshake prévio, session_id é null e não deve estar no JSON
        val json = Gson().fromJson(manager.buildListenStartMessage(), JsonObject::class.java)

        assertEquals("listen", json.get("type").asString)
        assertEquals("start", json.get("state").asString)
        assertNull(json.get("session_id"))
    }

    @Test
    fun handleHelloResponse_extractsSessionId() {
        manager.handleTextMessage("""{"type":"hello","transport":"websocket","session_id":"abc-123"}""")

        assertEquals("abc-123", manager.getSessionId())
    }

    @Test
    fun handleHelloResponse_wrongTransport_doesNotSetSessionId() {
        manager.handleTextMessage("""{"type":"hello","transport":"udp","session_id":"xyz"}""")

        assertNull(manager.getSessionId())
    }

    @Test
    fun handleHelloResponse_emitsHelloAndConnectedEvents() = runTest(testDispatcher) {
        val events = mutableListOf<WebSocketEvent>()
        val collectJob = launch {
            manager.events.collect { events.add(it) }
        }
        advanceUntilIdle()

        manager.handleTextMessage("""{"type":"hello","transport":"websocket","session_id":"s1"}""")
        advanceUntilIdle()

        assertTrue(events.any { it is WebSocketEvent.HelloReceived })
        assertTrue(events.any { it is WebSocketEvent.Connected })

        collectJob.cancel()
    }

    @Test
    fun handleHelloResponse_wrongTransport_emitsError() = runTest(testDispatcher) {
        val events = mutableListOf<WebSocketEvent>()
        val collectJob = launch {
            manager.events.collect { events.add(it) }
        }
        advanceUntilIdle()

        manager.handleTextMessage("""{"type":"hello","transport":"udp","session_id":"s1"}""")
        advanceUntilIdle()

        val error = events.filterIsInstance<WebSocketEvent.Error>().firstOrNull()
        assertNotNull(error)

        collectJob.cancel()
    }

    @Test
    fun calculateReconnectDelay_growsExponentially() {
        assertEquals(2000L, manager.calculateReconnectDelay(0))
        assertEquals(4000L, manager.calculateReconnectDelay(1))
        assertEquals(8000L, manager.calculateReconnectDelay(2))
        assertEquals(16000L, manager.calculateReconnectDelay(3))
    }

    @Test
    fun calculateReconnectDelay_capsAtMax() {
        assertEquals(30000L, manager.calculateReconnectDelay(10))
        assertEquals(30000L, manager.calculateReconnectDelay(100))
    }

    @Test
    fun binaryMessageEvent_emitsCorrectData() = runTest(testDispatcher) {
        val events = mutableListOf<WebSocketEvent>()
        val collectJob = launch {
            manager.events.collect { events.add(it) }
        }
        advanceUntilIdle()

        val binaryData = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        manager._events.emit(WebSocketEvent.BinaryMessage(binaryData))
        advanceUntilIdle()

        val binary = events.filterIsInstance<WebSocketEvent.BinaryMessage>().firstOrNull()
        assertNotNull(binary)
        assertEquals(4, binary!!.data.size)

        collectJob.cancel()
    }

    @After
    fun teardown() {
        manager.cleanup()
    }
}
