package com.tej.protojunc.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tej.protojunc.core.common.ConnectionState
import com.tej.protojunc.core.signaling.CommunicationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Orchestrates communication engines.
 * Allows switching between protocols (e.g., XMPP for chat, WebRTC for video).
 */
class CommunicationViewModel : ViewModel(), KoinComponent {

    // Lazy injection of engines using Koin
    private val webRtcEngine: CommunicationEngine by inject(named("WebRTC"))
    // XMPP might not be available on iOS (if only defined in Android module), 
    // so we should handle this gracefully or assume this VM is used in context where it exists.
    // For this showcase, we assume the platform provides it or we fallback.
    private val xmppEngine: CommunicationEngine by inject(named("XMPP")) 
    private val bleEngine: CommunicationEngine by inject(named("BLE"))

    private var activeEngine: CommunicationEngine? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _activeProtocol = MutableStateFlow("None")
    val activeProtocol: StateFlow<String> = _activeProtocol.asStateFlow()

    init {
        // Start with XMPP (Chat) by default
        switchToChat()
    }

    /**
     * Switches to XMPP engine for text chat.
     */
    fun switchToChat() {
        switchEngine(xmppEngine, "XMPP")
    }

    /**
     * Switches to WebRTC engine for video call.
     */
    fun switchToVideoCall() {
        switchEngine(webRtcEngine, "WebRTC")
    }

    /**
     * Switches to BLE engine.
     */
    fun switchToBle() {
        switchEngine(bleEngine, "BLE")
    }

    private fun switchEngine(newEngine: CommunicationEngine, protocolName: String) {
        viewModelScope.launch {
            // 1. Disconnect current engine
            activeEngine?.disconnect()
            
            // 2. Update active engine
            activeEngine = newEngine
            _activeProtocol.value = protocolName
            
            // 3. Observe new engine's state
            launch {
                newEngine.connectionState.collect { state ->
                    _connectionState.value = state
                }
            }

            // 4. Connect new engine
            // Resilience: Exponential backoff could be implemented here or inside the engine.
            newEngine.connect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            activeEngine?.disconnect()
        }
    }
}
