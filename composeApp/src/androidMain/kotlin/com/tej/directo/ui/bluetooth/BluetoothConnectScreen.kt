package com.tej.directo.ui.bluetooth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tej.directo.discovery.AndroidPeripheralAdvertiser
import com.tej.directo.discovery.KableDiscoveryManager
import com.tej.directo.discovery.PeerDiscovered
import com.tej.directo.discovery.DiscoveryManager
import android.content.Context
import android.bluetooth.BluetoothManager
import kotlinx.coroutines.launch

@Composable
fun InviteBleScreen(
    onAnswerReceived: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val advertiser = remember {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        AndroidPeripheralAdvertiser(context, manager.adapter)
    }
    val discoveryManager = remember { KableDiscoveryManager(advertiser) }

    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!manager.adapter.isEnabled) {
            error = "Bluetooth is disabled. Please enable it."
            return@LaunchedEffect
        }
        try {
            discoveryManager.startAdvertising("SDP_OFFER_PENDING")
            
            discoveryManager.observeMessages().collect { answer ->
                onAnswerReceived(answer)
            }
        } catch (e: Exception) {
            error = e.message ?: "Bluetooth error"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (error != null) {
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(24.dp))
            Text("Broadcasting via Bluetooth...", style = MaterialTheme.typography.titleMedium)
            Text("Nearby partners can now find you.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = {
                scope.launch { discoveryManager.stopDiscovery() }
                onBack()
            }) { Text("Stop & Back") }
        }
    }
}

@Composable
fun JoinBleScreen(
    discoveryManager: DiscoveryManager,
    onPeerSelected: (PeerDiscovered) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var peers by remember { mutableStateOf(setOf<PeerDiscovered>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!manager.adapter.isEnabled) {
            error = "Bluetooth is disabled. Please enable it."
            return@LaunchedEffect
        }
        try {
            discoveryManager.observeNearbyPeers().collect { peer ->
                peers = peers + peer
            }
        } catch (e: Exception) {
            error = e.message ?: "Discovery failed. Check if Bluetooth is On."
        }
    }

    LaunchedEffect(peers) {
        // Auto-select if only one Directo partner is found after 2 seconds
        if (peers.size == 1 && !isConnecting) {
            kotlinx.coroutines.delay(2000)
            if (peers.size == 1 && !isConnecting) {
                isConnecting = true
                onPeerSelected(peers.first())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Join Partner", style = MaterialTheme.typography.headlineSmall)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(contentAlignment = Alignment.Center) {
            RadarScanView()
            if (peers.isEmpty()) {
                Text("Searching...", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(peers.toList()) { peer ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { onPeerSelected(peer) }
                ) {
                    ListItem(
                        headlineContent = { Text(peer.name) },
                        supportingContent = { Text("Signal: ${peer.rssi} dBm") },
                        trailingContent = { Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, null) }
                    )
                }
            }
        }
        
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Cancel") }
    }
}
