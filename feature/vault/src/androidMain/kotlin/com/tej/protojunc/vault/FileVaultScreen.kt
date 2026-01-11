package com.tej.protojunc.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tej.protojunc.ui.theme.ProtojuncCard
import com.tej.protojunc.ui.theme.ProtojuncButton
import com.tej.protojunc.ui.theme.RadarDiscoveryUI
import com.tej.protojunc.models.NearbyPeer
import com.tej.protojunc.models.TransferStatus
import com.tej.protojunc.models.FileMetadata
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.tej.protojunc.common.UserIdentity
import kotlinx.coroutines.launch

@Composable
fun FileVaultScreen(
    manager: FileTransferManager,
    userIdentity: UserIdentity?,
    nearbyPeers: List<NearbyPeer>,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = SEND, 1 = RECEIVE

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("SEND", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("RECEIVE", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            }
        }

        Box(Modifier.weight(1f)) {
            if (selectedTab == 0) {
                SenderView(manager, nearbyPeers)
            } else {
                ReceiverView(manager)
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)) {
            Text("Exit Vault", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun SenderView(manager: FileTransferManager, nearbyPeers: List<NearbyPeer>) {
    val status by manager.transferStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedPeer by remember { mutableStateOf<NearbyPeer?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedUri = uri
    }

    Column(modifier = Modifier.padding(16.dp)) {
        if (selectedPeer == null) {
            Text("1. TAP A PARTNER ON RADAR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            RadarDiscoveryUI(
                nearbyPeers = nearbyPeers,
                onPeerClick = { selectedPeer = it },
                modifier = Modifier.height(350.dp)
            )
        } else {
            // Peer Selected
            Text("PARTNER SELECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            ProtojuncCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(40.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Text(selectedPeer!!.displayName.take(1)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(selectedPeer!!.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("Ready for handshake", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { selectedPeer = null; selectedUri = null }) { Icon(Icons.Default.Refresh, null) }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (selectedUri == null) {
                Text("2. SELECT CONTENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                ProtojuncButton(
                    onClick = { launcher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.FileUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Gallery / Picker")
                }
            } else {
                // File Selected
                Text("3. START SECURE TRANSFER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                ProtojuncCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(16.dp)) {
                        Text("File: ${selectedUri?.lastPathSegment}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        
                        if (status is TransferStatus.Idle) {
                            ProtojuncButton(
                                onClick = { scope.launch { manager.sendFile("192.168.49.1", selectedUri.toString()) } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Push to ${selectedPeer?.displayName}")
                            }
                        }
                    }
                }
            }
        }

        // Progress Overlay
        when (val s = status) {
            is TransferStatus.Progress -> {
                val progress = s.bytesTransferred.toFloat() / s.totalBytes
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text("Transferring: ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            is TransferStatus.Completed -> {
                Text("Transfer Success ✅", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 16.dp))
                Button(onClick = { selectedPeer = null; selectedUri = null }) { Text("Done") }
            }
            is TransferStatus.Error -> {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                Button(onClick = { /* retry */ }) { Text("Retry") }
            }
            else -> {}
        }
    }
}

@Composable
fun ReceiverView(manager: FileTransferManager) {
    val isServerRunning by manager.isServerRunning.collectAsState()
    val incoming by manager.incomingTransfers.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        ProtojuncCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (isServerRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isServerRunning) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, 
                     null, tint = if (isServerRunning) MaterialTheme.colorScheme.tertiary else Color.Gray)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Vault Visibility", style = MaterialTheme.typography.titleMedium)
                    Text(if (isServerRunning) "OPEN - Waiting for senders..." else "LOCKED - Senders can't see you", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = isServerRunning, onCheckedChange = {
                    scope.launch {
                        if (it) {
                            manager.startFileServer()
                        } else {
                            manager.stopFileServer()
                        }
                    }
                })
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("INCOMING VAULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        
        if (incoming.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No files received yet", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(incoming) { file ->
                ListItem(
                    headlineContent = { Text(file.name) },
                    supportingContent = { Text("${file.size / 1024} KB") },
                    leadingContent = { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }
        }
    }
}