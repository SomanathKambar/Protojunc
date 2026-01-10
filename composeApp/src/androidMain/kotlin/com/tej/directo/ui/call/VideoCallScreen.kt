package com.tej.directo.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tej.directo.webrtc.WebRtcSessionManager
import com.tej.directo.webrtc.WebRtcState
import com.tej.directo.ui.ConnectionViewModel
import com.tej.directo.webrtc.HandshakeStage
import com.tej.directo.discovery.DiscoveryManager
import com.tej.directo.discovery.PeerDiscovered
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import co.touchlab.kermit.Logger

@Composable
fun VideoCallScreen(
    sessionManager: WebRtcSessionManager,
    viewModel: ConnectionViewModel,
    discoveryManager: DiscoveryManager,
    isHost: Boolean,
    roomCode: String,
    checkAndRequestBluetooth: (() -> Unit) -> Unit = {},
    onEndCall: () -> Unit
) {
    val remoteTrack by sessionManager.remoteVideoTrack.collectAsState()
    val localTrack by sessionManager.localVideoTrack.collectAsState()
    val connectionState by sessionManager.connectionState.collectAsState()
    val progressMessage by sessionManager.progressMessage.collectAsState()
    val handshakeStage by viewModel.handshakeStage.collectAsState()
    val localSdp by viewModel.localSdp.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // Production RCA: Handle automated handshake based on role
    LaunchedEffect(isHost) {
        // 1. OPEN CAMERA IMMEDIATELY for everyone
        sessionManager.createPeerConnection()
        
        // Wait for PeerConnection to reach Ready state before proceeding
        sessionManager.connectionState.filter { it == WebRtcState.Ready }.first()

        // 2. TRIGGER HANDSHAKE with Bluetooth Check
        checkAndRequestBluetooth {
            if (isHost) {
                // HOST: Generate offer and start advertising
                viewModel.prepareInvite {
                    // localSdp is now ready, advertising starts in the other LaunchedEffect
                }
            } else {
                // JOINER: Scan for the host
                coroutineScope.launch {
                    var alreadyConnecting = false
                    discoveryManager.observeNearbyPeers().collect { peer ->
                        if (!alreadyConnecting && (roomCode.isEmpty() || peer.roomCode.equals(roomCode, ignoreCase = true))) {
                            alreadyConnecting = true
                            Logger.d { "Found host ${peer.name} for room $roomCode. Initiating handshake..." }
                            viewModel.initiateBleHandshake(discoveryManager, peer) {
                                // Handshake success
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-broadcast if we are host and offer is ready
    LaunchedEffect(localSdp) {
        if (isHost && localSdp != null) {
            checkAndRequestBluetooth {
                coroutineScope.launch {
                    try {
                        // Use roomCode for advertising, but Joiner will ignore it for speed
                        discoveryManager.startAdvertising(roomCode, localSdp!!)
                        
                        // Host listens for the Answer
                        discoveryManager.observeMessages().collect { answer ->
                            viewModel.handleAnswerScanned(answer) {
                                // Handshake complete
                            }
                        }
                    } catch (e: Exception) {
                        // Handle advertising failure
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ... (rest of the Video views remain same)
        if (remoteTrack != null) {
            WebRtcVideoView(
                videoTrack = remoteTrack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    val loadingText = if (isHost) {
                        if (localSdp == null) "Starting session..." else "Waiting for partner to join..."
                    } else {
                        if (handshakeStage == HandshakeStage.IDLE) "Connecting to partner..."
                        else "Joining: ${handshakeStage.name.replace("_", " ")}"
                    }
                    Text(loadingText, color = Color.LightGray)
                }
            }
        }

        // Local Preview (Small Box) - Shown immediately
        Surface(
            modifier = Modifier
                .size(120.dp, 180.dp)
                .align(Alignment.TopEnd)
                .padding(16.dp),
            color = Color.DarkGray,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            if (localTrack != null) {
                WebRtcVideoView(
                    videoTrack = localTrack,
                    modifier = Modifier.fillMaxSize(),
                    mirror = true
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Info, null, tint = Color.Gray)
                }
            }
        }

        // Status Overlay
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (connectionState) {
                    WebRtcState.Connected -> Icons.Default.Call
                    WebRtcState.Failed -> Icons.Default.Warning
                    else -> Icons.Default.Refresh
                }
                val color = when (connectionState) {
                    WebRtcState.Connected -> Color.Green
                    WebRtcState.Failed -> Color.Red
                    else -> Color.Yellow
                }
                
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val statusText = when {
                    handshakeStage != HandshakeStage.IDLE && handshakeStage != HandshakeStage.COMPLETED && handshakeStage != HandshakeStage.FAILED -> "Handshake: ${handshakeStage.name}"
                    connectionState != WebRtcState.Idle -> "WebRTC: ${connectionState.name}"
                    else -> progressMessage ?: "Initializing..."
                }
                Text(
                    text = statusText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Call, "End Call")
            }
        }
    }
}