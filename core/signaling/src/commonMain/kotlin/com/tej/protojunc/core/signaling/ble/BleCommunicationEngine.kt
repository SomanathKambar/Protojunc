package com.tej.protojunc.core.signaling.ble

import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.peripheral
import com.tej.protojunc.core.common.ConnectionState
import com.tej.protojunc.core.signaling.CommunicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger

/**
 * BLE implementation using Kable.
 */
class BleCommunicationEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CommunicationEngine {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private var peripheral: Peripheral? = null

    override suspend fun connect() {
        _connectionState.value = ConnectionState.Connecting
        try {
            val scanner = Scanner {
                // Filters
            }
            // Simplified: grab first advertisement
            val advertisement = scanner.advertisements.first() 
            peripheral = scope.peripheral(advertisement)
            
            peripheral?.connect()
            
            scope.launch {
                peripheral?.state?.collect { state ->
                    _connectionState.value = when(state) {
                        is State.Connected -> ConnectionState.Connected
                        is State.Connecting -> ConnectionState.Connecting
                        is State.Disconnected -> ConnectionState.Idle // Or Error depending on status
                        is State.Disconnecting -> ConnectionState.Idle
                    }
                }
            }

        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("BLE Error", e.message)
            Logger.e(e) { "BLE Connection failed" }
        }
    }

    override suspend fun disconnect() {
        peripheral?.disconnect()
    }

    override suspend fun sendData(data: ByteArray) {
        // peripheral.write(characteristic, data, WriteType.WithResponse)
    }
}
