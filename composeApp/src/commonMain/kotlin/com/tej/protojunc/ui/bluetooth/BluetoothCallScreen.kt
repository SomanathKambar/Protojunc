package com.tej.protojunc.ui.bluetooth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tej.protojunc.bluetooth.BluetoothCallManager
import com.tej.protojunc.bluetooth.BluetoothCallStatus
import com.tej.protojunc.bluetooth.BluetoothDeviceDomain

@Composable
fun BluetoothCallScreen(
    manager: BluetoothCallManager,
    onBack: () -> Unit
) {
    val devices by manager.devices.collectAsState()
    val status by manager.status.collectAsState()
    val error by manager.errorMessage.collectAsState()
    val currentPin by manager.pairingCode.collectAsState()
    
    var showConnectDialog by remember { mutableStateOf<BluetoothDeviceDomain?>(null) }
    var pinInput by remember { mutableStateOf(currentPin) }

    LaunchedEffect(Unit) {
        manager.refreshPairedDevices()
        manager.startServer()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bluetooth Direct Call", style = MaterialTheme.typography.headlineMedium)
        Text("My Device: ${manager.localName}", style = MaterialTheme.typography.labelMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Security Settings", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { 
                        pinInput = it
                        manager.setPairingCode(it) 
                    },
                    label = { Text("Connection PIN") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Share this PIN with your partner.", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { manager.requestDiscoverable() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Make Me Visible (300s)")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Status: $status")
        if (error != null) {
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { manager.startScanning() }, enabled = status == BluetoothCallStatus.IDLE) {
                Text("Scan")
            }
            Button(onClick = { manager.stopScanning() }, enabled = status == BluetoothCallStatus.SCANNING) {
                Text("Stop Scan")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (status == BluetoothCallStatus.CONNECTED) {
            Button(onClick = { manager.startAudio() }) {
                Text("Start Audio Call")
            }
        } else if (status == BluetoothCallStatus.AUDIO_STREAMING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { manager.stopCall() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("End Call")
                }
                
                var isSpeakerOn by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { 
                    isSpeakerOn = !isSpeakerOn
                    manager.toggleSpeakerphone(isSpeakerOn)
                }) {
                    Text(if (isSpeakerOn) "Speaker: ON" else "Speaker: OFF")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Available Devices:")
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(devices) { device ->
                val displayName = if (device.name.isEmpty() || device.name == "Unknown") "Unknown Device" else device.name
                val isPaired = device.name.contains("(Paired)")
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { showConnectDialog = device },
                    colors = if (isPaired) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Text(displayName, style = MaterialTheme.typography.titleMedium)
                             if (isPaired) {
                                 Spacer(modifier = Modifier.width(8.dp))
                                 Text("★", color = MaterialTheme.colorScheme.primary) 
                             }
                        }
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        Button(onClick = onBack) {
            Text("Back")
        }
    }
    
    if (showConnectDialog != null) {
        AlertDialog(
            onDismissRequest = { showConnectDialog = null },
            title = { Text("Start Call") },
            text = { Text("Choose call type with ${showConnectDialog?.name ?: "Device"}. Note: Video is experimental.") },
            confirmButton = {
                Button(onClick = {
                    manager.connect(showConnectDialog!!.address, isVideo = false)
                    showConnectDialog = null
                }) { Text("Voice Call") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    // Start Video (Experimental) - currently treated same as voice in manager logic, but UI intent is there
                    manager.connect(showConnectDialog!!.address, isVideo = true)
                    showConnectDialog = null
                }) { Text("Video (Exp)") }
            }
        )
    }
}
