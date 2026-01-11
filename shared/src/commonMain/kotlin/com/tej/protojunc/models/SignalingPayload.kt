package com.tej.protojunc.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class SignalingPayload(
    val sdp: String,
    val type: String, // "OFFER" or "ANSWER"
    val iceCandidates: List<IceCandidateModel>,
    val timestamp: Long
)

@Serializable
data class IceCandidateModel(
    val sdp: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)
