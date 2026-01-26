package com.tej.protojunc.signaling

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger
import com.tej.protojunc.core.models.SignalingMessage

/**
 * XMPP Implementation of the Signaling Client.
 */
class XmppSignalingClient(
    private val jid: String,
    private val host: String = "xmpp.example.com"
) : SignalingClient {
    
    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    override val messages = _messages.asSharedFlow()

    override suspend fun connect() {
        _state.value = SignalingState.CONNECTING
        delay(1000)
        _state.value = SignalingState.CONNECTED
        Logger.d { "XMPP Connected as $jid" }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        Logger.d { "Sending XMPP Jingle Message: ${message.type}" }
    }

    override suspend fun disconnect() {
        _state.value = SignalingState.DISCONNECTED
    }
}