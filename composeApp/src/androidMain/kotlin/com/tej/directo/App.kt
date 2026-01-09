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
    
    var showCancelDialog by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<Screen?>(null) }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> 
        pendingNavigation?.let { screen ->
            if (screen == Screen.InviteSelection) {
                viewModel.prepareInvite { navController.navigate(Screen.InviteQr.name) }
            }
            pendingNavigation = null
        }
    }

    val checkAndRequestBluetooth = { onReady: () -> Unit ->
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!manager.adapter.isEnabled) {
            btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            onReady()
        }
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            pendingNavigation?.let { screen ->
                navController.navigate(screen.name)
                pendingNavigation = null
            }
        }
    }

    MaterialTheme {
        // ... (existing BackHandler and showCancelDialog logic)

        Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = Screen.Home.name) {
                composable(Screen.Home.name) {
                    HomeScreen(
                        onNavigateToInvite = { 
                            pendingNavigation = Screen.InviteSelection
                            permissionLauncher.launch(permissions)
                        },
                        onNavigateToJoin = { 
                            pendingNavigation = Screen.JoinSelection
                            permissionLauncher.launch(permissions)
                        }
                    )
                }
                
                composable(Screen.InviteSelection.name) {
                    SelectionScreen(
                        title = "Invite Partner",
                        onQr = {
                            viewModel.prepareInvite {
                                navController.navigate(Screen.InviteQr.name)
                            }
                        },
                        onBle = { 
                            checkAndRequestBluetooth {
                                navController.navigate(Screen.InviteBle.name)
                            }
                        },
                        onBack = { 
                            viewModel.cancel()
                            navController.popBackStack() 
                        }
                    )
                }
                
                composable(Screen.JoinSelection.name) {
                    SelectionScreen(
                        title = "Join Partner",
                        onQr = { navController.navigate(Screen.JoinQr.name) },
                        onBle = { 
                            checkAndRequestBluetooth {
                                navController.navigate(Screen.JoinBle.name)
                            }
                        },
                        onBack = { 
                            viewModel.cancel()
                            navController.popBackStack() 
                        }
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
                        onEndCall = { 
                            viewModel.endCall()
                            navController.popBackStack(Screen.Home.name, false) 
                        }
                    )
                }

                composable(Screen.InviteBle.name) {
                    InviteBleScreen(onBack = { 
                        viewModel.cancel()
                        navController.popBackStack() 
                    })
                }

                composable(Screen.JoinBle.name) {
                     JoinBleScreen(
                         onPeerSelected = { peer ->
                             viewModel.handleBlePeerSelected(peer) {
                                 navController.navigate(Screen.VideoCall.name)
                             }
                         }, 
                         onBack = { 
                             viewModel.cancel()
                             navController.popBackStack() 
                         }
                     )
                }
            }

            if (isInitializing || isProcessing) {
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
                            text = progressMessage ?: "Preparing...",
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

@Composable
fun SelectionScreen(title: String, onQr: () -> Unit, onBle: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onQr, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Via QR Code") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBle, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Via Bluetooth") }
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onBack) { Text("Back") }
    }
}