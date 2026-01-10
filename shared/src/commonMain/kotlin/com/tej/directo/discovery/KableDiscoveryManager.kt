package com.tej.directo.discovery

import com.benasher44.uuid.uuidFrom
import com.juul.kable.*
import co.touchlab.kermit.Logger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface PeripheralAdvertiser {
    suspend fun startAdvertising(roomCode: String, serviceUuid: String, sdpPayload: String)
    fun stopAdvertising()
    fun observeReceivedMessages(): Flow<String>
}

class KableDiscoveryManager(private val advertiser: PeripheralAdvertiser) : DiscoveryManager {
    private val SERVICE_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440000")
    private val SDP_CHARACTERISTIC_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440001")
    
    // Cache discovered advertisements to allow connection later
    private val discoveredAdvertisements = mutableMapOf<String, Advertisement>()

    override suspend fun startAdvertising(roomCode: String, payload: String) {
        //Kable, you define a Peripheral to act as a Server
        // We use the 'advertiser' to start broadcasting our presence
       advertiser.startAdvertising(roomCode, "550e8400-e29b-41d4-a716-446655440000", payload)
    }

    override fun observeMessages(): Flow<String> = advertiser.observeReceivedMessages()

    override fun observeNearbyPeers(): Flow<PeerDiscovered> =
        Scanner {
            filters = listOf(Filter.Service(SERVICE_UUID))
        }.advertisements.map { advertisement ->
            val id = advertisement.name ?: "Unknown-${advertisement.hashCode()}"
            discoveredAdvertisements[id] = advertisement

            // Extract room code from service data if available
            val room = try {
                advertisement.serviceData(SERVICE_UUID)?.decodeToString() ?: ""
            } catch (e: Exception) { "" }

            PeerDiscovered(
                id = id,
                name = advertisement.name ?: "Unknown Peer",
                roomCode = room,
                remoteSdpBase64 = "", // We will connect to this peer to read the full SDP
                rssi = advertisement.rssi
            )
        }

    override suspend fun connectToPeer(peer: PeerDiscovered): String = coroutineScope {
        val advertisement = discoveredAdvertisements[peer.id] 
            ?: throw IllegalStateException("Advertisement not found for peer: ${peer.name}")

        val peripheral = peripheral(advertisement)
        
        try {
            // Safety timeout for GATT operations
            kotlinx.coroutines.withTimeout(12000) {
                peripheral.connect()
                
                // MTU negotiation is critical for reading full SDP strings in one go
                // Note: Kable 0.32.0 commonMain Peripheral does not expose requestMtu.
                // It must be handled in platform-specific layers if needed, 
                // but we rely on minification to fit.

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

    override suspend fun writeToPeer(peer: PeerDiscovered, data: String) = coroutineScope {
        val advertisement = discoveredAdvertisements[peer.id] 
            ?: throw IllegalStateException("Advertisement not found for peer: ${peer.name}")

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

    override fun generateQrData(payload: String): String = payload

    override suspend fun stopDiscovery() {
        // Logic to cancel advertiser scope
    }
}
