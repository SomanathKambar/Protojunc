package com.tej.protojunc.bluetooth

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Looper
import android.media.AudioRecord
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

class AndroidWifiDirectCallManager(
    private val context: Context,
    private val scope: CoroutineScope
) : WifiDirectCallManager {

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: Channel? = null

    private val _devices = MutableStateFlow<List<WifiP2pDeviceDomain>>(emptyList())
    override val devices: StateFlow<List<WifiP2pDeviceDomain>> = _devices.asStateFlow()

    private val _status = MutableStateFlow("IDLE")
    override val status: StateFlow<String> = _status.asStateFlow()

    private val _isGroupOwner = MutableStateFlow(false)
    override val isGroupOwner: StateFlow<Boolean> = _isGroupOwner.asStateFlow()

    private val _connectionInfo = MutableStateFlow("")
    override val connectionInfo: StateFlow<String> = _connectionInfo.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peers ->
                        val list = peers.deviceList.map {
                            WifiP2pDeviceDomain(it.deviceName, it.deviceAddress, getStatusString(it.status))
                        }
                        _devices.value = list
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            _isGroupOwner.value = info.isGroupOwner
                            val groupOwnerAddress = info.groupOwnerAddress.hostAddress
                            _connectionInfo.value = if (info.isGroupOwner) "Host (IP: $groupOwnerAddress)" else "Client (Host: $groupOwnerAddress)"
                            
                            if (info.isGroupOwner) {
                                startSocketServer()
                            } else {
                                groupOwnerAddress?.let { connectToSocketServer(it) }
                            }
                            _status.value = "CONNECTED"
                        }
                    } else {
                        _status.value = "DISCONNECTED"
                        _connectionInfo.value = ""
                    }
                }
            }
        }
    }

    init {
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
    }

    @SuppressLint("MissingPermission")
    override fun discoverPeers() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _status.value = "DISCOVERING"
            }
            override fun onFailure(reason: Int) {
                _status.value = "DISCOVERY_FAILED: $reason"
            }
        })
    }

    override fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, null)
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: WifiP2pDeviceDomain) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _status.value = "CONNECTING"
            }
            override fun onFailure(reason: Int) {
                _status.value = "CONNECT_FAILED: $reason"
            }
        })
    }

    override fun disconnect() {
        manager?.removeGroup(channel, null)
    }

    override fun startSocketServer() {
        scope.launch(Dispatchers.IO) {
            try {
                val serverSocket = java.net.ServerSocket(8888)
                val client = serverSocket.accept()
                handleSocket(client)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun connectToSocketServer(ip: String) {
        scope.launch(Dispatchers.IO) {
            try {
                // Retry loop
                var attempts = 0
                while (attempts < 5) {
                    try {
                        val socket = java.net.Socket(ip, 8888)
                        handleSocket(socket)
                        break
                    } catch (e: Exception) {
                        attempts++
                        kotlinx.coroutines.delay(1000)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun handleSocket(socket: java.net.Socket) {
        // Reuse similar logic to Bluetooth: AudioRecord -> Socket -> AudioTrack
        // For brevity, using simple stream copy for now or reuse a common StreamHandler class
        // But since I need to keep it isolated in this file as requested:
        
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        
        // Start Audio (Bidirectional)
        val minBufSizeRec = AudioRecord.getMinBufferSize(16000, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val minBufSizeTrack = AudioTrack.getMinBufferSize(16000, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        
        @SuppressLint("MissingPermission")
        val audioRecord = AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, minBufSizeRec * 2)
        val audioTrack = AudioTrack(android.media.AudioManager.STREAM_VOICE_CALL, 16000, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, minBufSizeTrack * 2, AudioTrack.MODE_STREAM)
        
        audioRecord.startRecording()
        audioTrack.play()
        
        val isRunning = true
        
        // Sender Coroutine
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBufSizeRec)
            while (isRunning) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    try { output.write(buffer, 0, read) } catch (e: Exception) { break }
                }
            }
        }
        
        // Receiver Loop
        val buffer = ByteArray(4096)
        while (isRunning) {
            try {
                val read = input.read(buffer)
                if (read > 0) {
                    audioTrack.write(buffer, 0, read)
                } else {
                    break
                }
            } catch (e: Exception) {
                break
            }
        }
        
        audioRecord.release()
        audioTrack.release()
    }

    private fun getStatusString(status: Int): String {
        return when (status) {
            WifiP2pDevice.AVAILABLE -> "Available"
            WifiP2pDevice.INVITED -> "Invited"
            WifiP2pDevice.CONNECTED -> "Connected"
            WifiP2pDevice.FAILED -> "Failed"
            WifiP2pDevice.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }
    }
}
