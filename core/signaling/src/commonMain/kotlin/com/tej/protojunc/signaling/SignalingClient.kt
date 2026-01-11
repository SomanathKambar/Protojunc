package com.tej.protojunc.signaling

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class SignalingMessage(
    val type: Type,
    val sdp: String? = null,
    val iceCandidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val senderId: String
) {
    @Serializable
    enum class Type { JOIN, OFFER, ANSWER, ICE_CANDIDATE, BYE, MESSAGE, VOICE_CALL, VIDEO_CALL }
}

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