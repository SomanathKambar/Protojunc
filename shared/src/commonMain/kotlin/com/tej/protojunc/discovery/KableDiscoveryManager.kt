package com.tej.protojunc.discovery

import com.benasher44.uuid.uuidFrom
import com.juul.kable.*
import co.touchlab.kermit.Logger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.tej.protojunc.p2p.core.discovery.DiscoveryClient
import com.tej.protojunc.p2p.core.discovery.DiscoveredPeer
import com.tej.protojunc.p2p.core.discovery.ConnectionType

interface PeripheralAdvertiser {
    suspend fun startAdvertising(roomCode: String, serviceUuid: String, sdpPayload: String)
    fun stopAdvertising()
    fun observeReceivedMessages(): Flow<String>
}

class KableDiscoveryManager(private val advertiser: PeripheralAdvertiser) : DiscoveryManager, DiscoveryClient {
    private val SERVICE_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440000")
    private val SDP_CHARACTERISTIC_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440001")
    
    private val discoveredAdvertisements = mutableMapOf<String, Advertisement>()

    override val discoveredPeers: Flow<List<DiscoveredPeer>> = observeNearbyPeers().map { peer ->
        listOf(DiscoveredPeer(peer.id, peer.name, ConnectionType.BLE, mapOf("roomCode" to peer.roomCode)))
    }

    override suspend fun startDiscovery() {
        // Handled by observeNearbyPeers
    }

    override suspend fun stopDiscovery() {
        // Handled by advertiser
    }

    override suspend fun startAdvertising(roomCode: String, payload: String) {
       advertiser.startAdvertising(roomCode, SERVICE_UUID.toString(), payload)
    }

    override fun observeMessages(): Flow<String> = advertiser.observeReceivedMessages()

    override fun observeNearbyPeers(): Flow<PeerDiscovered> =
        Scanner {
            filters = listOf(Filter.Service(SERVICE_UUID))
        }.advertisements.map { advertisement ->
            val id = advertisement.name ?: "Unknown-${advertisement.hashCode()}"
            discoveredAdvertisements[id] = advertisement

            val room = try {
                advertisement.serviceData(SERVICE_UUID)?.decodeToString() ?: ""
            } catch (e: Exception) { "" }

            PeerDiscovered(
                id = id,
                name = advertisement.name ?: "Unknown Peer",
                roomCode = room,
                remoteSdpBase64 = "",
                rssi = advertisement.rssi
            )
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