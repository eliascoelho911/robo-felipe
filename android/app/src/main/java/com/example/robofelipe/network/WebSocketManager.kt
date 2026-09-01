package com.example.robofelipe.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val client: OkHttpClient = defaultClient,
    private val gson: Gson = Gson(),
) {

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isHandshakeComplete = false
    private var shouldReconnect = true
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var sessionId: String? = null
    private var helloTimeoutJob: Job? = null

    private var lastUrl: String? = null
    private var lastDeviceId: String? = null
    private var lastToken: String? = null
    private var reconnectAttempts = 0

    internal val _events = MutableSharedFlow<WebSocketEvent>(replay = 0)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    fun connect(url: String, deviceId: String, token: String) {
        shouldReconnect = true
        lastUrl = url
        lastDeviceId = deviceId
        lastToken = token
        isHandshakeComplete = false
        sessionId = null
        reconnectAttempts = 0

        val request = Request.Builder()
            .url(url)
            .addHeader("Device-Id", deviceId)
            .addHeader("Client-Id", deviceId)
            .addHeader("Protocol-Version", "1")
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                scope.launch {
                    sendHelloMessage()
                    startHelloTimeout()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleTextMessage(text)
                    _events.emit(WebSocketEvent.TextMessage(text))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                scope.launch {
                    _events.emit(WebSocketEvent.BinaryMessage(bytes.toByteArray()))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { _events.emit(WebSocketEvent.Disconnected) }
                reconnectAfterDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { _events.emit(WebSocketEvent.Error(t.message ?: "Connection failed")) }
                reconnectAfterDisconnect()
            }
        })
    }

    // Handshake xiaozhi — anuncia audio_params ao servidor
    internal fun buildHelloMessage(): String {
        val hello = JsonObject().apply {
            addProperty("type", "hello")
            addProperty("version", 1)
            addProperty("transport", "websocket")
            add("audio_params", JsonObject().apply {
                addProperty("format", "opus")
                addProperty("sample_rate", 16000)
                addProperty("channels", 1)
                addProperty("frame_duration", 60)
            })
        }
        return gson.toJson(hello)
    }

    private fun sendHelloMessage() {
        sendTextMessage(buildHelloMessage())
    }

    internal fun handleTextMessage(text: String) {
        val json = gson.fromJson(text, JsonObject::class.java)
        when (json?.get("type")?.asString) {
            "hello" -> handleHelloResponse(json)
        }
    }

    private fun handleHelloResponse(json: JsonObject) {
        val transport = json.get("transport")?.asString
        if (transport == "websocket") {
            sessionId = json.get("session_id")?.asString
            isHandshakeComplete = true
            helloTimeoutJob?.cancel()
            scope.launch {
                _events.emit(WebSocketEvent.HelloReceived)
                _events.emit(WebSocketEvent.Connected)
            }
        } else {
            scope.launch { _events.emit(WebSocketEvent.Error("Handshake failed: transport mismatch")) }
        }
    }

    private fun startHelloTimeout() {
        helloTimeoutJob = scope.launch {
            delay(HELLO_TIMEOUT)
            if (!isHandshakeComplete) {
                _events.emit(WebSocketEvent.Error("Handshake timeout"))
                disconnect()
            }
        }
    }

    internal fun buildListenStartMessage(mode: String = "auto"): String {
        val message = JsonObject().apply {
            sessionId?.let { addProperty("session_id", it) }
            addProperty("type", "listen")
            addProperty("state", "start")
            addProperty("mode", mode)
        }
        return gson.toJson(message)
    }

    internal fun buildListenStopMessage(): String {
        val message = JsonObject().apply {
            sessionId?.let { addProperty("session_id", it) }
            addProperty("type", "listen")
            addProperty("state", "stop")
        }
        return gson.toJson(message)
    }

    internal fun buildAbortMessage(reason: String = "user_interrupt"): String {
        val message = JsonObject().apply {
            sessionId?.let { addProperty("session_id", it) }
            addProperty("type", "abort")
            addProperty("reason", reason)
        }
        return gson.toJson(message)
    }

    fun sendStartListening(mode: String = "auto") {
        sendTextMessage(buildListenStartMessage(mode))
    }

    fun sendStopListening() {
        sendTextMessage(buildListenStopMessage())
    }

    fun sendAbort(reason: String = "user_interrupt") {
        sendTextMessage(buildAbortMessage(reason))
    }

    fun sendTextMessage(message: String) {
        if (isConnected && webSocket != null) {
            webSocket!!.send(message)
        }
    }

    fun sendBinaryMessage(data: ByteArray) {
        if (isConnected && isHandshakeComplete && webSocket != null) {
            webSocket!!.send(okio.ByteString.of(*data))
        }
    }

    // Backoff exponencial — evita bombardear servidor indisponível
    private fun reconnectAfterDisconnect() {
        isConnected = false
        isHandshakeComplete = false
        sessionId = null
        helloTimeoutJob?.cancel()

        if (!shouldReconnect) return

        scope.launch {
            if (lastUrl != null && lastDeviceId != null && lastToken != null) {
                val delayMs = minOf(
                    RECONNECT_BASE_DELAY_MS * (1L shl reconnectAttempts),
                    RECONNECT_MAX_DELAY_MS
                )
                reconnectAttempts++
                delay(delayMs)
                connect(lastUrl!!, lastDeviceId!!, lastToken!!)
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        helloTimeoutJob?.cancel()
        webSocket?.close(1000, "Normal close")
        webSocket = null
        isConnected = false
        isHandshakeComplete = false
        sessionId = null
        lastUrl = null
        lastDeviceId = null
        lastToken = null
    }

    fun isConnected(): Boolean = isConnected && isHandshakeComplete

    fun getSessionId(): String? = sessionId

    fun cleanup() {
        disconnect()
        scope.cancel()
    }

    companion object {
        private const val TAG = "WebSocketManager"
        private const val HELLO_TIMEOUT = 15000L
        private const val RECONNECT_BASE_DELAY_MS = 2000L
        private const val RECONNECT_MAX_DELAY_MS = 30000L

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }
}
