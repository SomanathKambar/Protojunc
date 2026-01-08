package com.tej.directo.discovery

import com.benasher44.uuid.uuidFrom
import com.juul.kable.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface PeripheralAdvertiser {
    suspend fun startAdvertising(serviceUuid: String, sdpPayload: String)
    fun stopAdvertising()
}

class KableDiscoveryManager(private val advertiser: PeripheralAdvertiser) : DiscoveryManager {
    private val SERVICE_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440000")
    private val SDP_CHARACTERISTIC_UUID = uuidFrom("550e8400-e29b-41d4-a716-446655440001")

    override suspend fun startAdvertising(payload: String) {
        //Kable, you define a Peripheral to act as a Server
        // We use the 'advertiser' to start broadcasting our presence
       advertiser.startAdvertising("550e8400-e29b-41d4-a716-446655440000", payload)
    }

    override fun observeNearbyPeers(): Flow<PeerDiscovered> =
        Scanner {
            filters = listOf(Filter.Service(SERVICE_UUID))
        }.advertisements.map { advertisement ->
            // Here 'advertisement' is provided by the library after discovery
            PeerDiscovered(
                name = advertisement.name ?: "Unknown Peer",
                remoteSdpBase64 = "", // We will connect to this peer to read the full SDP
                rssi = advertisement.rssi
            )
        }

    override fun generateQrData(payload: String): String = payload

    override suspend fun stopDiscovery() {
        // Logic to cancel advertiser scope
    }
}
