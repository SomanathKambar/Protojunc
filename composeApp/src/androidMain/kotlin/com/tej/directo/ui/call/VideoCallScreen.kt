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
import com.tej.directo.p2p.core.discovery.ConnectionType
import com.tej.directo.p2p.core.orchestrator.CallSessionOrchestrator
import com.tej.directo.p2p.impl.server.KtorSignalingClient
import com.tej.directo.p2p.impl.xmpp.XmppSignalingClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import co.touchlab.kermit.Logger

@Composable
fun ProgressStepper(currentStage: HandshakeStage) {
    val stages = listOf(
        HandshakeStage.INITIALIZING_HARDWARE to "Hardware",
        HandshakeStage.STARTING_DISCOVERY to "Discovery",
        HandshakeStage.EXCHANGING_SDP_OFFER to "Handshake",
        HandshakeStage.GATHERING_ICE_CANDIDATES to "Gathering",
        HandshakeStage.LINK_ESTABLISHED to "Connecting"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        stages.forEachIndexed { index, (stage, label) ->
            val isCompleted = currentStage.ordinal > stage.ordinal || currentStage == HandshakeStage.COMPLETED || currentStage == HandshakeStage.LINK_ESTABLISHED
            val isCurrent = currentStage == stage
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val circleColor = when {
                    isCompleted -> Color.Green
                    isCurrent -> Color.Yellow
                    else -> Color.Gray
                }
                
                Box(modifier = Modifier.size(12.dp).background(circleColor, androidx.compose.foundation.shape.CircleShape))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label,
                    color = if (isCurrent) Color.White else Color.LightGray,
                    style = if (isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                }
            }
            
            if (index < stages.size - 1) {
                Box(modifier = Modifier.width(2.dp).height(8.dp).background(Color.DarkGray).padding(start = 5.dp))
            }
        }
    }
}

@Composable
fun VideoCallScreen(
    sessionManager: WebRtcSessionManager,
    viewModel: ConnectionViewModel,
    discoveryManager: DiscoveryManager,
    isHost: Boolean,
    roomCode: String,
    connectionType: ConnectionType = ConnectionType.BLE,
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
    val orchestrator = remember { CallSessionOrchestrator(sessionManager, coroutineScope) }

    // Production RCA: Handle automated handshake based on role and connection mode
    LaunchedEffect(isHost, connectionType) {
        // 1. OPEN CAMERA IMMEDIATELY for everyone
        sessionManager.createPeerConnection()
        sessionManager.connectionState.filter { it == WebRtcState.Ready }.first()

        when (connectionType) {
            ConnectionType.BLE -> {
                // Legacy BLE logic (refactored into orchestrator flow if needed)
                checkAndRequestBluetooth {
                    if (isHost) {
                        viewModel.prepareInvite {}
                    } else {
                        coroutineScope.launch {
                            var alreadyConnecting = false
                            discoveryManager.observeNearbyPeers().collect { peer ->
                                if (!alreadyConnecting && (roomCode.isEmpty() || peer.roomCode.equals(roomCode, ignoreCase = true))) {
                                    alreadyConnecting = true
                                    viewModel.initiateBleHandshake(discoveryManager, peer) {}
                                }
                            }
                        }
                    }
                }
            }
            ConnectionType.SERVER -> {
                // Online Signaling mode
                val serverClient = KtorSignalingClient(roomCode = roomCode)
                orchestrator.setSignalingClient(serverClient)
                orchestrator.startCall(isHost)
            }
            ConnectionType.XMPP -> {
                // XMPP mode
                val xmppClient = XmppSignalingClient(jid = "user@example.com")
                orchestrator.setSignalingClient(xmppClient)
                orchestrator.startCall(isHost)
            }
            else -> {
                Logger.w { "ConnectionType $connectionType not fully integrated in orchestrator yet" }
            }
        }
    }

    // Auto-broadcast if we are host and offer is ready
    LaunchedEffect(localSdp) {
        if (isHost && localSdp != null) {
            checkAndRequestBluetooth {
                coroutineScope.launch {
                    try {
                        discoveryManager.startAdvertising(roomCode, localSdp!!)
                        discoveryManager.observeMessages().collect { answer ->
                            viewModel.handleAnswerScanned(answer) {
                                // Handshake complete
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(e) { "Advertising failure" }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    handshakeStage != HandshakeStage.IDLE && handshakeStage != HandshakeStage.COMPLETED && handshakeStage != HandshakeStage.FAILED -> {
                        "Stage: ${handshakeStage.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }}..."
                    }
                    connectionState != WebRtcState.Idle -> "Network: ${connectionState.name}"
                    else -> progressMessage ?: "Initializing..."
                }
                Text(
                    text = statusText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Detailed Progress Overlay (Intermittent State Tracker)
        if (remoteTrack == null && connectionState != WebRtcState.Connected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                ProgressStepper(currentStage = handshakeStage)
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