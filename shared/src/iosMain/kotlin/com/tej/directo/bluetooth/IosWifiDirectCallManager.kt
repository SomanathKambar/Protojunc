package com.tej.directo.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosWifiDirectCallManager : WifiDirectCallManager {
    private val _devices = MutableStateFlow<List<WifiP2pDeviceDomain>>(emptyList())
    override val devices: StateFlow<List<WifiP2pDeviceDomain>> = _devices.asStateFlow()

    private val _status = MutableStateFlow("IDLE")
    override val status: StateFlow<String> = _status.asStateFlow()

    private val _isGroupOwner = MutableStateFlow(false)
    override val isGroupOwner: StateFlow<Boolean> = _isGroupOwner.asStateFlow()

    private val _connectionInfo = MutableStateFlow("")
    override val connectionInfo: StateFlow<String> = _connectionInfo.asStateFlow()

    override fun discoverPeers() {}
    override fun stopDiscovery() {}
    override fun connect(device: WifiP2pDeviceDomain) {}
    override fun disconnect() {}
    override fun startSocketServer() {}
    override fun connectToSocketServer(ip: String) {}
}
