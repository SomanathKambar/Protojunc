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
import com.tej.protojunc.discovery.PeerDiscovered
import com.tej.protojunc.core.models.SignalingMessage
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshCallScreen(
    coordinator: MeshCoordinator,
    nearbyPeers: List<NearbyPeer>,
    localId: String,
    isAdvertising: Boolean = false,
    onToggleAdvertising: () -> Unit = {},
    error: String? = null,
    onDismissError: () -> Unit = {},
    checkAndRequestBluetooth: (() -> Unit) -> Unit = {},
    onLeave: () -> Unit
) {
    val peers by coordinator.peers.collectAsState()
    val meshDiscoveredPeers by coordinator.discoveredPeers.collectAsState(emptyList())
    val pendingInvites by coordinator.pendingInvites.collectAsState()
    val chatMessages by coordinator.chatMessages.collectAsState()
    
    val scope = rememberCoroutineScope()
    var showAddPeerDialog by remember { mutableStateOf(false) }
    var selectedPeerForOptions by remember { mutableStateOf<PeerDiscovered?>(null) }
    var isChatOpen by remember { mutableStateOf(false) }
    var incomingCallFrom by remember { mutableStateOf<String?>(null) }

    // Observe incoming call signaling
    LaunchedEffect(Unit) {
        coordinator.signalingClient.messages.collect { msg ->
            if (msg.type == SignalingMessage.Type.VOICE_CALL || msg.type == SignalingMessage.Type.VIDEO_CALL) {
                if (msg.senderId != localId) {
                    incomingCallFrom = msg.senderId
                }
            }
        }
    }

    if (incomingCallFrom != null) {
        AlertDialog(
            onDismissRequest = { incomingCallFrom = null },
            title = { Text("Incoming Call") },
            text = { Text("Peer ${incomingCallFrom?.take(4)} is calling you...") },
            confirmButton = {
                Button(onClick = { 
                    scope.launch { coordinator.invitePeer(incomingCallFrom!!) }
                    incomingCallFrom = null 
                    isChatOpen = false // Go to call view
                }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { incomingCallFrom = null }) { Text("Decline") }
            }
        )
    }

    LaunchedEffect(Unit) {
        checkAndRequestBluetooth {
            // BLE scanning starts via App-level PresenceManager AND MeshCoordinator
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Bluetooth Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    checkAndRequestBluetooth { onDismissError() }
                }) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = onDismissError) { Text("Ignore") }
            }
        )
    }

    // Peer Options Dialog (Unified for both Discovered and Connected peers)
    if (selectedPeerForOptions != null) {
        val peerId = selectedPeerForOptions!!.id
        val isConnected = peers.containsKey(peerId)
        val isPending = pendingInvites.contains(peerId)

        AlertDialog(
            onDismissRequest = { selectedPeerForOptions = null },
            title = { Text(selectedPeerForOptions?.name ?: "Peer Options") },
            text = {
                Column {
                    if (!isConnected) {
                        Button(
                            onClick = { 
                                scope.launch { coordinator.invitePeer(peerId) }
                                selectedPeerForOptions = null 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPending
                        ) {
                            if (isPending) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                            else Text("Invite to Group")
                        }
                    } else {
                        Text("Connected to Peer", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                isChatOpen = true
                                selectedPeerForOptions = null 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Chat")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPeerForOptions = null }) { Text("Close") }
            }
        )
    }

    if (showAddPeerDialog) {
        AlertDialog(
            onDismissRequest = { showAddPeerDialog = false },
            title = { Text("Add Peer to Mesh") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("Mesh Ready Neighbors", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    
                    // Filter out already connected peers
                    val availableMeshPeers = meshDiscoveredPeers.filter { !peers.containsKey(it.id) }
                    
                    if (availableMeshPeers.isEmpty()) Text("No new mesh peers found", style = MaterialTheme.typography.bodySmall)
                    
                    availableMeshPeers.forEach { peer ->
                        val isPending = pendingInvites.contains(peer.id)
                        TextButton(
                            onClick = { 
                                scope.launch { coordinator.invitePeer(peer.id) }
                                showAddPeerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPending
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(peer.name)
                                if (isPending) {
                                    Spacer(Modifier.width(8.dp))
                                    CircularProgressIndicator(Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Paired Devices", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    val paired = coordinator.pairedDevices.filter { !peers.containsKey(it.id) }
                    if (paired.isEmpty()) Text("No new paired devices", style = MaterialTheme.typography.bodySmall)
                    
                    paired.forEach { device ->
                        val isPending = pendingInvites.contains(device.id)
                        TextButton(
                            onClick = { 
                                coordinator.connectToPairedDevice(device)
                                showAddPeerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPending
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(device.name)
                                if (isPending) {
                                    Spacer(Modifier.width(8.dp))
                                    CircularProgressIndicator(Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddPeerDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Mesh Group", style = MaterialTheme.typography.titleLarge)
                            Text("${peers.size} Connected", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddPeerDialog = true }) {
                            Icon(Icons.Default.PersonAdd, "Add")
                        }
                        IconButton(onClick = onLeave) {
                            Icon(Icons.Default.CallEnd, null, tint = Color.Red)
                        }
                    }
                )
                // Advertising Status Bar - Fixed Cropping
                Surface(color = if (isAdvertising) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(8.dp).background(if (isAdvertising) Color.Green else Color.Red, androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isAdvertising) "Discoverable" else "Hidden", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        Button(
                            onClick = onToggleAdvertising,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(if (isAdvertising) "STOP" else "START", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !isChatOpen,
                    onClick = { isChatOpen = false },
                    icon = { Icon(Icons.Default.Videocam, null) },
                    label = { Text("Calls") }
                )
                NavigationBarItem(
                    selected = isChatOpen,
                    onClick = { isChatOpen = true },
                    icon = { 
                        BadgedBox(badge = { if (chatMessages.isNotEmpty()) Badge { Text(chatMessages.size.toString()) } }) {
                            Icon(Icons.Default.Chat, null)
                        }
                    },
                    label = { Text("Chat") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (isChatOpen) {
                MeshChatView(
                    messages = chatMessages,
                    localId = localId,
                    onSendMessage = { coordinator.sendChatMessage(it) }
                )
            } else if (peers.isEmpty()) {
                // Empty state / Discovery
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    RadarDiscoveryUI(
                        nearbyPeers = meshDiscoveredPeers.map { NearbyPeer(it.id, it.name, 0L, 0, false) },
                        onPeerClick = { peer ->
                            selectedPeerForOptions = meshDiscoveredPeers.find { it.id == peer.deviceId }
                        },
                        modifier = Modifier.height(400.dp)
                    )
                    Text("Tap a node to invite", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
            } else {
                // Active Peer Grid
                Column(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(peers.toList()) { (peerId, manager) ->
                            val remoteTrack by manager.remoteVideoTrack.collectAsState()
                            val connectionState by manager.connectionState.collectAsState()

                            Card(
                                onClick = { 
                                    selectedPeerForOptions = PeerDiscovered(peerId, "Peer ${peerId.take(4)}", "", "", 0)
                                },
                                modifier = Modifier.aspectRatio(0.85f)
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    if (remoteTrack != null) {
                                        WebRtcVideoView(videoTrack = remoteTrack, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Text(connectionState.name, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text(
                                        "PEER ${peerId.take(4).uppercase()}",
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(0.5f)).padding(4.dp),
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
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
                            Button(onClick = { coordinator.startVoiceCall() }) { Icon(Icons.Default.Call, "Voice Call") }
                            Button(onClick = { /* Toggle Mic */ }) { Icon(Icons.Default.Mic, null) }
                            Button(onClick = { coordinator.startVideoCall() }) { Icon(Icons.Default.Videocam, "Video Call") }
                            Button(onClick = { /* Share File */ }) { Icon(Icons.Default.AttachFile, null) }
                        }
                    }
                }
            }
        }
    }
}
