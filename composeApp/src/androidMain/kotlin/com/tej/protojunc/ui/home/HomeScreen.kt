package com.tej.protojunc.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tej.protojunc.p2p.core.discovery.ConnectionType

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import com.tej.protojunc.models.NearbyPeer
import com.tej.protojunc.ui.theme.ProtojuncCard
import com.tej.protojunc.ui.theme.ProtojuncButton
import com.tej.protojunc.ui.theme.ProtojuncStatusIndicator

@Composable
fun HomeScreen(
    nearbyPeers: List<NearbyPeer> = emptyList(),
    onModeSelected: (ConnectionType, Boolean) -> Unit, // Boolean true = Host, false = Joiner
    onVaultClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    serverStatus: Boolean = false,
    processing: Boolean = false,
    onUpdateConfig: (String, Int) -> Unit = { _, _ -> }
) {
    var showRoleDialog by remember { mutableStateOf<ConnectionType?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.tej.protojunc.R.drawable.ic_launcher_foreground),
                contentDescription = "Protojunc Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Protojunc", 
                    style = MaterialTheme.typography.headlineLarge, 
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                ProtojuncStatusIndicator(isActive = serverStatus)
            }
        }
        Text("Universal P2P Communication", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Nearby Contacts Section (New in 2.0)
        if (nearbyPeers.isNotEmpty()) {
            Text(
                "NEARBY CONTACTS", 
                modifier = Modifier.fillMaxWidth(), 
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(nearbyPeers) { peer ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(70.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    peer.displayName.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            peer.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            "COMMUNICATION MODES", 
            modifier = Modifier.fillMaxWidth(), 
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                ModeCard(
                    title = "WiFi Direct",
                    subtitle = "High Speed Local",
                    icon = Icons.Default.SettingsInputAntenna,
                    onClick = { showRoleDialog = ConnectionType.WIFI_DIRECT }
                )
            }
            item {
                ModeCard(
                    title = "BT Socket",
                    subtitle = "Robust Stream",
                    icon = Icons.Default.BluetoothAudio,
                    onClick = { showRoleDialog = ConnectionType.BT_SOCKET } 
                )
            }
            item {
                ModeCard(
                    title = "BLE Link",
                    subtitle = "Low Power Discovery",
                    icon = Icons.Default.Bluetooth,
                    onClick = { showRoleDialog = ConnectionType.BLE }
                )
            }
            item {
                ModeCard(
                    title = "Online Call",
                    subtitle = "Via Global Server",
                    icon = Icons.Default.Language,
                    onClick = { showRoleDialog = ConnectionType.ONLINE }
                )
            }
            item {
                ModeCard(
                    title = "XMPP",
                    subtitle = "Enterprise Jingle",
                    icon = Icons.Default.Lock,
                    onClick = { showRoleDialog = ConnectionType.XMPP }
                )
            }
            item {
                ModeCard(
                    title = "Mesh Link",
                    subtitle = "Decentralized Group",
                    icon = Icons.Default.Groups,
                    onClick = { onModeSelected(ConnectionType.MESH, true) }
                )
            }
            item {
                ModeCard(
                    title = "File Vault",
                    subtitle = "Secure P2P Sharing",
                    icon = Icons.Default.FolderZip,
                    onClick = onVaultClick
                )
            }
            item {
                ModeCard(
                    title = "Dashboard",
                    subtitle = "Live Clinical Ops",
                    icon = Icons.Default.MonitorHeart,
                    onClick = onDashboardClick
                )
            }
            item {
                ModeCard(
                    title = "QR Code",
                    subtitle = "Manual Handshake",
                    icon = Icons.Default.QrCode,
                    onClick = { showRoleDialog = ConnectionType.QR } 
                )
            }
        }
    }

    if (showSettings) {
        var tempHost by remember { mutableStateOf(com.tej.protojunc.signalingServerHost) }
        var tempPort by remember { mutableStateOf(com.tej.protojunc.signalingServerPort.toString()) }

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Server Configuration") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempHost,
                        onValueChange = { tempHost = it },
                        label = { Text("Server IP / Host") },
                        placeholder = { Text("e.g. 192.168.1.100") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = { tempPort = it },
                        label = { Text("Server Port") },
                        placeholder = { Text("8080") }
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(if (serverStatus) Color.Green else Color.Red, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(if (serverStatus) "Server Connected" else "Server Unreachable", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    onUpdateConfig(tempHost, tempPort.toIntOrNull() ?: 8080)
                    showSettings = false 
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Cancel") }
            }
        )
    }

    if (showRoleDialog != null) {
        val isMesh = showRoleDialog == ConnectionType.MESH
        AlertDialog(
            onDismissRequest = { showRoleDialog = null },
            title = { Text(if (isMesh) "Mesh Group" else "Choose Role") },
            text = { 
                Text(
                    if (isMesh) "Would you like to Create a decentralized group or Join one nearby?"
                    else "Would you like to Start a new call (Host) or Join an existing one?"
                ) 
            },
            confirmButton = {
                Button(onClick = { 
                    onModeSelected(showRoleDialog!!, true)
                    showRoleDialog = null
                }) { 
                    Text(if (isMesh) "Create Group" else "Start Call") 
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    onModeSelected(showRoleDialog!!, false)
                    showRoleDialog = null
                }) { 
                    Text(if (isMesh) "Join Group" else "Join Call") 
                }
            }
        )
    }
}

@Composable
fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}