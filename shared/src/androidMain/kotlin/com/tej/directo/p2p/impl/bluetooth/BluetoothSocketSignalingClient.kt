package com.tej.directo.p2p.impl.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.tej.directo.p2p.core.signaling.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import co.touchlab.kermit.Logger
import java.io.IOException
import java.util.*

class BluetoothSocketSignalingClient(
    private val adapter: BluetoothAdapter,
    private val remoteDeviceAddress: String? = null, // If null, we act as Server
    private val serviceUuid: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
) : SignalingClient {
    
    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    override val messages = _messages.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun connect() {
        _state.value = SignalingState.CONNECTING
        withContext(Dispatchers.IO) {
            try {
                if (remoteDeviceAddress != null) {
                    // Client Mode
                    val device = adapter.getRemoteDevice(remoteDeviceAddress)
                    socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                    adapter.cancelDiscovery() // Discovery slows down connection
                    socket?.connect()
                } else {
                    // Server Mode
                    val serverSocket: BluetoothServerSocket? = adapter.listenUsingRfcommWithServiceRecord("Directo", serviceUuid)
                    socket = serverSocket?.accept(30000) // 30s timeout
                    serverSocket?.close()
                }
                
                if (socket != null) {
                    _state.value = SignalingState.CONNECTED
                    startListening()
                } else {
                    _state.value = SignalingState.ERROR
                }
            } catch (e: IOException) {
                Logger.e(e) { "Bluetooth Socket connection failed" }
                _state.value = SignalingState.ERROR
            }
        }
    }

    private fun startListening() {
        scope.launch {
            val reader = socket?.inputStream?.bufferedReader() ?: return@launch
            while (isActive) {
                try {
                    val line = reader.readLine() ?: break
                    val message = Json.decodeFromString<SignalingMessage>(line)
                    _messages.emit(message)
                } catch (e: Exception) {
                    Logger.e(e) { "Bluetooth Socket read error" }
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
            } catch (e: IOException) {
                Logger.e(e) { "Bluetooth Socket write error" }
            }
        }
    }

    override suspend fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Logger.e(e) { "Error closing Bluetooth socket" }
        }
        _state.value = SignalingState.DISCONNECTED
    }
}
