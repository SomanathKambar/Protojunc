package com.tej.protojunc.discovery

import com.benasher44.uuid.uuidFrom
import com.juul.kable.*
import co.touchlab.kermit.Logger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.tej.protojunc.p2p.core.discovery.DiscoveryClient
import com.tej.protojunc.p2p.core.discovery.DiscoveredPeer
import com.tej.protojunc.p2p.core.discovery.ConnectionType

interface PeripheralAdvertiser {
    suspend fun startAdvertising(roomCode: String, serviceUuid: String, sdpPayload: String)
    fun stopAdvertising()
    fun observeReceivedMessages(): Flow<String>
    // New: Expose advertising state for UI feedback
    val advertisingState: Flow<Boolean>
}

class KableDiscoveryManager(private val advertiser: PeripheralAdvertiser) : DiscoveryManager, DiscoveryClient {
    private val SERVICE_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440000")
    private val SDP_CHARACTERISTIC_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440001")
    
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discoveredPeers: Flow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()
    
    // For DiscoveryManager compatibility
    override val meshDiscoveredPeers: Flow<List<PeerDiscovered>> = _discoveredPeers.map { list ->
        list.map { 
            PeerDiscovered(it.id, it.name, it.metadata["roomCode"] ?: "", "", 0)
        }
    }
    
    private val discoveredAdvertisements = mutableMapOf<String, Advertisement>()
    private var scanJob: kotlinx.coroutines.Job? = null
    
    override suspend fun startDiscovery() = coroutineScope {
        scanJob?.cancel()
        scanJob = launch {
            observeNearbyPeers().collect { (id, name, roomCode, _, _) ->
                val current = _discoveredPeers.value.toMutableList()
                val index = current.indexOfFirst { it.id == id }
                val newPeer = DiscoveredPeer(id, name, ConnectionType.MESH, mapOf("roomCode" to roomCode))
                
                if (index >= 0) {
                    current[index] = newPeer
                } else {
                    current.add(newPeer)
                }
                _discoveredPeers.value = current
            }
        }
    }

    override suspend fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
    }

    override suspend fun startAdvertising(roomCode: String, payload: String) {
       advertiser.startAdvertising(roomCode, SERVICE_UUID.toString(), payload)
    }

    override fun observeMessages(): Flow<String> = advertiser.observeReceivedMessages()

    override fun observeNearbyPeers(): Flow<PeerDiscovered> =
        Scanner {
            filters = listOf(Filter.Service(SERVICE_UUID))
        }.advertisements.map { advertisement ->
            val id = advertisement.stableId()
            discoveredAdvertisements[id] = advertisement

            val room = try {
                advertisement.serviceData(SERVICE_UUID)?.decodeToString() ?: ""
            } catch (e: Exception) { "" }
            
            val name = advertisement.name ?: if (room.isNotEmpty()) "Peer-$room" else "Unknown Peer"

            PeerDiscovered(
                id = id,
                name = name,
                roomCode = room,
                remoteSdpBase64 = "",
                rssi = advertisement.rssi
            )
        }

    override suspend fun maintainConnection(peerId: String) = coroutineScope {
        val advertisement = discoveredAdvertisements[peerId] 
        if (advertisement != null) {
            Logger.i { "maintainConnection called for $peerId. Assuming MeshManager handles it via ID." }
        }
    }

    override suspend fun connectToPeer(peer: PeerDiscovered): String = coroutineScope {
        val advertisement = discoveredAdvertisements[peer.id] 
            ?: throw IllegalStateException("Advertisement not found for peer: ${peer.name}")

        val peripheral = peripheral(advertisement)
        
        try {
            withTimeout(12000) {
                peripheral.connect()
                
                val characteristic = characteristicOf(
                    service = SERVICE_UUID.toString(),
                    characteristic = SDP_CHARACTERISTIC_UUID.toString()
                )
                
                val data = peripheral.read(characteristic)
                val result = data.decodeToString()
                
                if (result.isEmpty()) {
                    throw IllegalStateException("Received empty data from peer")
                }
                
                return@withTimeout result
            }
        } finally {
            peripheral.disconnect()
        }
    }

    override suspend fun writeToPeer(peer: PeerDiscovered, data: String) {
        val advertisement = discoveredAdvertisements[peer.id] 
            ?: throw IllegalStateException("Advertisement not found for peer: ${peer.name}")

        coroutineScope {
            val peripheral = peripheral(advertisement)
            try {
                peripheral.connect()
                val characteristic = characteristicOf(
                    service = SERVICE_UUID.toString(),
                    characteristic = SDP_CHARACTERISTIC_UUID.toString()
                )
                peripheral.write(characteristic, data.encodeToByteArray(), WriteType.WithResponse)
            } finally {
                peripheral.disconnect()
            }
        }
    }

    override fun generateQrData(payload: String): String = payload
}
