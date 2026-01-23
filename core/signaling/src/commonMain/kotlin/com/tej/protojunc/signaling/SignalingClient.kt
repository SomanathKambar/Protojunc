package com.tej.protojunc.signaling

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SignalingMessage(
    @ProtoNumber(1) val type: Type,
    @ProtoNumber(2) val sdp: String? = null,
    @ProtoNumber(3) val iceCandidate: String? = null,
    @ProtoNumber(4) val sdpMid: String? = null,
    @ProtoNumber(5) val sdpMLineIndex: Int? = null,
    @ProtoNumber(6) val senderId: String,
    @ProtoNumber(7) val encryptedPayload: ByteArray? = null,
    @ProtoNumber(8) val ephemeralKey: String? = null,
    @ProtoNumber(9) val payload: String? = null
) {
    @Serializable
    enum class Type { 
        @ProtoNumber(0) UNKNOWN,
        @ProtoNumber(1) JOIN, 
        @ProtoNumber(2) OFFER, 
        @ProtoNumber(3) ANSWER, 
        @ProtoNumber(4) ICE_CANDIDATE, 
        @ProtoNumber(5) BYE, 
        @ProtoNumber(6) MESSAGE, 
        @ProtoNumber(7) VOICE_CALL, 
        @ProtoNumber(8) VIDEO_CALL,
        @ProtoNumber(9) ENCRYPTED,
        @ProtoNumber(10) IDENTITY
    }
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