package com.tej.protojunc

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
import com.tej.protojunc.ui.ConnectionViewModel
import com.tej.protojunc.ui.Screen
import com.tej.protojunc.ui.home.HomeScreen
import com.tej.protojunc.ui.qr.InviteQrScreen
import com.tej.protojunc.ui.qr.JoinQrScreen
import com.tej.protojunc.ui.call.VideoCallScreen
import com.tej.protojunc.ui.bluetooth.InviteBleScreen
import com.tej.protojunc.ui.bluetooth.JoinBleScreen
import com.shepeliev.webrtckmp.SessionDescriptionType
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import com.tej.protojunc.discovery.PeerDiscovered
import com.tej.protojunc.models.NearbyPeer
import com.tej.protojunc.discovery.AndroidPeripheralAdvertiser
import com.tej.protojunc.discovery.KableDiscoveryManager
import com.tej.protojunc.discovery.DiscoveryManager
import com.tej.protojunc.p2p.core.discovery.ConnectionType
import com.tej.protojunc.bluetooth.AndroidBluetoothCallManager
import com.tej.protojunc.bluetooth.AndroidWifiDirectCallManager
import com.tej.protojunc.bluetooth.BluetoothMeshManager
import com.tej.protojunc.bluetooth.AndroidGattServer
import com.tej.protojunc.bluetooth.AndroidPairedDeviceRepository
import com.tej.protojunc.ui.bluetooth.BluetoothCallScreen
import com.tej.protojunc.ui.wifi.WifiDirectCallScreen
import com.tej.protojunc.vault.FileVaultScreen
import com.tej.protojunc.ui.mesh.MeshCallScreen
import com.tej.protojunc.p2p.core.orchestrator.MeshCoordinator
import com.tej.protojunc.p2p.core.orchestrator.LinkOrchestrator
import com.tej.protojunc.p2p.core.orchestrator.TransportPriority
import com.tej.protojunc.ui.theme.ProtojuncTheme
import com.tej.protojunc.ui.dashboard.DashboardScreen
import com.juul.kable.peripheral

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
    val serverStatus by viewModel.serverStatus.collectAsState()
    val nearbyPeers by viewModel.nearbyPeers.collectAsState()
    val userIdentity by viewModel.userIdentity.collectAsState()
    
    var isNavigating by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<Screen?>(null) }
    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var roomCode by remember { mutableStateOf("APPLE") }
    var selectedPeer by remember { mutableStateOf<PeerDiscovered?>(null) }
    
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val advertiser = remember { AndroidPeripheralAdvertiser(context, bluetoothManager.adapter) }
    val discoveryManager: DiscoveryManager = remember { KableDiscoveryManager(advertiser) }
    val bluetoothCallManager = remember { AndroidBluetoothCallManager(context, scope) }
    val wifiDirectCallManager = remember { AndroidWifiDirectCallManager(context, scope) }
    val pairedDeviceRepository = remember { AndroidPairedDeviceRepository(context) }
    
    val gattServer = remember { AndroidGattServer(context) }
    val localId = userIdentity?.deviceId ?: "unknown_device"
    
    // UI State for Advertising
    val isAdvertising by advertiser.advertisingState.collectAsState(false)
    var bluetoothError by remember { mutableStateOf<String?>(null) }
    
    val meshManager = remember(localId) {
        BluetoothMeshManager(
            localId = localId,
            scope = scope,
            gattServer = gattServer,
            startAdvertising = { uuid -> 
                try {
                    advertiser.startAdvertising(roomCode, uuid, "") 
                } catch (e: Exception) {
                    bluetoothError = "Failed to start advertising: ${e.message}"
                }
            },
            stopAdvertising = { advertiser.stopAdvertising() },
            peripheralBuilder = { macAddress ->
                try {
                    scope.peripheral(macAddress)
                } catch (e: Exception) {
                    null
                }
            }
        )
    }

    val linkOrchestrator: LinkOrchestrator = remember { 
        LinkOrchestrator(scope) { transport: TransportPriority, bitrate: Int ->
            viewModel.sessionManager.setBitrate(bitrate)
        }.apply {
            addTransport(TransportPriority.BLE, meshManager)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            wifiDirectCallManager.release()
        }
    }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> 
        if (bluetoothManager.adapter.isEnabled) {
            pendingBluetoothAction?.invoke()
        }
        pendingBluetoothAction = null
        isNavigating = false
    }

    val checkAndRequestBluetooth = { onReady: () -> Unit ->
        if (!bluetoothManager.adapter.isEnabled) {
            pendingBluetoothAction = onReady
            btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            onReady()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val essential = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val essentialGranted = essential.all { results[it] == true }
        
        if (essentialGranted) {
            pendingBluetoothAction?.invoke()
        }
        pendingNavigation = null
        pendingBluetoothAction = null
        isNavigating = false
    }

    val checkPermissions = { onGranted: () -> Unit ->
        val required = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        val allGranted = required.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onGranted()
        } else {
            isNavigating = true
            pendingBluetoothAction = onGranted
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    // Reset navigation lock after a timeout as a safety measure
    LaunchedEffect(isNavigating) {
        if (isNavigating) {
            kotlinx.coroutines.delay(5000)
            isNavigating = false
        }
    }

    // Auto-start presence if permissions are already granted
    LaunchedEffect(Unit) {
        val required = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        val allGranted = required.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted && bluetoothManager.adapter.isEnabled) {
            viewModel.presenceManager.startPresence()
        }
    }

    ProtojuncTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                NavHost(navController = navController, startDestination = Screen.Home.name) {
                    composable(Screen.Home.name) {
                        HomeScreen(
                            nearbyPeers = nearbyPeers,
                            onVaultClick = { navController.navigate(Screen.FileVault.name) },
                            onDashboardClick = { navController.navigate(Screen.Dashboard.name) },
                            onModeSelected = { type, isHost ->
                                if (!isNavigating) {
                                    val isBluetoothRequired = type == ConnectionType.BLE || 
                                                              type == ConnectionType.BT_SOCKET || 
                                                              type == ConnectionType.WIFI_DIRECT ||
                                                              type == ConnectionType.MESH
                                    
                                    val action = {
                                        viewModel.cancel()
                                        when (type) {
                                            ConnectionType.BT_SOCKET -> navController.navigate(Screen.BluetoothDirectCall.name)
                                            ConnectionType.WIFI_DIRECT -> navController.navigate(Screen.WifiDirectCall.name)
                                            ConnectionType.MESH -> navController.navigate(Screen.MeshCall.name + "?host=$isHost")
                                            else -> navController.navigate(Screen.VideoCall.name + "?host=$isHost&type=${type.name}")
                                        }
                                    }

                                    if (isBluetoothRequired) {
                                        checkPermissions {
                                            checkAndRequestBluetooth {
                                                action()
                                            }
                                        }
                                    } else {
                                        val basicPermissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                        val allGranted = basicPermissions.all {
                                            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        }
                                        if (allGranted) {
                                            action()
                                        } else {
                                            isNavigating = true
                                            pendingBluetoothAction = action
                                            permissionLauncher.launch(basicPermissions.toTypedArray())
                                        }
                                    }
                                }
                            },
                            serverStatus = serverStatus,
                            processing = isNavigating,
                            onUpdateConfig = { host, port ->
                                viewModel.updateServerConfig(host, port)
                            }
                        )
                    }
                    
                    composable(Screen.InviteSelection.name) {
                        SelectionScreen(
                            title = "Invite Partner",
                            roomCode = roomCode,
                            onRoomCodeChange = { roomCode = it },
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
                            roomCode = roomCode,
                            onRoomCodeChange = { roomCode = it },
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
                    
                    composable(
                        route = Screen.VideoCall.name + "?host={isHost}&type={type}",
                        arguments = listOf(
                            androidx.navigation.navArgument("isHost") {
                                type = androidx.navigation.NavType.BoolType
                                defaultValue = false
                            },
                            androidx.navigation.navArgument("type") {
                                type = androidx.navigation.NavType.StringType
                                defaultValue = ConnectionType.BLE.name
                            }
                        )
                    ) { backStackEntry ->
                        val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
                        val typeStr = backStackEntry.arguments?.getString("type") ?: ConnectionType.BLE.name
                        val type = ConnectionType.valueOf(typeStr)
                        
                        VideoCallScreen(
                            sessionManager = viewModel.sessionManager,
                            viewModel = viewModel,
                            discoveryManager = discoveryManager,
                            linkOrchestrator = linkOrchestrator,
                            isHost = isHost,
                            roomCode = roomCode,
                            connectionType = type,
                            checkAndRequestBluetooth = { checkAndRequestBluetooth(it) },
                            onEndCall = { 
                                selectedPeer = null
                                viewModel.endCall()
                                navController.popBackStack(Screen.Home.name, false) 
                            }
                        )
                    }

                    composable(Screen.BluetoothDirectCall.name) {
                        BluetoothCallScreen(
                            manager = bluetoothCallManager,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.WifiDirectCall.name) {
                        WifiDirectCallScreen(
                            manager = wifiDirectCallManager,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.FileVault.name) {
                        val userIdentity by viewModel.userIdentity.collectAsState()
                        FileVaultScreen(
                            manager = viewModel.fileTransferManager,
                            userIdentity = userIdentity,
                            nearbyPeers = nearbyPeers,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Screen.MeshCall.name + "?host={isHost}",
                        arguments = listOf(
                            androidx.navigation.navArgument("isHost") {
                                type = androidx.navigation.NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { backStackEntry ->
                        val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
                        val userIdentity by viewModel.userIdentity.collectAsState()
                        if (userIdentity != null) {
                            val meshCoordinator = remember {
                                MeshCoordinator(
                                    localId = userIdentity!!.deviceId,
                                    isHost = isHost,
                                    scope = scope,
                                    signalingClient = linkOrchestrator,
                                    discoveryManager = discoveryManager,
                                    pairedDeviceRepository = pairedDeviceRepository,
                                    onManualConnect = { address ->
                                        meshManager.connectToDevice(address)
                                    }
                                )
                            }
                            MeshCallScreen(
                                coordinator = meshCoordinator,
                                nearbyPeers = nearbyPeers,
                                localId = localId,
                                isAdvertising = isAdvertising,
                                error = bluetoothError,
                                onDismissError = { bluetoothError = null },
                                onToggleAdvertising = {
                                    scope.launch {
                                        try {
                                            if (isAdvertising) {
                                                advertiser.stopAdvertising()
                                            } else {
                                                advertiser.startAdvertising(roomCode, "550e8400-e29b-41d4-a716-446655440000", "")
                                            }
                                        } catch (e: Exception) {
                                            bluetoothError = "Bluetooth Error: ${e.message}"
                                        }
                                    }
                                },
                                checkAndRequestBluetooth = { checkAndRequestBluetooth(it) },
                                onLeave = { navController.popBackStack(Screen.Home.name, false) }
                            )
                        }
                    }

                                        composable(Screen.InviteBle.name) {
                                            InviteBleScreen(
                                                localOfferSdp = localSdp,
                                                roomCode = roomCode,
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
                             roomCode = roomCode,
                             onPeerSelected = { peer ->
                                 selectedPeer = peer
                                 navController.navigate(Screen.VideoCall.name)
                             },
                             onBack = { 
                                 viewModel.cancel()
                                 navController.popBackStack() 
                             }
                         )
                    }

                    composable(Screen.Dashboard.name) {
                        DashboardScreen(
                            serverHost = com.tej.protojunc.signalingServerHost,
                            serverPort = com.tej.protojunc.signalingServerPort,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                if (isInitializing || isProcessing || isNavigating) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
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
fun SelectionScreen(
    title: String, 
    roomCode: String,
    onRoomCodeChange: (String) -> Unit,
    onQr: () -> Unit, 
    onBle: () -> Unit, 
    onBack: () -> Unit, 
    processing: Boolean = false
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = roomCode,
            onValueChange = { if (it.length <= 5) onRoomCodeChange(it.uppercase()) },
            label = { Text("Room Code (Optional 5-chars)") },
            placeholder = { Text("e.g. APPLE") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !processing
        )
        
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
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { 
                onQr() 
            }, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !processing
        ) { 
            Text("Via BT Socket") 
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { 
                onQr()
            }, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !processing
        ) { 
            Text("Via Online Server") 
        }
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onBack, enabled = !processing) { Text("Back") }
    }
}