package com.tej.directo.discovery

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import java.util.UUID

class AndroidPeripheralAdvertiser(private val bluetoothAdapter: BluetoothAdapter) : PeripheralAdvertiser {
    override suspend fun startAdvertising(serviceUuid: String, sdpPayload: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UUID.fromString(serviceUuid)))
            .setIncludeDeviceName(true)
            .build()

        bluetoothAdapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            // Log success/failure
        })
    }

    override fun stopAdvertising() {
        TODO("Not yet implemented")
    }
}