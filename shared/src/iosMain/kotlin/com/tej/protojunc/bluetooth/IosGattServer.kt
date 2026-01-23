package com.tej.protojunc.bluetooth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class IosGattServer : GattServer {
    override fun startServer(serviceUuid: String) {
        // Todo: Implement iOS CBPeripheralManager
    }

    override fun stopServer() {
    }

    override val receivedPackets: Flow<ByteArray> = emptyFlow()

    override suspend fun notifyClients(data: ByteArray) {
    }
}
