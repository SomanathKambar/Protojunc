package com.tej.protojunc.signaling

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import com.tej.protojunc.core.models.SignalingMessage

expect class XmppSignalingClient(
    jid: String,
    password: String = "password",
    host: String = "10.0.2.2",
    domain: String = "example.com"
) : SignalingClient {
    override val state: StateFlow<SignalingState>
    override val messages: Flow<SignalingMessage>

    override suspend fun connect()
    override suspend fun sendMessage(message: SignalingMessage)
    override suspend fun disconnect()
}
