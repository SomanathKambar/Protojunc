package com.tej.directo.common

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import co.touchlab.kermit.Logger

class ServerHealthCheck(private val host: String, private val port: Int = 8080) {
    private val client = HttpClient()

    suspend fun isServerRunning(): Boolean {
        return try {
            val response: HttpResponse = client.get("http://$host:$port/")
            response.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
