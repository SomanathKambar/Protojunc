package com.tej.directo.p2p.discovery.`interface`

import kotlinx.coroutines.flow.Flow

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val rssi: Int
)

interface DiscoveryManager {
    fun startDiscovery()
    fun stopDiscovery()
    val discoveredDevices: Flow<List<DiscoveredDevice>>
}
