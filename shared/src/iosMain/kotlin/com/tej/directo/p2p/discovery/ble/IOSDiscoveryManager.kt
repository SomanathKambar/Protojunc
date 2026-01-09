package com.tej.directo.p2p.discovery.ble

import com.tej.directo.p2p.discovery.`interface`.DiscoveredDevice
import com.tej.directo.p2p.discovery.`interface`.DiscoveryManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.*
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import co.touchlab.kermit.Logger

class IOSDiscoveryManager : DiscoveryManager {
    private val centralManager: CBCentralManager
    private val delegate = CentralDelegate()
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    init {
        centralManager = CBCentralManager(delegate, null)
    }

    override fun startDiscovery() {
        if (centralManager.state == CBCentralManagerStatePoweredOn) {
            centralManager.scanForPeripheralsWithServices(null, null)
             Logger.d { "Started iOS BLE scan" }
        }
    }

    override fun stopDiscovery() {
        centralManager.stopScan()
         Logger.d { "Stopped iOS BLE scan" }
    }

    override val discoveredDevices: Flow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private inner class CentralDelegate : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            if (central.state == CBCentralManagerStatePoweredOn) {
                // Auto start or wait for explicit call? Prompt implies manager handles it.
                // We'll wait for explicit startDiscovery call, but log state.
                Logger.d { "Bluetooth Powered On" }
            }
        }

        override fun centralManager(central: CBCentralManager, didDiscoverPeripheral: CBPeripheral, advertisementData: Map<Any?, *>, RSSI: NSNumber) {
            val id = didDiscoverPeripheral.identifier.UUIDString
            val name = didDiscoverPeripheral.name ?: "Unknown"
            val rssi = RSSI.intValue

            val current = _devices.value.toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx >= 0) {
                current[idx] = DiscoveredDevice(id, name, rssi)
            } else {
                current.add(DiscoveredDevice(id, name, rssi))
            }
            _devices.value = current
        }
    }
}
