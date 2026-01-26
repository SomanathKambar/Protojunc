package com.tej.protojunc.signaling

import kotlinx.coroutines.flow.Flow
import com.tej.protojunc.core.models.SignalingMessage

enum class SignalingState {
    IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

interface SignalingClient {
    val state: Flow<SignalingState>
    val messages: Flow<SignalingMessage>
    
    suspend fun connect()
    suspend fun sendMessage(message: SignalingMessage)
    suspend fun disconnect()
}