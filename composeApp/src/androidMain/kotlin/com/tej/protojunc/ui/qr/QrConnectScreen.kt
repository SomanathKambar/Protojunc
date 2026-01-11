package com.tej.protojunc.ui.qr

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tej.protojunc.util.QrCodeAnalyzer
import com.tej.protojunc.util.QrUtils
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun InviteQrScreen(
    localOfferSdp: String?,
    onAnswerScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var showManualDialog by remember { mutableStateOf(false) }

    if (showManualDialog) {
        ManualConnectionDialog(
            isHost = true,
            localSdp = localOfferSdp,
            onRemoteSdpEntered = { 
                showManualDialog = false
                onAnswerScanned(it) 
            },
            onDismiss = { showManualDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Host: Step $step", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (localOfferSdp == null) {
            CircularProgressIndicator()
            Text("Generating Offer...")
        } else if (step == 1) {
            Text("1. Show this to your partner", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            val qrBitmap = remember(localOfferSdp) { QrUtils.generateQrCode(localOfferSdp!!) }
            if (qrBitmap != null) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Offer QR", modifier = Modifier.size(300.dp))
            } else {
                Card(modifier = Modifier.size(300.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Code too large for QR", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        val clipboard = LocalClipboardManager.current
                        Button(onClick = { clipboard.setText(AnnotatedString(localOfferSdp!!)) }) {
                            Text("Copy Code Instead")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { step = 2 }) { Text("Done, now SCAN partner's Answer") }
        } else {
            Text("2. Scan their Answer QR", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                QrScannerView(onQrScanned = onAnswerScanned)
                // Overlay to indicate scanning area
                Box(modifier = Modifier.size(250.dp).background(Color.Transparent, shape = MaterialTheme.shapes.medium)
                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { step = 1 }) { Text("Back to Offer QR") }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { showManualDialog = true }) { Text("Enter Code Manually") }
        TextButton(onClick = onBack) { Text("Cancel") }
    }
}

@Composable
fun JoinQrScreen(
    localAnswerSdp: String?,
    onOfferScanned: (String) -> Unit,
    onRescan: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    var showManualDialog by remember { mutableStateOf(false) }

    if (showManualDialog) {
        ManualConnectionDialog(
            isHost = false,
            localSdp = localAnswerSdp,
            onRemoteSdpEntered = { 
                showManualDialog = false
                onOfferScanned(it) 
            },
            onDismiss = { showManualDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Guest Mode", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (localAnswerSdp == null) {
            Text("1. SCAN partner's Offer QR", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                QrScannerView(onQrScanned = onOfferScanned)
                 // Overlay to indicate scanning area
                Box(modifier = Modifier.size(250.dp).background(Color.Transparent, shape = MaterialTheme.shapes.medium)
                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium))
            }
        } else {
            Text("2. Show this Answer to partner", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            val qrBitmap = remember(localAnswerSdp) { QrUtils.generateQrCode(localAnswerSdp) }
            if (qrBitmap != null) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Answer QR", modifier = Modifier.size(300.dp))
            } else {
                Card(modifier = Modifier.size(300.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Code too large for QR", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        val clipboard = LocalClipboardManager.current
                        Button(onClick = { clipboard.setText(AnnotatedString(localAnswerSdp ?: "")) }) {
                            Text("Copy Answer Instead")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onComplete) { Text("I've shown it, Connect!") }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onRescan) { Text("Rescan Offer") }
        }

        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { showManualDialog = true }) { Text("Enter Code Manually") }
        TextButton(onClick = onBack) { Text("Cancel") }
    }
}

@Composable
fun QrScannerView(onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, QrCodeAnalyzer { result -> onQrScanned(result) }) }
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ManualConnectionDialog(
    isHost: Boolean,
    localSdp: String?,
    onRemoteSdpEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var remoteSdpInput by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isHost) "Manual Connection (Host)" else "Manual Connection (Guest)") },
        text = {
            Column {
                if (localSdp != null) {
                    Text("Your Code (Copy this to share):", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = localSdp,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        trailingIcon = {
                            Button(onClick = {
                                clipboardManager.setText(AnnotatedString(localSdp))
                            }) {
                                Text("Copy")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (!submitted || isHost) {
                    Text(if (isHost) "Partner's Answer Code:" else "Partner's Offer Code:", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = remoteSdpInput,
                        onValueChange = { remoteSdpInput = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Paste code here") }
                    )
                } else {
                    Text("Code generated! Now copy 'Your Code' above and share it back.", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            if (!submitted || isHost) {
                Button(
                    onClick = { 
                        if (remoteSdpInput.isNotBlank()) {
                            onRemoteSdpEntered(remoteSdpInput)
                            submitted = true
                            if (isHost) {
                                // For host, we can dismiss after entering answer as it triggers navigation
                            }
                        }
                    },
                    enabled = remoteSdpInput.isNotBlank()
                ) {
                    Text(if (isHost) "Connect" else "Generate Answer")
                }
            } else {
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (!submitted || isHost) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}