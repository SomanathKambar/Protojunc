package com.tej.protojunc.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class AndroidBluetoothCallManager(
    private val context: Context,
    private val scope: CoroutineScope
) : BluetoothCallManager {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val APP_UUID: UUID = UUID.fromString("e0cbf06c-cd8b-4647-bb8a-263b43f0f974")
    private val APP_NAME = "ProtojuncBluetoothCall"

    private val _devices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val devices: StateFlow<List<BluetoothDeviceDomain>> = _devices.asStateFlow()

    private val _pairingCode = MutableStateFlow("1234")
    override val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _status = MutableStateFlow(BluetoothCallStatus.IDLE)
    override val status: StateFlow<BluetoothCallStatus> = _status.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    override val localName: String
        get() = try {
            bluetoothAdapter?.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Unknown Device"
        }

    private var connectJob: Job? = null
    private var acceptJob: Job? = null
    private val connectedThreads = ConcurrentHashMap<String, ConnectedThread>()
    
    // Audio configuration
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isStreaming = false
    private var isScanning = false
    
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        registerStateReceiver()
    }
    
    override fun refreshPairedDevices() {
        val current = _devices.value.filter { !it.isPaired }.toMutableList()
        val paired = getPairedDevicesList()
        _devices.value = paired + current
    }
    
    private fun getPairedDevicesList(): List<BluetoothDeviceDomain> {
        return try {
            val paired = bluetoothAdapter?.bondedDevices
            paired?.map { 
                BluetoothDeviceDomain(it.name ?: "Bonded Device", it.address, isPaired = true) 
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }
    
    private fun registerStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                 if (BluetoothDevice.ACTION_ACL_DISCONNECTED == intent.action) {
                     val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                     device?.address?.let { addr ->
                         connectedThreads[addr]?.cancel()
                         connectedThreads.remove(addr)
                         if (connectedThreads.isEmpty()) {
                             stopCall()
                         }
                     }
                 }
                 if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                     val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                     if (state == BluetoothAdapter.STATE_OFF) {
                         stopCall()
                         _status.value = BluetoothCallStatus.IDLE
                     }
                 }
            }
        }, filter)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                         val newDevice = BluetoothDeviceDomain(device.name ?: "New Device", device.address, isPaired = device.bondState == BluetoothDevice.BOND_BONDED)
                         val currentList = _devices.value.toMutableList()
                         if (currentList.none { it.address == newDevice.address }) {
                            currentList.add(newDevice)
                            _devices.value = currentList
                         }
                    }
                }
            }
        }
    }
    
    override fun toggleSpeakerphone(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    override fun setPairingCode(code: String) {
        _pairingCode.value = code
    }
    
    override fun requestDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(discoverableIntent)
    }

    override fun startScanning() {
        if (bluetoothAdapter == null) return
        if (!isScanning) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
            isScanning = true
        }
        refreshPairedDevices()
        _status.value = BluetoothCallStatus.SCANNING
        bluetoothAdapter.startDiscovery()
    }

    override fun stopScanning() {
        if (isScanning) {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
            isScanning = false
        }
        bluetoothAdapter?.cancelDiscovery()
        if (_status.value == BluetoothCallStatus.SCANNING) {
            _status.value = BluetoothCallStatus.IDLE
        }
    }

    override fun connect(address: String, isVideo: Boolean) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        
        _status.value = BluetoothCallStatus.CONNECTING
        stopScanning()
        
        scope.launch(Dispatchers.IO) {
            bluetoothAdapter?.cancelDiscovery()
            try {
                val socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()
                withContext(Dispatchers.Main) {
                    manageConnectedSocket(socket)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Connection Failed: ${e.message}"
                    _status.value = BluetoothCallStatus.IDLE
                }
            }
        }
    }

    override fun startServer() {
        acceptJob?.cancel()
        acceptJob = scope.launch(Dispatchers.IO) {
            var serverSocket: BluetoothServerSocket? = null
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                Logger.i { "BT Server listening..." }
                while (isActive) {
                    val socket = serverSocket?.accept()
                    socket?.let {
                        withContext(Dispatchers.Main) {
                            manageConnectedSocket(it)
                        }
                    }
                }
            } catch (e: IOException) {
                Logger.e(e) { "BT Server Stopped" }
            } finally {
                serverSocket?.close()
            }
        }
    }

    override fun startAudio() {
        if (connectedThreads.isEmpty()) return
        if (isStreaming) return
        
        isStreaming = true
        _status.value = BluetoothCallStatus.AUDIO_STREAMING
        
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // High-Quality Audio Config
        val sampleRate = 44100 // Upgrade to 44.1kHz
        val minBufSizeRec = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat)
        val minBufSizeTrack = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat)

        try {
            audioRecord = AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat, minBufSizeRec * 4)
            audioTrack = AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat, minBufSizeTrack * 4, AudioTrack.MODE_STREAM)
            
            audioRecord?.startRecording()
            audioTrack?.play()
            
            // Dedicated Thread for ultra-low latency audio processing
            Thread {
                val buffer = ByteArray(minBufSizeRec)
                while (isStreaming) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val data = buffer.copyOfRange(0, read)
                        synchronized(connectedThreads) {
                            connectedThreads.values.forEach { it.write(data) }
                        }
                    }
                }
            }.start()
        } catch (e: Exception) {
            _errorMessage.value = "Audio Hardware Error"
            stopCall()
        }
    }

    override fun stopCall() {
        isStreaming = false
        connectJob?.cancel()
        acceptJob?.cancel()
        connectedThreads.values.forEach { it.cancel() }
        connectedThreads.clear()
        stopAudioHardware()
        _status.value = BluetoothCallStatus.IDLE
        startServer()
    }
    
    private fun stopAudioHardware() {
        try { 
            audioRecord?.stop(); audioRecord?.release()
            audioTrack?.stop(); audioTrack?.release()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {}
        audioRecord = null; audioTrack = null
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        val addr = socket.remoteDevice.address
        val thread = ConnectedThread(socket)
        connectedThreads[addr] = thread
        
        scope.launch(Dispatchers.IO) {
            thread.runLoop()
        }
        _status.value = BluetoothCallStatus.CONNECTED
        Logger.i { "Mesh Peer Connected: $addr. Total peers: ${connectedThreads.size}" }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private var handshakeVerified = false

        suspend fun runLoop() {
            try {
                mmOutStream.write("PIN:${_pairingCode.value}\n".toByteArray())
            } catch (e: IOException) { return }

            val buffer = ByteArray(4096)
            while (true) {
                try {
                    val bytes = mmInStream.read(buffer)
                    if (bytes <= 0) break
                    
                    if (!handshakeVerified) {
                         val msg = String(buffer, 0, bytes)
                         if (msg.startsWith("PIN:")) {
                             handshakeVerified = true
                         }
                         continue
                    }

                    if (isStreaming) {
                         // Directly write to track from current thread
                         audioTrack?.write(buffer, 0, bytes)
                    }
                } catch (e: IOException) {
                    Logger.w { "Mesh Peer Lost: ${mmSocket.remoteDevice.address}" }
                    break
                }
            }
            cancel()
        }

        fun write(bytes: ByteArray) {
            try { mmOutStream.write(bytes) } catch (e: IOException) {}
        }

        fun cancel() { 
            try { mmSocket.close() } catch (e: IOException) {}
        }
    }
}
