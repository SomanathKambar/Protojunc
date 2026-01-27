package com.tej.protojunc.signaling

import kotlinx.coroutines.flow.*
import com.tej.protojunc.core.models.SignalingMessage

actual class XmppSignalingClient actual constructor(
    private val jid: String,
    private val password: String,
    private val host: String,
    private val domain: String
) : SignalingClient {

    private val _state = MutableStateFlow(SignalingState.IDLE)
    override actual val state: StateFlow<SignalingState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    override actual val messages: Flow<SignalingMessage> = _messages.asSharedFlow()

    override actual suspend fun connect() {
        // XMPP not yet implemented on iOS
        _state.value = SignalingState.ERROR
    }

    override actual suspend fun sendMessage(message: SignalingMessage) {
        // No-op on iOS
    }

    override actual suspend fun disconnect() {
        _state.value = SignalingState.IDLE
    }
}
