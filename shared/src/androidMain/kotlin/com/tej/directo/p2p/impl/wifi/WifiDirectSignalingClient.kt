package com.tej.directo.p2p.impl.wifi

import com.tej.directo.p2p.core.signaling.SignalingClient
import com.tej.directo.p2p.core.signaling.SignalingMessage
import com.tej.directo.p2p.core.signaling.SignalingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
            val inputStream = socket?.getInputStream() ?: return@launch
            val reader = inputStream.bufferedReader()
            while (isActive) {
                try {
                    val line = reader.readLine() ?: break
                    // Here we would decode the message (JSON/Protobuf)
                    // For now, placeholder decoding
                } catch (e: Exception) {
                    break
                }
            }
            _state.value = SignalingState.DISCONNECTED
        }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        withContext(Dispatchers.IO) {
            try {
                val outputStream = socket?.getOutputStream()
                // outputStream?.write(...)
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
