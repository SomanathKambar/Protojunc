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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

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

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var acceptThread: AcceptThread? = null
    
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
        _devices.value = getPairedDevicesList()
    }
    
    private fun getPairedDevicesList(): List<BluetoothDeviceDomain> {
        return try {
            val paired = bluetoothAdapter?.bondedDevices
            paired?.map { 
                BluetoothDeviceDomain(it.name ?: "Unknown (Paired)", it.address) 
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
                     if (_status.value == BluetoothCallStatus.CONNECTED || _status.value == BluetoothCallStatus.AUDIO_STREAMING) {
                         _errorMessage.value = "Device Disconnected"
                         stopCall()
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
                         // Relaxed filtering for now to ensure visibility, relies on manual PIN check
                         val newDevice = BluetoothDeviceDomain(device.name ?: "Unknown Device", device.address)
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
            filter.addAction(BluetoothDevice.ACTION_UUID)
            context.registerReceiver(receiver, filter)
            isScanning = true
        }
        _devices.value = getPairedDevicesList()
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
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
             try { device.createBond() } catch (e: Exception) {}
        }
        _status.value = BluetoothCallStatus.CONNECTING
        stopScanning()
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    override fun startServer() {
        acceptThread?.cancel()
        acceptThread = AcceptThread()
        acceptThread?.start()
    }

    override fun startAudio() {
        if (_status.value != BluetoothCallStatus.CONNECTED) return
        
        connectedThread?.let { thread ->
            if (isStreaming) return
            isStreaming = true
            _status.value = BluetoothCallStatus.AUDIO_STREAMING
            
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
            
            val minBufSizeRec = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
            val minBufSizeTrack = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

            try {
                audioRecord = AudioRecord(
                    android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION, 
                    sampleRate, channelConfigIn, audioFormat, minBufSizeRec * 2
                )
                
                audioTrack = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    sampleRate, channelConfigOut, audioFormat, minBufSizeTrack * 2, AudioTrack.MODE_STREAM
                )
                
                audioRecord?.startRecording()
                audioTrack?.play()
                
                scope.launch(Dispatchers.IO) {
                    val buffer = ByteArray(minBufSizeRec) // Smaller chunks for lower latency
                    while (isStreaming) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            thread.write(buffer.copyOfRange(0, read))
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Audio Error: ${e.message}"
                stopCall()
            }
        }
    }

    override fun stopCall() {
        isStreaming = false
        connectThread?.cancel()
        acceptThread?.cancel()
        connectedThread?.cancel()
        connectThread = null
        acceptThread = null
        connectedThread = null
        stopAudioHardware()
        _status.value = BluetoothCallStatus.IDLE
        startServer()
    }
    
    private fun stopAudioHardware() {
        try { audioRecord?.stop(); audioRecord?.release()
            audioTrack?.stop(); audioTrack?.release()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {}
        audioRecord = null; audioTrack = null
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
        _status.value = BluetoothCallStatus.CONNECTED
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(APP_UUID)
        }
        override fun run() {
            bluetoothAdapter?.cancelDiscovery()
            try {
                mmSocket?.connect()
                mmSocket?.let { manageConnectedSocket(it) }
            } catch (e: IOException) {
                _errorMessage.value = "Connection Failed: ${e.message}"
                _status.value = BluetoothCallStatus.IDLE
                cancel()
            }
        }
        fun cancel() { try { mmSocket?.close() } catch (e: IOException) {} }
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
        }
        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try { mmServerSocket?.accept() } catch (e: IOException) { shouldLoop = false; null }
                socket?.let {
                    manageConnectedSocket(it)
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }
        fun cancel() { try { mmServerSocket?.close() } catch (e: IOException) {} }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val dataOut = DataOutputStream(mmOutStream)
        private val dataIn = DataInputStream(mmInStream)
        
        private var handshakeVerified = false

        override fun run() {
            try {
                mmOutStream.write("PIN:${_pairingCode.value}\n".toByteArray())
            } catch (e: IOException) {
                return
            }

            val buffer = ByteArray(4096)
            
            while (true) {
                try {
                    if (mmInStream.available() > 10000) {
                         mmInStream.skip(mmInStream.available().toLong())
                    }

                    if (!handshakeVerified) {
                         val bytes = mmInStream.read(buffer)
                         val msg = String(buffer, 0, bytes)
                         if (msg.startsWith("PIN:")) {
                             val receivedPin = msg.substring(4).trim()
                             if (receivedPin.startsWith(_pairingCode.value)) {
                                 handshakeVerified = true
                             }
                         }
                         continue
                    }

                    val bytes = mmInStream.read(buffer)
                    if (isStreaming && bytes > 0) {
                         audioTrack?.write(buffer, 0, bytes)
                    }
                    
                } catch (e: IOException) {
                    _status.value = BluetoothCallStatus.ERROR
                    _errorMessage.value = "Connection Lost"
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {}
        }

        fun cancel() { try { mmSocket.close() } catch (e: IOException) {} }
    }
}