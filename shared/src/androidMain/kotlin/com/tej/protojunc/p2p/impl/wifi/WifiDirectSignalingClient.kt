package com.tej.protojunc.p2p.impl.wifi

import com.tej.protojunc.signaling.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.ServerSocket
import java.net.Socket
import co.touchlab.kermit.Logger

class WifiDirectSignalingClient(
    private val hostAddress: String? = null, // If null, we act as Server
    private val port: Int = 8888
) : SignalingClient {
    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    override val messages = _messages.asSharedFlow()

    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun connect() {
        _state.value = SignalingState.CONNECTING
        withContext(Dispatchers.IO) {
            try {
                if (hostAddress != null) {
                    // Client mode
                    socket = Socket(hostAddress, port)
                } else {
                    // Server mode
                    val serverSocket = ServerSocket(port)
                    socket = serverSocket.accept()
                }
                _state.value = SignalingState.CONNECTED
                startListening()
            } catch (e: Exception) {
                Logger.e(e) { "WiFi Direct Socket Connection Failed" }
                _state.value = SignalingState.ERROR
            }
        }
    }

    private fun startListening() {
        scope.launch {
            val reader = socket?.getInputStream()?.bufferedReader() ?: return@launch
            while (isActive) {
                try {
                    val line = reader.readLine() ?: break
                    val message = Json.decodeFromString<SignalingMessage>(line)
                    _messages.emit(message)
                } catch (e: Exception) {
                    Logger.e(e) { "WiFi Direct Socket read error" }
                    break
                }
            }
            _state.value = SignalingState.DISCONNECTED
        }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        withContext(Dispatchers.IO) {
            try {
                val text = Json.encodeToString(message) + "\n"
                socket?.outputStream?.write(text.toByteArray())
                socket?.outputStream?.flush()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to send message over WiFi Direct socket" }
            }
        }
    }

    override suspend fun disconnect() {
        socket?.close()
        _state.value = SignalingState.DISCONNECTED
    }
}
