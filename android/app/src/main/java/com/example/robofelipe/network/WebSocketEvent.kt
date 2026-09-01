package com.example.robofelipe.network

sealed class WebSocketEvent {
    object Connected : WebSocketEvent()
    object Disconnected : WebSocketEvent()
    data class TextMessage(val message: String) : WebSocketEvent()
    data class BinaryMessage(val data: ByteArray) : WebSocketEvent()
    data class Error(val error: String) : WebSocketEvent()
    object HelloReceived : WebSocketEvent()
}
