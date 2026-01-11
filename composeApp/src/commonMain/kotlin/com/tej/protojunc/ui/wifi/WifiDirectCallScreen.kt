package com.tej.protojunc.ui.wifi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tej.protojunc.bluetooth.WifiDirectCallManager
import com.tej.protojunc.bluetooth.WifiP2pDeviceDomain

import com.tej.protojunc.ui.theme.RadarDiscoveryUI
import com.tej.protojunc.models.NearbyPeer
import com.tej.protojunc.ui.theme.ProtojuncCard
import com.tej.protojunc.ui.theme.ProtojuncButton
import androidx.compose.ui.graphics.Color

@Composable
fun WifiDirectCallScreen(
    manager: WifiDirectCallManager,
    onBack: () -> Unit
) {
    val devices by manager.devices.collectAsState()
    val status by manager.status.collectAsState()
    val isGroupOwner by manager.isGroupOwner.collectAsState()
    val info by manager.connectionInfo.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Wi-Fi Direct Link", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ProtojuncCard(modifier = Modifier.fillMaxWidth()) {
             Column(modifier = Modifier.padding(16.dp)) {
                 Text("Status: $status", style = MaterialTheme.typography.titleMedium)
                 if (info.isNotEmpty()) {
                     Text("Info: $info", style = MaterialTheme.typography.bodyMedium)
                     Text("Role: ${if (isGroupOwner) "Host (Group Owner)" else "Client"}", style = MaterialTheme.typography.bodySmall)
                 }
             }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // RADAR SECTION
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            RadarDiscoveryUI(
                nearbyPeers = devices.map { 
                    NearbyPeer(it.address, it.name, System.currentTimeMillis(), -50, isPaired = it.status == "Connected") 
                },
                onPeerClick = { nearby ->
                    val device = devices.firstOrNull { it.address == nearby.deviceId }
                    device?.let { manager.connect(it) }
                }
            )
            
            if (devices.isEmpty() && status == "DISCOVERING") {
                Text("Searching for partners...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtojuncButton(onClick = { manager.discoverPeers() }, modifier = Modifier.weight(1f)) {
                Text("Scan")
            }
            ProtojuncButton(onClick = { manager.stopDiscovery() }, modifier = Modifier.weight(1f)) {
                Text("Stop")
            }
        }
        
        if (status == "CONNECTED" || status == "Connected") {
             Spacer(modifier = Modifier.height(8.dp))
             Button(onClick = { manager.disconnect() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                 Text("Disconnect Group")
             }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onBack) {
            Text("Back", color = MaterialTheme.colorScheme.secondary)
        }
    }
}
