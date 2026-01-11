package com.tej.protojunc.p2p.discovery.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.tej.protojunc.p2p.discovery.`interface`.DiscoveredDevice
import com.tej.protojunc.p2p.discovery.`interface`.DiscoveryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import co.touchlab.kermit.Logger

class AndroidDiscoveryManager(
    private val context: Context
) : DiscoveryManager {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        Logger.d { "Starting discovery request" }
    }

    override fun stopDiscovery() {
        Logger.d { "Stopping discovery request" }
    }

    override val discoveredDevices: Flow<List<DiscoveredDevice>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Logger.e { "Bluetooth LE Scanner not available" }
            close()
            return@callbackFlow
        }

        val devices = mutableMapOf<String, DiscoveredDevice>()

        val callback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val id = device.address
                    val name = device.name ?: "Unknown"
                    val rssi = result.rssi
                    
                    devices[id] = DiscoveredDevice(id, name, rssi)
                    trySend(devices.values.toList())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Logger.e { "Scan failed: $errorCode" }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
             scanner.startScan(callback)
             Logger.d { "Started BLE scan" }
        } else {
            Logger.w { "Missing BLUETOOTH_SCAN permission" }
        }

        awaitClose {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                scanner.stopScan(callback)
                Logger.d { "Stopped BLE scan" }
            }
        }
    }
}
