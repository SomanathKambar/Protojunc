package com.tej.directo.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class SignalingPayload(
    @ProtoNumber(1) val sdp: String,
    @ProtoNumber(2) val type: String, // "OFFER" or "ANSWER"
    @ProtoNumber(3) val iceCandidates: List<IceCandidateModel>,
    @ProtoNumber(4) val timestamp: Long
)

@Serializable
data class IceCandidateModel(
    @ProtoNumber(1) val sdp: String,
    @ProtoNumber(2) val sdpMid: String?,
    @ProtoNumber(3) val sdpMLineIndex: Int
)
