package com.tej.protojunc.models

import kotlinx.serialization.Serializable

@Serializable
data class NearbyPeer(
    val deviceId: String,
    val displayName: String,
    val lastSeen: Long,
    val rssi: Int,
    val isPaired: Boolean = false
)
