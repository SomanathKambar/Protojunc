package com.tej.protojunc.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tej.protojunc.signaling.KtorSignalingClient
import com.tej.protojunc.signaling.SignalingState
import com.tej.protojunc.core.models.SignalingMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.work.*
import com.tej.protojunc.p2p.data.db.SurgicalReportDao
import com.tej.protojunc.p2p.data.db.SurgicalReportEntity
import com.tej.protojunc.worker.ReportSyncWorker
import org.koin.compose.koinInject

data class DashboardUpdate(val id: Long, val message: String, val timestamp: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    serverHost: String,
    serverPort: Int,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val updates = remember { mutableStateListOf<DashboardUpdate>() }
    var connectionState by remember { mutableStateOf(SignalingState.IDLE) }
    
    val reportDao: SurgicalReportDao = koinInject()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showReportDialog by remember { mutableStateOf(false) }

    val client = remember(serverHost, serverPort) {
        KtorSignalingClient(
            host = serverHost,
            port = serverPort,
            roomCode = "dashboard",
            deviceName = "Android-Dashboard-Client"
        )
    }

    LaunchedEffect(client) {
        launch {
            client.state.collect { connectionState = it }
        }
        launch {
            client.messages.collect { msg ->
                val sdp = msg.sdp
                if (msg.type == SignalingMessage.Type.MESSAGE && sdp != null && sdp.startsWith("Surgical Update:")) {
                    val message = sdp.removePrefix("Surgical Update: ")
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    updates.add(0, DashboardUpdate(System.currentTimeMillis(), message, time))
                    if (updates.size > 50) updates.removeAt(50)
                }
            }
        }
        client.connect()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clinical Care Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val statusColor = when (connectionState) {
                        SignalingState.CONNECTED -> Color.Green
                        SignalingState.CONNECTING -> Color.Yellow
                        else -> Color.Red
                    }
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape, 
                        color = statusColor, 
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(16.dp))
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showReportDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Report")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (updates.isEmpty() && connectionState != SignalingState.CONNECTED) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting to hospital network...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (updates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Waiting for live status updates...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(), 
                    contentPadding = PaddingValues(16.dp), 
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(updates, key = { it.id }) { update ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (update.message.contains("Emergency", ignoreCase = true)) 
                                    MaterialTheme.colorScheme.errorContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp), 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info, 
                                    contentDescription = null, 
                                    tint = if (update.message.contains("Emergency")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(update.message, style = MaterialTheme.typography.titleMedium)
                                    Text(update.timestamp, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showReportDialog) {
            var patientId by remember { mutableStateOf("") }
            var content by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text("New Surgical Report") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = patientId,
                            onValueChange = { patientId = it },
                            label = { Text("Patient ID") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Report Content") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            reportDao.insertReport(SurgicalReportEntity(
                                patientId = patientId,
                                reportContent = content,
                                timestamp = System.currentTimeMillis()
                            ))
                            
                            val constraints = Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                            
                            val syncRequest = OneTimeWorkRequestBuilder<ReportSyncWorker>()
                                .setConstraints(constraints)
                                .build()
                            
                            WorkManager.getInstance(context).enqueue(syncRequest)
                            showReportDialog = false
                        }
                    }) { Text("Save & Sync") }
                },
                dismissButton = {
                    TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}