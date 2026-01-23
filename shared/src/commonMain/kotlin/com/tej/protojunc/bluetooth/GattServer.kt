package com.tej.protojunc.bluetooth

import kotlinx.coroutines.flow.Flow

interface GattServer {
    fun startServer(serviceUuid: String)
    fun stopServer()
    val receivedPackets: Flow<ByteArray>
    suspend fun notifyClients(data: ByteArray)
}
