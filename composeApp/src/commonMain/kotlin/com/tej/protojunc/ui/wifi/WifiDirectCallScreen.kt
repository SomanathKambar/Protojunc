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
        Text("Wi-Fi Direct Call", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
             Column(modifier = Modifier.padding(16.dp)) {
                 Text("Status: $status", style = MaterialTheme.typography.titleMedium)
                 if (info.isNotEmpty()) {
                     Text("Info: $info", style = MaterialTheme.typography.bodyMedium)
                     Text("Role: ${if (isGroupOwner) "Host (Group Owner)" else "Client"}", style = MaterialTheme.typography.bodySmall)
                 }
             }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { manager.discoverPeers() }) {
                Text("Discover Peers")
            }
            Button(onClick = { manager.stopDiscovery() }) {
                Text("Stop Discovery")
            }
        }
        
        if (status == "CONNECTED") {
             Spacer(modifier = Modifier.height(8.dp))
             Button(onClick = { manager.disconnect() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                 Text("Disconnect Group")
             }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Available Peers:")
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { manager.connect(device) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text("${device.address} - ${device.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
