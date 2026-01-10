package com.tej.directo.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosBluetoothCallManager : BluetoothCallManager {
    private val _devices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val devices: StateFlow<List<BluetoothDeviceDomain>> = _devices.asStateFlow()

    private val _status = MutableStateFlow(BluetoothCallStatus.IDLE)
    override val status: StateFlow<BluetoothCallStatus> = _status.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>("Bluetooth Calls not supported on iOS yet")
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    override val localName: String = "iOS Device"
    
    private val _pairingCode = MutableStateFlow("1234")
    override val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    override fun setPairingCode(code: String) { _pairingCode.value = code }
    override fun requestDiscoverable() {}

    override fun startScanning() {
        _errorMessage.value = "Not implemented on iOS"
    }

    override fun stopScanning() {}
    override fun connect(address: String, isVideo: Boolean) {}
    override fun startServer() {}
    override fun startAudio() {}
    override fun stopCall() {}
    override fun toggleSpeakerphone(on: Boolean) {}
    override fun refreshPairedDevices() {}
}
