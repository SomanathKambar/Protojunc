package com.tej.directo.discovery

import kotlinx.coroutines.flow.Flow

interface DiscoveryManager {
    // BLE: Broadcast our SDP payload to nearby devices
    suspend fun startAdvertising(payload: String)

    // BLE: Scan for other peers broadcasting their SDP
    fun observeNearbyPeers(): Flow<PeerDiscovered>

    // QR: Generate a QR code from our SDP payload (returns the data for the UI to render)
    fun generateQrData(payload: String): String

    suspend fun stopDiscovery()
}

data class PeerDiscovered(
    val name: String,
    val remoteSdpBase64: String,
    val rssi: Int // Signal strength
)
