package com.example.robofelipe.audio

sealed class AudioEvent {
    data class AudioData(val data: ByteArray) : AudioEvent()
    data class Error(val message: String) : AudioEvent()
}
