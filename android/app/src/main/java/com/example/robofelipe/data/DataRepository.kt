package com.example.robofelipe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

interface RobotRepository {
    suspend fun sendCommand(code: Int): Result<Unit>
    suspend fun ping(): Result<Unit>
}

class DefaultRobotRepository(private val hostProvider: () -> String) : RobotRepository {

    override suspend fun sendCommand(code: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("http://${hostProvider()}/control?var=robot&val=$code")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
            }
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }.map { }
    }

    override suspend fun ping(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("http://${hostProvider()}/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
            }
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }.map { }
    }
}
