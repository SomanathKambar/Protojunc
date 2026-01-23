package com.tej.protojunc.core.signaling.webrtc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger
import com.tej.protojunc.core.models.SignalingMessage

/**
 * Manages WebSocket signaling for WebRTC.
 * Handles exchanging SDP offers/answers and ICE candidates.
 */
class SignalingManager(
    private val client: HttpClient,
    private val signalingUrl: String = "ws://localhost:8080/signaling/default?device=App",
) {
    private var session: WebSocketSession? = null
    private val _incomingMessages = MutableSharedFlow<SignalingMessage>()
    val incomingMessages = _incomingMessages.asSharedFlow()

    suspend fun connect() {
        try {
            session = client.webSocketSession {
                url(signalingUrl)
            }
            Logger.i { "Signaling connected to $signalingUrl" }
            
            // Listen for incoming messages
            session?.incoming?.receiveAsFlow()
                ?.filter { it is Frame.Text }
                ?.map { (it as Frame.Text).readText() }
                ?.collect { text ->
                    try {
                        val message = Json.decodeFromString<SignalingMessage>(text)
                        _incomingMessages.emit(message)
                    } catch (e: Exception) {
                        Logger.e(e) { "Failed to parse signaling message: $text" }
                    }
                }
        } catch (e: Exception) {
            Logger.e(e) { "Signaling connection failed" }
            throw e
        }
    }

    suspend fun send(message: SignalingMessage) {
        val text = Json.encodeToString(SignalingMessage.serializer(), message)
        session?.send(Frame.Text(text))
    }

    suspend fun close() {
        session?.close()
        session = null
    }
}
