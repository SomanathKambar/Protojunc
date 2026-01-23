package com.tej.protojunc.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidGattServer(private val context: Context) : GattServer {
    private var bluetoothGattServer: BluetoothGattServer? = null
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val _receivedPackets = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val receivedPackets = _receivedPackets.asSharedFlow()
    
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val writeBuffers = mutableMapOf<String, ByteArray>()
    
    private val WRITE_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    private val NOTIFY_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device)
                Logger.i { "Device connected to GATT Server: ${device.address}" }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device)
                writeBuffers.remove(device.address)
                Logger.i { "Device disconnected from GATT Server: ${device.address}" }
            }
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
            if (characteristic.uuid == WRITE_UUID) {
                if (preparedWrite) {
                    val current = writeBuffers[device.address] ?: ByteArray(0)
                    // Ensure buffer is large enough
                    val newBuffer = if (offset + value.size > current.size) {
                        ByteArray(offset + value.size).apply {
                            current.copyInto(this)
                        }
                    } else current
                    value.copyInto(newBuffer, offset)
                    writeBuffers[device.address] = newBuffer
                } else {
                    _receivedPackets.tryEmit(value)
                }
                
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            if (execute) {
                val data = writeBuffers[device.address]
                if (data != null) {
                    _receivedPackets.tryEmit(data)
                }
            }
            writeBuffers.remove(device.address)
            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
             if (characteristic.uuid == NOTIFY_UUID) {
                 bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
             }
        }
        
        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            super.onNotificationSent(device, status)
        }
    }

    override fun startServer(serviceUuid: String) {
        if (bluetoothGattServer != null) return // Already started
        
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        
        val service = BluetoothGattService(UUID.fromString(serviceUuid), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val writeChar = BluetoothGattCharacteristic(
            WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        val notifyChar = BluetoothGattCharacteristic(
            NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        val configDesc = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        notifyChar.addDescriptor(configDesc)
        
        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)
        
        bluetoothGattServer?.addService(service)
        Logger.i { "GATT Server started with Service: $serviceUuid" }
    }

    override fun stopServer() {
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        connectedDevices.clear()
    }

    override suspend fun notifyClients(data: ByteArray) {
        val server = bluetoothGattServer ?: return
        val service = server.getService(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")) ?: return
        val notifyChar = service.getCharacteristic(NOTIFY_UUID) ?: return
        
        // Determine if we need to chunk (BLE MTU is usually small, ~20 bytes default, up to 512)
        // For simplicity in this step, we assume small packets or MTU negotiation has happened (which Kable tries to do)
        // But the server must send notification.
        
        // Setting value is deprecated in API 33 but necessary for older API logic or helper wrapper
        // Use legacy approach for broad compatibility or check API level
        notifyChar.value = data
        
        connectedDevices.forEach { device ->
            try {
                server.notifyCharacteristicChanged(device, notifyChar, false)
            } catch (e: Exception) {
                Logger.e { "Failed to notify device ${device.address}" }
            }
        }
    }
}
