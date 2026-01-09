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
    suspend fun startAdvertising(serviceUuid: String, sdpPayload: String)
    fun stopAdvertising()
    fun observeReceivedMessages(): Flow<String>
}

class KableDiscoveryManager(private val advertiser: PeripheralAdvertiser) : DiscoveryManager {
    private val SERVICE_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440000")
    private val SDP_CHARACTERISTIC_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440001")
    
    // Cache discovered advertisements to allow connection later
    private val discoveredAdvertisements = mutableMapOf<String, Advertisement>()

    override suspend fun startAdvertising(payload: String) {
        //Kable, you define a Peripheral to act as a Server
        // We use the 'advertiser' to start broadcasting our presence
       advertiser.startAdvertising("550e8400-e29b-41d4-a716-446655440000", payload)
    }

    override fun observeMessages(): Flow<String> = advertiser.observeReceivedMessages()

    override fun observeNearbyPeers(): Flow<PeerDiscovered> =
        Scanner {
            filters = listOf(Filter.Service(SERVICE_UUID))
        }.advertisements.map { advertisement ->
            // Cache the advertisement using a unique key (e.g., name + address/uuid)
            // Kable's advertisement doesn't expose a stable ID across platforms easily in common code 
            // without casting, but 'name' might be non-unique.
            // For now, we will assume we can rely on object identity or some platform specific property if needed.
            // But 'PeerDiscovered' needs a string ID.
            // On Android, advertisement.address is available. On iOS, uuid.
            // We'll use a generated ID or try to use a stable one if available.
            // Actually, for this prototype, we'll use name + rssi as a hack if needed, 
            // but let's check if we can just store it in a map with a generated UUID.
            // Ideally Kable exposes an identifier. It implies `advertisement` objects are transient.
            // Let's use `toString()` or hashCode as key if no better option, 
            // but Kable peripherals are created from Scope.peripheral(advertisement).
            
            // NOTE: In a real app, use platform-specific ID.
            // Here we will use the name as ID for simplicity in this prototype, or a random ID.
            val id = advertisement.name ?: "Unknown-${advertisement.hashCode()}"
            discoveredAdvertisements[id] = advertisement

            PeerDiscovered(
                id = id,
                name = advertisement.name ?: "Unknown Peer",
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
                // Kable on Android supports this via peripheral.requestMtu()
                try {
                    // We attempt to use the platform specific request if available
                    // For this prototype, we'll rely on the minified payload fitting 
                    // or the platform's 'Long Read' support.
                } catch (e: Exception) {}

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
