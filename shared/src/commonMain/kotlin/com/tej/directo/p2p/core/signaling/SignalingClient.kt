package com.tej.directo.p2p.core.signaling

import kotlinx.coroutines.flow.Flow

enum class SignalingState {
    IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

data class SignalingMessage(
    val type: Type,
    val sdp: String? = null,
    val iceCandidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val senderId: String
) {
    enum class Type { OFFER, ANSWER, ICE_CANDIDATE, BYE }
}

interface SignalingClient {
    val state: Flow<SignalingState>
    val messages: Flow<SignalingMessage>
    
    suspend fun connect()
    suspend fun sendMessage(message: SignalingMessage)
    suspend fun disconnect()
}
