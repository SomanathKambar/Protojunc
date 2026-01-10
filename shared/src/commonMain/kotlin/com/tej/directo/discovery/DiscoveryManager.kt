package com.tej.directo.discovery

import kotlinx.coroutines.flow.Flow

interface DiscoveryManager {
    // BLE: Broadcast our SDP payload to nearby devices
    suspend fun startAdvertising(roomCode: String, payload: String)

    // BLE: Scan for other peers broadcasting their SDP
    fun observeNearbyPeers(): Flow<PeerDiscovered>

    // BLE: Connect to a discovered peer and read their SDP
    suspend fun connectToPeer(peer: PeerDiscovered): String

    // BLE: Write data (e.g., Answer SDP) to a peer
    suspend fun writeToPeer(peer: PeerDiscovered, data: String)

    // BLE: Observe incoming messages (e.g., Answer SDP received by Host)
    fun observeMessages(): Flow<String>

    // QR: Generate a QR code from our SDP payload (returns the data for the UI to render)
    fun generateQrData(payload: String): String

    suspend fun stopDiscovery()
}

data class PeerDiscovered(
    val id: String,
    val name: String,
    val roomCode: String,
    val remoteSdpBase64: String,
    val rssi: Int // Signal strength
)
