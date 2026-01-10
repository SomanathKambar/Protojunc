package com.tej.directo.p2p.impl.server

import com.tej.directo.p2p.core.signaling.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import co.touchlab.kermit.Logger

class KtorSignalingClient(
    private val host: String = "localhost",
    private val port: Int = 8080,
    private val roomCode: String
) : SignalingClient {
    
    private val client = HttpClient {
        install(WebSockets)
    }
    
    private var session: DefaultClientWebSocketSession? = null
    
    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    override val messages = _messages.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun connect() {
        _state.value = SignalingState.CONNECTING
        try {
            client.webSocket(method = HttpMethod.Get, host = host, port = port, path = "/signaling/$roomCode") {
                session = this
                _state.value = SignalingState.CONNECTED
                Logger.d { "Connected to signaling server room: $roomCode" }
                
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val message = Json.decodeFromString<SignalingMessage>(text)
                            _messages.emit(message)
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "WebSocket receiving error" }
                } finally {
                    _state.value = SignalingState.DISCONNECTED
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Signaling connection failed" }
            _state.value = SignalingState.ERROR
        }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        val text = Json.encodeToString(message)
        session?.send(Frame.Text(text)) ?: Logger.w { "Cannot send message, no active session" }
    }

    override suspend fun disconnect() {
        session?.close()
        session = null
        _state.value = SignalingState.DISCONNECTED
    }
}
