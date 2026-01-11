package com.tej.protojunc.p2p.core.discovery

import kotlinx.coroutines.flow.Flow

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val connectionType: ConnectionType,
    val metadata: Map<String, String> = emptyMap()
)

enum class ConnectionType {
    BLE, WIFI_DIRECT, BT_SOCKET, ONLINE, XMPP, QR
}

enum class ConnectionMode {
    ADVERTISE, SCAN, QR_SHOW, QR_SCAN
}

interface DiscoveryClient {
    val discoveredPeers: Flow<List<DiscoveredPeer>>
    
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
}
