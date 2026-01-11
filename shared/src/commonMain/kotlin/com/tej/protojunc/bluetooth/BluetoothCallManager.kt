package com.tej.protojunc.bluetooth

import kotlinx.coroutines.flow.StateFlow

data class BluetoothDeviceDomain(
    val name: String,
    val address: String,
    val isPaired: Boolean = false
)

enum class BluetoothCallStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    AUDIO_STREAMING,
    ERROR
}

interface BluetoothCallManager {
    val devices: StateFlow<List<BluetoothDeviceDomain>>
    val status: StateFlow<BluetoothCallStatus>
    val errorMessage: StateFlow<String?>
    val localName: String
    val pairingCode: StateFlow<String>

    fun setPairingCode(code: String)
    fun requestDiscoverable()
    fun startScanning()
    fun stopScanning()
    fun connect(address: String, isVideo: Boolean)
    fun startServer() // Start listening for connections
    fun startAudio()
    fun stopCall()
    fun toggleSpeakerphone(on: Boolean)
    fun refreshPairedDevices()
}
