package com.tej.directo.signaling

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import co.touchlab.kermit.Logger
// We will need to inject the host or pass it in since Platform.kt is in shared
// For now, let's keep it as is and we'll fix the host issue by passing it from the caller.

import io.ktor.client.plugins.logging.*

class KtorSignalingClient(
    private val host: String,
    private val port: Int = 8080,
    private val roomCode: String,
    private val deviceName: String = "GenericDevice",
    private val useSsl: Boolean = false
) : SignalingClient {
    
    private val client = HttpClient {
        install(WebSockets)
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d(tag = "Ktor") { message }
                }
            }
            level = LogLevel.INFO
        }
    }
    
    private var session: DefaultClientWebSocketSession? = null
    
    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>(replay = 5, extraBufferCapacity = 10)
    override val messages = _messages.asSharedFlow()

    override suspend fun connect() {
        if (_state.value == SignalingState.CONNECTED) return
        
        val protocol = if (useSsl) "wss" else "ws"
        val sanitizedRoom = if (roomCode.isBlank()) "default" else roomCode
        var retryDelay = 1000L
        
        while (currentCoroutineContext().isActive) {
            _state.value = SignalingState.CONNECTING
            val path = "/signaling/$sanitizedRoom"
            
            try {
                client.webSocket(
                    method = HttpMethod.Get, 
                    host = host, 
                    port = port, 
                    path = "$path?device=$deviceName"
                ) {
                    session = this
                    _state.value = SignalingState.CONNECTED
                    retryDelay = 1000L
                    Logger.i { "Trunk Established: $sanitizedRoom" }
                    
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val message = Json.decodeFromString<SignalingMessage>(text)
                                    _messages.emit(message)
                                } catch (e: Exception) {
                                    Logger.e { "Signaling Decode Error: ${e.message}" }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.w { "WebSocket session closed: ${e.message}" }
                    } finally {
                        session = null
                        if (_state.value == SignalingState.CONNECTED) {
                            _state.value = SignalingState.DISCONNECTED
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = SignalingState.ERROR
                Logger.e { "Signaling connection failed to $host:$port. Retrying in ${retryDelay}ms..." }
            }

            if (!currentCoroutineContext().isActive) break
            
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30000L)
        }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        val text = Json.encodeToString(message)
        session?.send(Frame.Text(text)) ?: co.touchlab.kermit.Logger.w { "Cannot send message, no active session" }
    }

    override suspend fun disconnect() {
        _state.value = SignalingState.IDLE // Set to IDLE to stop retry loop via isActive check
        session?.close()
        session = null
    }
}
