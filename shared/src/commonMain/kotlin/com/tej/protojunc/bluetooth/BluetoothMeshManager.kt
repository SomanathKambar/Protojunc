package com.tej.protojunc.bluetooth

import com.tej.protojunc.p2p.core.orchestrator.TransportPriority
import com.tej.protojunc.core.models.SignalingMessage
import com.tej.protojunc.signaling.SignalingState
import com.tej.protojunc.signaling.SignalingClient
import com.tej.protojunc.signaling.mesh.MeshPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import co.touchlab.kermit.Logger
import com.benasher44.uuid.uuidFrom
import com.benasher44.uuid.uuid4
import com.juul.kable.*
import com.tej.protojunc.getCurrentTimeMillis

class BluetoothMeshManager(
    private val localId: String,
    private val scope: CoroutineScope,
    private val gattServer: GattServer,
    private val startAdvertising: suspend (String) -> Unit,
    private val stopAdvertising: suspend () -> Unit,
    private val peripheralBuilder: (String) -> Peripheral? = { null }
) : SignalingClient {

    private val SERVICE_UUID = "550e8400-e29b-41d4-a716-446655440000"
    private val WRITE_CHAR_UUID = "550e8400-e29b-41d4-a716-446655440001"
    private val NOTIFY_CHAR_UUID = "550e8400-e29b-41d4-a716-446655440002"

    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state: Flow<SignalingState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 100)
    override val messages: Flow<SignalingMessage> = _messages.asSharedFlow()

    private val seenPackets = mutableSetOf<String>()
    
    private val connectedPeers = mutableMapOf<String, Peripheral>()

    init {
        scope.launch {
            gattServer.receivedPackets.collect { data ->
                processIncomingPacket(data)
            }
        }
    }
    
    suspend fun connectToDevice(id: String): Boolean {
        return try {
            val peripheral = peripheralBuilder(id)
            if (peripheral != null) {
                Logger.i { "Manual connection initiated to $id" }
                // Connect and wait. 
                // Note: connectToPeripheral launches a job. We should probably wait here.
                // Refactoring connectToPeripheral to be suspendable for this specific call?
                // Or just use the peripheral directly here.
                
                // Existing logic caches connectedPeers.
                if (connectedPeers.containsKey(id)) return true
                
                peripheral.connect()
                connectedPeers[id] = peripheral
                
                // Subscribe to notifications
                val notifyChar = characteristicOf(SERVICE_UUID, NOTIFY_CHAR_UUID)
                scope.launch {
                    try {
                        // Ensure services are discovered before observing? 
                        // Kable usually handles this, but a race might occur or service is missing.
                        peripheral.observe(notifyChar).collect { data ->
                            processIncomingPacket(data)
                        }
                    } catch (e: Exception) {
                        Logger.e { "Failed to observe characteristic for $id: ${e.message}" }
                        // Optional: Disconnect if observation fails as it's critical for mesh
                        // connectedPeers.remove(id)
                        // peripheral.disconnect()
                    }
                }
                true
            } else {
                Logger.w { "Could not build peripheral for $id" }
                false
            }
        } catch (e: Exception) {
            Logger.e { "Failed to connect to $id: ${e.message}" }
            false
        }
    }

    override suspend fun connect() {
        _state.value = SignalingState.CONNECTING
        try {
            gattServer.startServer(SERVICE_UUID)
            startAdvertising(SERVICE_UUID)
            startScanning()
            _state.value = SignalingState.CONNECTED
        } catch (e: Exception) {
            Logger.e(e) { "Failed to start Mesh" }
            _state.value = SignalingState.ERROR
        }
    }

    override suspend fun disconnect() {
        stopAdvertising()
        gattServer.stopServer()
        connectedPeers.values.forEach { 
            try { it.disconnect() } catch (e: Exception) {} 
        }
        connectedPeers.clear()
        _state.value = SignalingState.DISCONNECTED
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        val payloadBytes = ProtoBuf.encodeToByteArray(message)
        val packet = MeshPacket(
            id = uuid4().toString(),
            senderId = localId,
            targetId = "BROADCAST",
            type = MeshPacket.Type.SIGNALING,
            payload = payloadBytes,
            timestamp = getCurrentTimeMillis()
        )
        
        broadcastPacket(packet)
    }

    private suspend fun broadcastPacket(packet: MeshPacket) {
        if (seenPackets.contains(packet.id)) return
        seenPackets.add(packet.id)

        val data = ProtoBuf.encodeToByteArray(packet)

        connectedPeers.values.forEach { peripheral ->
            scope.launch {
                try {
                    writeToPeripheral(peripheral, data)
                } catch (e: Exception) {
                    Logger.w { "Failed to write to peripheral: ${e.message}" }
                }
            }
        }

        gattServer.notifyClients(data)
    }

    private suspend fun writeToPeripheral(peripheral: Peripheral, data: ByteArray) {
        val char = characteristicOf(SERVICE_UUID, WRITE_CHAR_UUID)
        peripheral.write(char, data, WriteType.WithResponse)
    }

    private fun startScanning() {
        scope.launch {
            try {
                Scanner {
                    filters = listOf(Filter.Service(uuidFrom(SERVICE_UUID)))
                }.advertisements.collect { advertisement ->
                    val id = advertisement.name ?: "Unknown"
                    if (!connectedPeers.containsKey(id)) {
                        val peripheral = scope.peripheral(advertisement)
                        connectToPeripheral(peripheral, id)
                    }
                }
            } catch (e: Exception) {
                Logger.e { "Mesh Scanning Failed: ${e.message}" }
                // We could emit an error state here if we want the UI to know
                _state.value = SignalingState.ERROR
            }
        }
    }

    private suspend fun connectToPeripheral(peripheral: Peripheral, id: String) {
        try {
            peripheral.connect()
            connectedPeers[id] = peripheral
            
            val notifyChar = characteristicOf(SERVICE_UUID, NOTIFY_CHAR_UUID)
            peripheral.observe(notifyChar).collect { data ->
                processIncomingPacket(data)
            }
        } catch (e: Exception) {
            Logger.e { "Failed to connect to $id: ${e.message}" }
            connectedPeers.remove(id)
        }
    }

    private suspend fun processIncomingPacket(data: ByteArray) {
        try {
            val packet = ProtoBuf.decodeFromByteArray<MeshPacket>(data)
            
            if (seenPackets.contains(packet.id)) return
            seenPackets.add(packet.id)

            // Emit signaling messages to the flow
            if (packet.type == MeshPacket.Type.SIGNALING) {
                try {
                    val sigMsg = ProtoBuf.decodeFromByteArray<SignalingMessage>(packet.payload)
                    _messages.emit(sigMsg)
                } catch (e: Exception) {
                    Logger.e { "Failed to decode signaling message from packet" }
                }
            }

            if (packet.ttl > 0) {
                val newPacket = packet.copy(ttl = packet.ttl - 1, hopCount = packet.hopCount + 1)
                broadcastPacket(newPacket)
            }

        } catch (e: Exception) {
            Logger.e { "Failed to decode packet" }
        }
    }
}