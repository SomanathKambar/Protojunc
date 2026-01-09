package com.tej.directo.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onNavigateToInvite: () -> Unit,
    onNavigateToJoin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Directo P2P Video", style = MaterialTheme.typography.displaySmall)
        Text("No Server. No Internet required.", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            onClick = onNavigateToInvite
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("INVITE", style = MaterialTheme.typography.titleLarge)
                    Text("Show your QR / Advertise via BLE", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            onClick = onNavigateToJoin,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("JOIN", style = MaterialTheme.typography.titleLarge)
                    Text("Scan QR / Search for BLE", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}