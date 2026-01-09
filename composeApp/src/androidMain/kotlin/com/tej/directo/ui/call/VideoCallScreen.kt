package com.tej.directo.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tej.directo.webrtc.WebRtcSessionManager
import com.tej.directo.webrtc.WebRtcState

@Composable
fun VideoCallScreen(
    sessionManager: WebRtcSessionManager,
    onEndCall: () -> Unit
) {
    val remoteTrack by sessionManager.remoteVideoTrack.collectAsState()
    val localTrack by sessionManager.localVideoTrack.collectAsState()
    val connectionState by sessionManager.connectionState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Simple UI for state
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "State: ${connectionState.name}",
                color = if (connectionState == WebRtcState.Failed) Color.Red else Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (remoteTrack != null) {
            // In a real app, use the VideoRenderer from previous step
            Text("VIDEO FEED ACTIVE", color = Color.Green, modifier = Modifier.align(Alignment.Center))
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }

        Surface(
            modifier = Modifier.size(120.dp, 160.dp).align(Alignment.TopEnd).padding(16.dp),
            color = Color.DarkGray,
            shape = MaterialTheme.shapes.medium
        ) {
            if (localTrack != null) {
                Text("Self", color = Color.White, modifier = Modifier.padding(8.dp))
            }
        }
        
        Button(
            onClick = onEndCall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("End Call", color = Color.White)
        }
    }
}

