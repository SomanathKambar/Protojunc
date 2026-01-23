package com.tej.protojunc.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class IosPeripheralAdvertiser : PeripheralAdvertiser {
    override suspend fun startAdvertising(roomCode: String, serviceUuid: String, sdpPayload: String) {
        // Stub
    }

    override fun stopAdvertising() {
        // Stub
    }

    override fun observeReceivedMessages(): Flow<String> = emptyFlow()

    override val advertisingState: Flow<Boolean> = emptyFlow()
}
