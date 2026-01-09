package com.tej.directo.ui.qr

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.tej.directo.util.QrCodeAnalyzer
import com.tej.directo.util.QrUtils

@Composable
fun InviteQrScreen(
    localOfferSdp: String?,
    onAnswerScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(1) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Host: Step $step", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (localOfferSdp == null) {
            CircularProgressIndicator()
            Text("Generating Offer...")
        } else if (step == 1) {
            Text("1. Show this to your partner", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            val qrBitmap = remember(localOfferSdp) { QrUtils.generateQrCode(localOfferSdp) }
            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Offer QR", modifier = Modifier.size(300.dp))
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { step = 2 }) { Text("Done, now SCAN partner's Answer") }
        } else {
            Text("2. Scan their Answer QR", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            QrScannerView(onQrScanned = onAnswerScanned)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onBack) { Text("Cancel") }
    }
}

@Composable
fun JoinQrScreen(
    localAnswerSdp: String?,
    onOfferScanned: (String) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Guest Mode", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (localAnswerSdp == null) {
            Text("1. SCAN partner's Offer QR", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            QrScannerView(onQrScanned = onOfferScanned)
        } else {
            Text("2. Show this Answer to partner", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            val qrBitmap = remember(localAnswerSdp) { QrUtils.generateQrCode(localAnswerSdp) }
            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Answer QR", modifier = Modifier.size(300.dp))
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onComplete) { Text("I've shown it, Connect!") }
        }

        Spacer(modifier = Modifier.weight(1f))
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