package com.tej.protojunc.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.tej.protojunc.models.PairedDevice
import com.tej.protojunc.models.PairedDeviceRepository

class AndroidPairedDeviceRepository(context: Context) : PairedDeviceRepository {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<PairedDevice> {
        return adapter?.bondedDevices?.map { device ->
            PairedDevice(
                id = device.address,
                name = device.name ?: "Unknown",
                isConnected = false // Hard to tell without connecting
            )
        } ?: emptyList()
    }
}
