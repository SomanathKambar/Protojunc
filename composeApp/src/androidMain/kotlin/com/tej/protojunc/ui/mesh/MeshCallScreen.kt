package com.tej.protojunc.ui.mesh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tej.protojunc.p2p.core.orchestrator.MeshCoordinator
import com.tej.protojunc.ui.call.WebRtcVideoView
import com.tej.protojunc.ui.theme.ProtojuncCard
import com.tej.protojunc.ui.theme.RadarDiscoveryUI
import com.tej.protojunc.ui.ConnectionViewModel
import com.tej.protojunc.models.NearbyPeer
import kotlinx.coroutines.launch

@Composable
fun MeshCallScreen(
    coordinator: MeshCoordinator,
    nearbyPeers: List<NearbyPeer>,
    checkAndRequestBluetooth: (() -> Unit) -> Unit = {},
    onLeave: () -> Unit
) {
    val peers by coordinator.peers.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        checkAndRequestBluetooth {
            // BLE scanning starts via App-level PresenceManager
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mesh Group", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("${peers.size} Connected Peers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onLeave) {
                Icon(Icons.Default.CallEnd, null, tint = MaterialTheme.colorScheme.error)
            }
        }

        if (peers.isEmpty()) {
            // DISCOVERY MODE: Show Radar
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("1. TAP PARTNERS TO FORM GROUP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    RadarDiscoveryUI(
                        nearbyPeers = nearbyPeers,
                        onPeerClick = { peer ->
                            scope.launch { 
                                // Explicit Invitation Logic
                                coordinator.invitePeer(peer.deviceId) 
                            }
                        },
                        modifier = Modifier.height(400.dp)
                    )
                    
                    if (nearbyPeers.isEmpty()) {
                        Text("Looking for nearby mesh nodes...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        Text("${nearbyPeers.size} partners found nearby", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        } else {
            // ACTIVE MESH: Show Grid with more detail
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(peers.toList()) { (peerId, manager) ->
                    val remoteTrack by manager.remoteVideoTrack.collectAsState()
                    val connectionState by manager.connectionState.collectAsState()

                    ProtojuncCard(modifier = Modifier.aspectRatio(0.85f)) {
                        Box(Modifier.fillMaxSize()) {
                            if (remoteTrack != null) {
                                WebRtcVideoView(videoTrack = remoteTrack, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        Text(connectionState.name, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            
                            // Peer Info Overlay
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("PEER: ${peerId.take(8).uppercase()}", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Handshake: ${connectionState.name}", style = TextStyle(fontSize = 9.sp), color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { /* Toggle Mic */ }) { Icon(Icons.Default.Mic, null) }
                Button(onClick = { /* Toggle Video */ }) { Icon(Icons.Default.Videocam, null) }
                Button(onClick = { /* Share File */ }) { Icon(Icons.Default.AttachFile, null) }
            }
        }
    }
}