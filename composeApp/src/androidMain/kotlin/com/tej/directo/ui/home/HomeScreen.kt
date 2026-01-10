package com.tej.directo.ui.home

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
import com.tej.directo.p2p.core.discovery.ConnectionType

@Composable
fun HomeScreen(
    onModeSelected: (ConnectionType, Boolean) -> Unit, // Boolean true = Host, false = Joiner
    processing: Boolean = false
) {
    var showRoleDialog by remember { mutableStateOf<ConnectionType?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Directo", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text("Universal P2P Video Calls", style = MaterialTheme.typography.bodyLarge)
        
        Spacer(modifier = Modifier.height(48.dp))
        
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
                    title = "QR Code",
                    subtitle = "Manual Handshake",
                    icon = Icons.Default.QrCode,
                    onClick = { showRoleDialog = ConnectionType.QR } 
                )
            }
        }
    }

    if (showRoleDialog != null) {
        AlertDialog(
            onDismissRequest = { showRoleDialog = null },
            title = { Text("Choose Role") },
            text = { Text("Would you like to Start a new call (Host) or Join an existing one?") },
            confirmButton = {
                Button(onClick = { 
                    onModeSelected(showRoleDialog!!, true)
                    showRoleDialog = null
                }) { Text("Start Call") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    onModeSelected(showRoleDialog!!, false)
                    showRoleDialog = null
                }) { Text("Join Call") }
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