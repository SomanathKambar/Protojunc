package com.tej.directo.bluetooth

import kotlinx.coroutines.flow.StateFlow

data class WifiP2pDeviceDomain(
    val name: String,
    val address: String,
    val status: String
)

interface WifiDirectCallManager {
    val devices: StateFlow<List<WifiP2pDeviceDomain>>
    val status: StateFlow<String> // IDLE, DISCOVERING, CONNECTED
    val isGroupOwner: StateFlow<Boolean>
    val connectionInfo: StateFlow<String> // IP Address

    fun discoverPeers()
    fun stopDiscovery()
    fun connect(device: WifiP2pDeviceDomain)
    fun disconnect()
    
    // Once connected via P2P, we use sockets
    fun startSocketServer()
    fun connectToSocketServer(ip: String)
}
