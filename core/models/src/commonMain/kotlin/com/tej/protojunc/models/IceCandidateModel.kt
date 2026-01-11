package com.tej.protojunc.models

import kotlinx.serialization.Serializable

@Serializable
data class IceCandidateModel(
    val sdp: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)
