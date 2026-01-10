package com.tej.directo.discovery

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import co.touchlab.kermit.Logger
import com.tej.directo.p2p.core.discovery.ConnectionType
import com.tej.directo.p2p.core.discovery.DiscoveredPeer
import com.tej.directo.p2p.core.discovery.DiscoveryClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiDirectDiscoveryManager(
    private val context: Context
) : DiscoveryClient {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)
    
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discoveredPeers: Flow<List<DiscoveredPeer>> = _peers.asStateFlow()

    override suspend fun startDiscovery() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Logger.d { "WiFi Direct Discovery Started" }
            }
            override fun onFailure(reason: Int) {
                Logger.e { "WiFi Direct Discovery Failed: $reason" }
            }
        })
    }

    override suspend fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, null)
    }

    // This would be called by a BroadcastReceiver in a real app
    fun updatePeerList(deviceList: List<WifiP2pDevice>) {
        _peers.value = deviceList.map { device ->
            DiscoveredPeer(
                id = device.deviceAddress,
                name = device.deviceName,
                connectionType = ConnectionType.WIFI_DIRECT,
                metadata = mapOf("status" to device.status.toString())
            )
        }
    }
}
