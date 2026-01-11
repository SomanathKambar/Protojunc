package com.tej.directo.common

import kotlinx.coroutines.flow.Flow

interface ServerDiscovery {
    fun startDiscovery()
    fun stopDiscovery()
    val discoveredServer: Flow<ServerInfo?>
}

data class ServerInfo(val host: String, val port: Int)
