package com.tej.directo.discovery

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.content.Context
import co.touchlab.kermit.Logger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidPeripheralAdvertiser(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) : PeripheralAdvertiser {
    private var gattServer: BluetoothGattServer? = null
    private val SERVICE_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val CHARACTERISTIC_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    
    private val _receivedMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    
    // Buffer for long writes
    private val writeBuffer = mutableMapOf<String, ByteArray>()

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
                val fullData = sdpPayload.toByteArray()
                
                if (offset >= fullData.size) {
                     // Offset out of bounds, send success with empty data to signal end
                     gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                     return
                }
                
                // Android's sendResponse has a limit (usually ~512 bytes). 
                // We provide the slice from the offset to the end. 
                // The underlying Bluetooth stack handles the MTU-sized chunking.
                val chunk = fullData.copyOfRange(offset, fullData.size)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                val deviceId = device.address
                if (preparedWrite) {
                    val current = writeBuffer[deviceId] ?: ByteArray(0)
                    // Ensure buffer is large enough for the offset
                    val newBuffer = if (offset + value.size > current.size) {
                        ByteArray(offset + value.size).apply {
                            current.copyInto(this)
                        }
                    } else current
                    
                    value.copyInto(newBuffer, offset)
                    writeBuffer[deviceId] = newBuffer
                } else {
                    val message = String(value)
                    _receivedMessages.tryEmit(message)
                }
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }

            override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
                if (execute) {
                    val data = writeBuffer[device.address]
                    if (data != null) {
                        _receivedMessages.tryEmit(String(data))
                    }
                }
                writeBuffer.remove(device.address)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        })

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID, 
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE, 
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
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

    override fun observeReceivedMessages(): Flow<String> = _receivedMessages.asSharedFlow()
}
