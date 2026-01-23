package com.tej.protojunc.models

import kotlinx.serialization.Serializable

@Serializable
data class PairedDevice(
    val id: String, // MAC Address or UUID
    val name: String,
    val isConnected: Boolean = false
)

interface PairedDeviceRepository {
    fun getPairedDevices(): List<PairedDevice>
}
