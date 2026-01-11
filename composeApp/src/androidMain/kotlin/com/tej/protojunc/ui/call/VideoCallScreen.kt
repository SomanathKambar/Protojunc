package com.tej.protojunc.ui.call

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.tej.protojunc.webrtc.WebRtcSessionManager
import com.tej.protojunc.webrtc.WebRtcState
import com.tej.protojunc.ui.ConnectionViewModel
import com.tej.protojunc.webrtc.HandshakeStage
import com.tej.protojunc.discovery.DiscoveryManager
import com.tej.protojunc.discovery.PeerDiscovered
import com.tej.protojunc.p2p.core.discovery.ConnectionType
import com.tej.protojunc.p2p.core.orchestrator.CallSessionOrchestrator
import com.tej.protojunc.signaling.*
import com.tej.protojunc.signalingServerHost
import com.tej.protojunc.deviceName
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
    val errorMessage by sessionManager.errorMessage.collectAsState()
    val handshakeStage by viewModel.handshakeStage.collectAsState()
    val localSdp by viewModel.localSdp.collectAsState()

    var showXmppOptions by remember { mutableStateOf(connectionType == ConnectionType.XMPP) }
    var selectedXmppMode by remember { mutableStateOf<SignalingMessage.Type?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val orchestrator = remember { 
        CallSessionOrchestrator(
            webRtcManager = sessionManager, 
            scope = coroutineScope,
            onHandshakeStageChanged = { viewModel.setHandshakeStage(it) }
        ) 
    }
    val signalingState by orchestrator.signalingState.collectAsState()
    val chatMessages by orchestrator.chatMessages.collectAsState("")
    var textInput by remember { mutableStateOf("") }
    
    var retryTrigger by remember { mutableStateOf(0) }

    // Production RCA: Handle automated handshake based on role and connection mode
    LaunchedEffect(isHost, connectionType, retryTrigger, selectedXmppMode) {
        // Only run if not waiting for XMPP selection
        if (connectionType == ConnectionType.XMPP && selectedXmppMode == null) return@LaunchedEffect

        when (connectionType) {
            ConnectionType.BLE -> {
                sessionManager.createPeerConnection()
                // Bluetooth logic only runs if connectionType is BLE
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
            ConnectionType.ONLINE -> {
                val serverClient = KtorSignalingClient(
                    host = signalingServerHost, 
                    port = com.tej.protojunc.signalingServerPort,
                    roomCode = roomCode, 
                    deviceName = com.tej.protojunc.deviceName
                )
                orchestrator.setSignalingClient(serverClient)
                orchestrator.startCall(isHost)
            }
            ConnectionType.XMPP -> {
                val xmppClient = XmppSignalingClient(jid = "user@example.com")
                orchestrator.setSignalingClient(xmppClient)
                // If it's a message only, we skip peer connection creation inside startCall for now or handle it.
                orchestrator.startCall(isHost, mode = selectedXmppMode ?: SignalingMessage.Type.VIDEO_CALL)
            }
            ConnectionType.QR -> {
                sessionManager.createPeerConnection()
                if (isHost) {
                    viewModel.prepareInvite {}
                }
            }
            else -> {
                Logger.w { "ConnectionType $connectionType logic not implemented" }
            }
        }
    }

    if (showXmppOptions) {
        AlertDialog(
            onDismissRequest = { /* Force selection or back */ },
            title = { Text("XMPP Mode Selection") },
            text = { Text("Choose how you want to test the XMPP handshake:") },
            confirmButton = {
                Column(Modifier.fillMaxWidth()) {
                    Button(onClick = { selectedXmppMode = SignalingMessage.Type.VIDEO_CALL; showXmppOptions = false }, Modifier.fillMaxWidth()) { Text("Video Call") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { selectedXmppMode = SignalingMessage.Type.VOICE_CALL; showXmppOptions = false }, Modifier.fillMaxWidth()) { Text("Voice Call") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { selectedXmppMode = SignalingMessage.Type.MESSAGE; showXmppOptions = false }, Modifier.fillMaxWidth()) { Text("Text Message Only") }
                }
            },
            dismissButton = {
                TextButton(onClick = onEndCall) { Text("Cancel") }
            }
        )
    }

    val reconnect = {
        retryTrigger++
        viewModel.clearError()
        sessionManager.reset()
    }

    // Auto-broadcast if we are host and offer is ready (for BLE mode)
    LaunchedEffect(localSdp) {
        if (isHost && localSdp != null && connectionType == ConnectionType.BLE) {
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
        if (selectedXmppMode == SignalingMessage.Type.MESSAGE) {
            // Chat UI Mode
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Spacer(Modifier.height(80.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.3f), MaterialTheme.shapes.medium).padding(16.dp)) {
                    Text("Chat Content:\n$chatMessages", color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = Color.White, focusedTextColor = Color.White)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { 
                        coroutineScope.launch {
                            orchestrator.sendTextMessage(textInput)
                            textInput = ""
                        }
                    }) { Text("Send") }
                }
                Spacer(Modifier.height(80.dp))
            }
        } else if (remoteTrack != null) {

            WebRtcVideoView(
                videoTrack = remoteTrack,
                modifier = Modifier.fillMaxSize()
            )
        } else if (connectionType == ConnectionType.QR && remoteTrack == null) {
            // QR Handshake Layer
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isHost) {
                    if (localSdp != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Partner scans this QR", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(24.dp))
                            val qrBitmap = remember(localSdp) { 
                                com.tej.protojunc.util.QrUtils.generateQrCode(localSdp!!) 
                            }
                            if (qrBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Offer",
                                    modifier = Modifier.size(300.dp).background(Color.White).padding(12.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Joiner Scanner UI placeholder
                    Text("Scanner active... Point at Host's QR", color = Color.White)
                }
            }
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
                containerColor = if (errorMessage != null) Color.Red.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.5f)
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                        connectionState == WebRtcState.Closed && signalingState == SignalingState.CONNECTED -> "Network: CLOSED (Signaling OK)"
                                        connectionState != WebRtcState.Idle -> "Network: ${connectionState.name}"
                                        else -> progressMessage ?: "Initializing..."
                                    }
                                    Text(
                                        text = statusText,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge
                                    )                    
                    if (connectionType == ConnectionType.ONLINE || connectionType == ConnectionType.XMPP) {
                        VerticalDivider(modifier = Modifier.height(16.dp).padding(horizontal = 8.dp), color = Color.Gray)
                        val sigColor = when(signalingState) {
                            SignalingState.CONNECTED -> Color.Green
                            SignalingState.CONNECTING -> Color.Yellow
                            SignalingState.ERROR -> Color.Red
                            else -> Color.Gray
                        }
                        Box(modifier = Modifier.size(8.dp).background(sigColor, androidx.compose.foundation.shape.CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Signaling: ${signalingState.name}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                
                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        }

        // Detailed Progress Overlay (Intermittent State Tracker)
        if (remoteTrack == null && connectionState != WebRtcState.Connected && connectionState != WebRtcState.Failed && connectionType != ConnectionType.QR) {
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
            if (connectionState == WebRtcState.Failed || connectionState == WebRtcState.Closed) {
                FloatingActionButton(
                    onClick = reconnect,
                    containerColor = Color.Yellow,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Refresh, "Reconnect")
                }
            }

            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        orchestrator.endCall()
                        onEndCall()
                    }
                },
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Call, "End Call")
            }
        }
    }
}
