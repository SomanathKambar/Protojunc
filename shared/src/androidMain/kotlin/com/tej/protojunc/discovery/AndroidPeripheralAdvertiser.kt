package com.tej.protojunc.discovery

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.content.Context
import co.touchlab.kermit.Logger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

class AndroidPeripheralAdvertiser(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) : PeripheralAdvertiser {
    
    private val _advertisingState = MutableStateFlow(false)
    override val advertisingState = _advertisingState.asStateFlow()

    private var advertiseCallback: AdvertiseCallback? = null

    override suspend fun startAdvertising(roomCode: String, serviceUuid: String, sdpPayload: String) {
        if (!bluetoothAdapter.isEnabled) throw IllegalStateException("Bluetooth Off")
        
        // Stop previous if any
        stopAdvertising()

        val uuid = UUID.fromString(serviceUuid)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(uuid))
            .build()

        // Service Data fits limited payload? 
        // UUID (16) + RoomCode (5) = 21 bytes. Scan Response limit is 31. Safe.
        val scanResponse = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(uuid), roomCode.toByteArray())
            .build()

        Logger.d { "Starting BLE Advertising for Service: $serviceUuid" }
        
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Logger.i { "BLE Advertising Started Successfully" }
                _advertisingState.value = true
            }
            override fun onStartFailure(errorCode: Int) {
                Logger.e { "BLE Advertising Failed: $errorCode" }
                _advertisingState.value = false
            }
        }
        advertiseCallback = callback

        bluetoothAdapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, scanResponse, callback)
    }

    override fun stopAdvertising() {
        advertiseCallback?.let {
            bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(it)
        }
        advertiseCallback = null
        _advertisingState.value = false
    }

    // Legacy support stub - Message receiving is now handled by BluetoothMeshManager's GattServer
    override fun observeReceivedMessages(): Flow<String> = emptyFlow()
}

