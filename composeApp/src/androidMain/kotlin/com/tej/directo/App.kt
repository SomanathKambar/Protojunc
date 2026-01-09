package com.tej.directo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tej.directo.ui.ConnectionViewModel
import com.tej.directo.ui.Screen
import com.tej.directo.ui.home.HomeScreen
import com.tej.directo.ui.qr.InviteQrScreen
import com.tej.directo.ui.qr.JoinQrScreen
import com.tej.directo.ui.call.VideoCallScreen
import com.tej.directo.ui.bluetooth.InviteBleScreen
import com.tej.directo.ui.bluetooth.JoinBleScreen
import com.shepeliev.webrtckmp.SessionDescriptionType
import kotlinx.coroutines.launch

import androidx.compose.ui.input.pointer.pointerInput

import com.tej.directo.discovery.PeerDiscovered
import com.tej.directo.discovery.AndroidPeripheralAdvertiser
import com.tej.directo.discovery.KableDiscoveryManager
import com.tej.directo.discovery.DiscoveryManager

@Composable
fun App() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: ConnectionViewModel = viewModel()
    val scope = rememberCoroutineScope()
    
    val localSdp by viewModel.localSdp.collectAsState()
    val isInitializing by viewModel.isInitializing.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val globalError by viewModel.errorMessage.collectAsState()
    val progressMessage by viewModel.progressMessage.collectAsState()
    
    var isNavigating by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<Screen?>(null) }
    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var selectedPeer by remember { mutableStateOf<PeerDiscovered?>(null) }
    val advertiser = remember {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        AndroidPeripheralAdvertiser(context, manager.adapter)
    }
    val discoveryManager: DiscoveryManager = remember { KableDiscoveryManager(advertiser) }

    // Reset navigation lock after a timeout as a safety measure
    LaunchedEffect(isNavigating) {
        if (isNavigating) {
            kotlinx.coroutines.delay(5000)
            isNavigating = false
        }
    }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> 
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (manager.adapter.isEnabled) {
            pendingBluetoothAction?.invoke()
        }
        pendingBluetoothAction = null
        isNavigating = false
    }

    val checkAndRequestBluetooth = { onReady: () -> Unit ->
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!manager.adapter.isEnabled) {
            pendingBluetoothAction = onReady
            btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            onReady()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Check if at least essential permissions are granted (Camera/Audio)
        val essential = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val essentialGranted = essential.all { results[it] == true }
        
        if (essentialGranted) {
            pendingNavigation?.let { screen ->
                navController.navigate(screen.name)
            }
        }
        pendingNavigation = null
        isNavigating = false
    }

    val checkPermissions = { onGranted: () -> Unit ->
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        val allGranted = required.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onGranted()
        } else {
            val allToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            isNavigating = true
            permissionLauncher.launch(allToRequest)
        }
    }

    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                NavHost(navController = navController, startDestination = Screen.Home.name) {
                    composable(Screen.Home.name) {
                        HomeScreen(
                            onNavigateToInvite = { 
                                if (!isNavigating) {
                                    pendingNavigation = Screen.InviteSelection
                                    checkPermissions {
                                        navController.navigate(Screen.InviteSelection.name)
                                        pendingNavigation = null
                                    }
                                }
                            },
                            onNavigateToJoin = { 
                                if (!isNavigating) {
                                    pendingNavigation = Screen.JoinSelection
                                    checkPermissions {
                                        navController.navigate(Screen.JoinSelection.name)
                                        pendingNavigation = null
                                    }
                                }
                            },
                            processing = isNavigating
                        )
                    }
                    
                    composable(Screen.InviteSelection.name) {
                        SelectionScreen(
                            title = "Invite Partner",
                            onQr = {
                                if (!isNavigating) {
                                    isNavigating = true
                                    viewModel.prepareInvite {
                                        isNavigating = false
                                        navController.navigate(Screen.InviteQr.name)
                                    }
                                }
                            },
                            onBle = { 
                                if (!isNavigating) {
                                    isNavigating = true
                                    checkAndRequestBluetooth {
                                        isNavigating = false
                                        navController.navigate(Screen.InviteBle.name)
                                    }
                                }
                            },
                            onBack = { 
                                viewModel.cancel()
                                navController.popBackStack() 
                            },
                            processing = isNavigating || isProcessing
                        )
                    }
                    
                    composable(Screen.JoinSelection.name) {
                        SelectionScreen(
                            title = "Join Partner",
                            onQr = { 
                                navController.navigate(Screen.JoinQr.name) 
                            },
                            onBle = { 
                                if (!isNavigating) {
                                    isNavigating = true
                                    checkAndRequestBluetooth {
                                        isNavigating = false
                                        navController.navigate(Screen.JoinBle.name)
                                    }
                                }
                            },
                            onBack = { 
                                viewModel.cancel()
                                navController.popBackStack() 
                            },
                            processing = isNavigating
                        )
                    }

                    composable(Screen.InviteQr.name) {
                        InviteQrScreen(
                            localOfferSdp = localSdp,
                            onAnswerScanned = { answer ->
                                viewModel.handleAnswerScanned(answer) {
                                    navController.navigate(Screen.VideoCall.name)
                                }
                            },
                            onBack = { 
                                viewModel.cancel()
                                navController.popBackStack(Screen.Home.name, false) 
                            }
                        )
                    }

                    composable(Screen.JoinQr.name) {
                        JoinQrScreen(
                            localAnswerSdp = localSdp,
                            onOfferScanned = { offer ->
                                viewModel.handleOfferScanned(offer)
                            },
                            onRescan = { viewModel.cancel() },
                            onComplete = { navController.navigate(Screen.VideoCall.name) },
                            onBack = { 
                                viewModel.cancel()
                                navController.popBackStack(Screen.Home.name, false) 
                            }
                        )
                    }
                    
                                    composable(Screen.VideoCall.name) {
                                        VideoCallScreen(
                                            sessionManager = viewModel.sessionManager,
                                            viewModel = viewModel,
                                            discoveryManager = discoveryManager,
                                            selectedPeer = selectedPeer,
                                            onEndCall = { 
                                                selectedPeer = null
                                                viewModel.endCall()
                                                navController.popBackStack(Screen.Home.name, false) 
                                            }
                                        )
                                    }
                    
                                    composable(Screen.InviteBle.name) {
                                        InviteBleScreen(
                                            onAnswerReceived = { answer ->
                                                viewModel.handleAnswerScanned(answer) {
                                                    navController.navigate(Screen.VideoCall.name)
                                                }
                                            },
                                            onBack = { 
                                                viewModel.cancel()
                                                navController.popBackStack() 
                                            }
                                        )
                                    }
                    
                                    composable(Screen.JoinBle.name) {
                                         JoinBleScreen(
                                             discoveryManager = discoveryManager,
                                             onPeerSelected = { peer ->
                                                 selectedPeer = peer
                                                 navController.navigate(Screen.VideoCall.name)
                                             },
                                             onBack = { 
                                                 viewModel.cancel()
                                                 navController.popBackStack() 
                                             }
                                         )
                                    }                }

                if (isInitializing || isProcessing || isNavigating) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                // Consume all clicks to prevent background interaction
                            },
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isNavigating) "Loading..." else (progressMessage ?: "Preparing..."),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                if (globalError != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearError() },
                        confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
                        title = { Text("Error") },
                        text = { Text(globalError!!) }
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionScreen(title: String, onQr: () -> Unit, onBle: () -> Unit, onBack: () -> Unit, processing: Boolean = false) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { if (!processing) onQr() }, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !processing
        ) { 
            Text("Via QR Code") 
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { if (!processing) onBle() }, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !processing
        ) { 
            Text("Via Bluetooth") 
        }
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onBack, enabled = !processing) { Text("Back") }
    }
}