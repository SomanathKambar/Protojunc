package com.tej.protojunc.p2p.core.orchestrator

import com.tej.protojunc.signaling.SignalingClient
import com.tej.protojunc.core.models.SignalingMessage
import com.tej.protojunc.signaling.SignalingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger

enum class TransportPriority {
    LAN, WIFI_DIRECT, BLE, CLOUD
}

data class TransportCandidate(
    val priority: TransportPriority,
    val client: SignalingClient
)

/**
 * Manages multiple signaling transports and picks the best one.
 * Implements Phase 2 of Protojunc 2.0.
 */
class LinkOrchestrator(
    private val scope: CoroutineScope,
    private val onTransportChanged: (TransportPriority, Int) -> Unit = { _, _ -> }
) : com.tej.protojunc.signaling.SignalingClient {
    private val _activeTransports = MutableStateFlow<List<TransportCandidate>>(emptyList())
    
    private val _bestTransport = MutableStateFlow<TransportCandidate?>(null)
    val bestTransport = _bestTransport.asStateFlow()

    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 100)
    override val messages = _messages.asSharedFlow()

    override suspend fun connect() {
        // In LinkOrchestrator, connect means connect all candidate transports
        _activeTransports.value.forEach { 
            scope.launch { it.client.connect() }
        }
    }

    override suspend fun disconnect() {
        _activeTransports.value.forEach { 
            scope.launch { it.client.disconnect() }
        }
    }

    fun addTransport(priority: TransportPriority, client: SignalingClient) {
        val candidate = TransportCandidate(priority, client)
        _activeTransports.value += candidate
        
        // Observe this transport
        scope.launch {
            client.state.collect { state ->
                Logger.d { "Transport ${priority.name} state changed to $state" }
                updateBestTransport()
            }
        }

        scope.launch {
            client.messages.collect { msg ->
                _messages.emit(msg)
            }
        }
    }

    private suspend fun updateBestTransport() {
        val candidates = _activeTransports.value
        
        // Pick best transport
        val best = candidates
            .filter { it.client.state.first() == SignalingState.CONNECTED }
            .minByOrNull { it.priority.ordinal }
        
        if (best != _bestTransport.value) {
            Logger.i { "Switching to best transport: ${best?.priority?.name ?: "NONE"}" }
            _bestTransport.value = best
            
            best?.let {
                val bitrate = when(it.priority) {
                    TransportPriority.LAN -> 5000
                    TransportPriority.WIFI_DIRECT -> 4000
                    TransportPriority.BLE -> 200
                    TransportPriority.CLOUD -> 1500
                }
                onTransportChanged(it.priority, bitrate)
            }
        }

        // Update overall state
        _state.value = if (candidates.any { it.client.state.first() == SignalingState.CONNECTED }) {
            SignalingState.CONNECTED
        } else if (candidates.any { it.client.state.first() == SignalingState.CONNECTING }) {
            SignalingState.CONNECTING
        } else {
            SignalingState.IDLE
        }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        val transport = _bestTransport.value
        if (transport != null) {
            transport.client.sendMessage(message)
        } else {
            Logger.w { "No active transport to send message: ${message.type}. Attempting broadcast..." }
            broadcastMessage(message)
        }
    }

    suspend fun broadcastMessage(message: SignalingMessage) {
        val connected = _activeTransports.value.filter { it.client.state.first() == SignalingState.CONNECTED }
        Logger.d { "Broadcasting ${message.type} to ${connected.size} transports" }
        connected.forEach { transport ->
            scope.launch {
                try {
                    transport.client.sendMessage(message)
                } catch (e: Exception) {
                    Logger.e { "Failed to send on ${transport.priority}: ${e.message}" }
                }
            }
        }
    }
}
