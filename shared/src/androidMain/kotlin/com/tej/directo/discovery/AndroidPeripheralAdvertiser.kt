package com.tej.directo.discovery

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.content.Context
import co.touchlab.kermit.Logger
import java.util.UUID

class AndroidPeripheralAdvertiser(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) : PeripheralAdvertiser {
    private var gattServer: BluetoothGattServer? = null
    private val SERVICE_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val CHARACTERISTIC_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")

    override suspend fun startAdvertising(serviceUuid: String, sdpPayload: String) {
        if (!bluetoothAdapter.isEnabled) throw IllegalStateException("Bluetooth Off")

        // 1. Setup GATT Server to host the SDP
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        gattServer = manager.openGattServer(context, object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice, 
                requestId: Int, 
                offset: Int, 
                characteristic: BluetoothGattCharacteristic
            ) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, sdpPayload.toByteArray())
            }
        })

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID, 
            BluetoothGattCharacteristic.PROPERTY_READ, 
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        // 2. Start Advertising the service
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()

        bluetoothAdapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Logger.d { "BLE Advertising Started with GATT Server" }
            }
            override fun onStartFailure(errorCode: Int) {
                Logger.e { "BLE Advertising Failed: $errorCode" }
            }
        })
    }

    override fun stopAdvertising() {
        gattServer?.close()
    }
}
