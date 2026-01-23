package com.tej.protojunc.core.models

import kotlinx.serialization.Serializable

@Serializable
data class SignalingMessage(
    val type: String, // "offer", "answer", "candidate"
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)
