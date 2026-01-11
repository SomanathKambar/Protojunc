package com.tej.protojunc.ui.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tej.protojunc.discovery.AndroidPeripheralAdvertiser
import com.tej.protojunc.discovery.DiscoveryManager
import com.tej.protojunc.discovery.KableDiscoveryManager
import com.tej.protojunc.discovery.PeerDiscovered
import kotlinx.coroutines.launch

import com.tej.protojunc.ui.theme.RadarDiscoveryUI
import com.tej.protojunc.models.NearbyPeer
import androidx.compose.ui.graphics.Color

@Composable
fun InviteBleScreen(
    localOfferSdp: String?,
    roomCode: String,
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

    LaunchedEffect(localOfferSdp) {
        if (localOfferSdp == null) return@LaunchedEffect
        
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!manager.adapter.isEnabled) {
            error = "Bluetooth is disabled. Please enable it."
            return@LaunchedEffect
        }
        try {
            discoveryManager.startAdvertising(roomCode, localOfferSdp)
            
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
        } else if (localOfferSdp == null) {
            CircularProgressIndicator()
            Text("Preparing Offer...")
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(24.dp))
            Text("Room: ${if (roomCode.isEmpty()) "Open" else roomCode}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
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
    roomCode: String,
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
        // Auto-select logic based on Room Code
        if (peers.isNotEmpty() && !isConnecting) {
            val matchingPeer = if (roomCode.isNotEmpty()) {
                // Strictly match the Room Code found in BLE Service Data
                peers.firstOrNull { it.roomCode.equals(roomCode, ignoreCase = true) } 
            } else {
                // If Open, only auto connect if there's exactly one peer nearby
                if (peers.size == 1) peers.first() else null
            }

            matchingPeer?.let {
                kotlinx.coroutines.delay(500) // Small delay for UX
                if (!isConnecting) {
                    isConnecting = true
                    onPeerSelected(it)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Join Partner", style = MaterialTheme.typography.headlineSmall)
        if (roomCode.isNotEmpty()) {
            Text("Searching for Room: $roomCode", color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        RadarDiscoveryUI(
            nearbyPeers = peers.map { 
                NearbyPeer(it.id, it.name, System.currentTimeMillis(), it.rssi) 
            },
            onPeerClick = { nearby ->
                val peer = peers.firstOrNull { it.id == nearby.deviceId }
                peer?.let {
                    if (!isConnecting) {
                        isConnecting = true
                        onPeerSelected(it)
                    }
                }
            }
        )

        if (peers.isEmpty()) {
            Text("Searching for signals...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(peers.toList()) { peer ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { 
                        if (!isConnecting) {
                            isConnecting = true
                            onPeerSelected(peer)
                        }
                    }
                ) {
                    ListItem(
                        headlineContent = { Text(peer.name) },
                        supportingContent = { Text("Signal: ${peer.rssi} dBm") },
                        trailingContent = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward, null) }
                    )
                }
            }
        }
        
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Cancel") }
    }
}
