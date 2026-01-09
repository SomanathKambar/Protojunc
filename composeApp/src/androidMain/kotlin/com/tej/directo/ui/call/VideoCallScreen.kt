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

@Composable
fun VideoCallScreen(
    sessionManager: WebRtcSessionManager,
    viewModel: ConnectionViewModel,
    discoveryManager: DiscoveryManager? = null,
    selectedPeer: PeerDiscovered? = null,
    onEndCall: () -> Unit
) {
    val remoteTrack by sessionManager.remoteVideoTrack.collectAsState()
    val localTrack by sessionManager.localVideoTrack.collectAsState()
    val connectionState by sessionManager.connectionState.collectAsState()
    val progressMessage by sessionManager.progressMessage.collectAsState()
    val handshakeStage by viewModel.handshakeStage.collectAsState()

    // Trigger Handshake if needed
    LaunchedEffect(selectedPeer) {
        if (selectedPeer != null && discoveryManager != null && handshakeStage == HandshakeStage.IDLE) {
            viewModel.initiateBleHandshake(discoveryManager, selectedPeer) {
                // Handshake ready, WebRTC will take over
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ... (existing Video Views)
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
                    val loadingText = if (selectedPeer != null && handshakeStage != HandshakeStage.COMPLETED) {
                        "Handshake: ${handshakeStage.name.replace("_", " ")}"
                    } else {
                        progressMessage ?: "Waiting for remote video..."
                    }
                    Text(loadingText, color = Color.LightGray)
                }
            }
        }

        // Local Preview (Small Box)
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
                val statusText = if (selectedPeer != null && handshakeStage != HandshakeStage.COMPLETED && handshakeStage != HandshakeStage.FAILED) {
                    "Stage: ${handshakeStage.name}"
                } else {
                    progressMessage ?: connectionState.name
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