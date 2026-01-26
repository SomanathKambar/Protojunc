package com.tej.protojunc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tej.protojunc.webrtc.WebRtcSessionManager
import com.tej.protojunc.webrtc.WebRtcState
import com.tej.protojunc.discovery.PeerDiscovered
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.tej.protojunc.signaling.util.SdpMinifier
import com.tej.protojunc.core.models.SignalingMessage as ServerSignalingMessage
import com.tej.protojunc.webrtc.HandshakeStage
import com.tej.protojunc.discovery.DiscoveryManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.tej.protojunc.common.ServerHealthCheck
import com.tej.protojunc.common.AndroidServerDiscovery
import com.tej.protojunc.signalingServerHost
import com.tej.protojunc.common.IdentityManager
import com.tej.protojunc.common.DataStoreIdentityManager
import com.tej.protojunc.common.createDataStore
import com.tej.protojunc.common.UserIdentity
import com.tej.protojunc.discovery.PresenceManager
import com.tej.protojunc.discovery.PresenceManagerImpl
import com.tej.protojunc.discovery.AndroidPresenceAdvertiser
import com.tej.protojunc.discovery.AndroidPresenceScanner
import android.bluetooth.BluetoothManager
import android.content.Context

import com.tej.protojunc.vault.FileTransferManager
import com.tej.protojunc.vault.AndroidFileTransferManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.tej.protojunc.core.signaling.webrtc.SignalingManager
import com.tej.protojunc.models.IceCandidateModel

class ConnectionViewModel(application: Application) : AndroidViewModel(application), KoinComponent {
    val sessionManager = WebRtcSessionManager()
    private val signalingManager: SignalingManager by inject()
    
    val identityManager: IdentityManager = DataStoreIdentityManager(createDataStore(application))
    private val _userIdentity = MutableStateFlow<UserIdentity?>(null)
    val userIdentity: StateFlow<UserIdentity?> = _userIdentity.asStateFlow()

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val presenceManager: PresenceManager = PresenceManagerImpl(
        identityManager = identityManager,
        advertiser = AndroidPresenceAdvertiser(application, bluetoothManager.adapter),
        scanner = AndroidPresenceScanner(application, bluetoothManager.adapter),
        scope = viewModelScope
    )
    val nearbyPeers = presenceManager.nearbyPeers

    val fileTransferManager: FileTransferManager = AndroidFileTransferManager(application)

    private val _serverStatus = MutableStateFlow(false)
    val serverStatus: StateFlow<Boolean> = _serverStatus.asStateFlow()

    private val serverDiscovery = AndroidServerDiscovery(application)
    private var healthCheck = ServerHealthCheck(signalingServerHost, com.tej.protojunc.signalingServerPort)

    init {
        checkServerPeriodically()
        observeServerDiscovery()
        serverDiscovery.startDiscovery()
        loadIdentity()
        startFileServer()
        
        // Start Signaling over WebSocket Server
        viewModelScope.launch {
            try {
                signalingManager.connect()
                
                // Listen for incoming signaling
                launch {
                    signalingManager.incomingMessages.collect { msg ->
                        handleSignalingMessage(msg)
                    }
                }
                
                // Listen for local candidates to send to server
                launch {
                    sessionManager.iceCandidates.collect { candidate ->
                        signalingManager.send(
                            ServerSignalingMessage(
                                type = ServerSignalingMessage.Type.ICE_CANDIDATE,
                                iceCandidate = candidate.sdp,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Signaling Server Init Failed" }
            }
        }
    }

    private suspend fun handleSignalingMessage(message: ServerSignalingMessage) {
        try {
            when (message.type) {
                ServerSignalingMessage.Type.OFFER -> {
                    Logger.i { "Received Remote Offer via Server" }
                    sessionManager.createPeerConnection()
                    sessionManager.handleRemoteDescription(message.sdp!!, SessionDescriptionType.Offer)
                    val answer = sessionManager.createAnswer()
                    if (answer != null) {
                        signalingManager.send(ServerSignalingMessage(type = ServerSignalingMessage.Type.ANSWER, sdp = answer))
                    }
                    _handshakeStage.value = HandshakeStage.COMPLETED
                }
                ServerSignalingMessage.Type.ANSWER -> {
                    Logger.i { "Received Remote Answer via Server" }
                    sessionManager.handleRemoteDescription(message.sdp!!, SessionDescriptionType.Answer)
                    _handshakeStage.value = HandshakeStage.COMPLETED
                }
                ServerSignalingMessage.Type.ICE_CANDIDATE -> {
                    if (message.iceCandidate != null && message.sdpMLineIndex != null) {
                        Logger.d { "Received Remote ICE Candidate via Server" }
                        sessionManager.addIceCandidate(
                            IceCandidateModel(
                                sdp = message.iceCandidate!!,
                                sdpMid = message.sdpMid,
                                sdpMLineIndex = message.sdpMLineIndex!!
                            )
                        )
                    }
                }
                else -> {
                    Logger.d { "Unhandled signaling message type: ${message.type}" }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Signaling Handle Error" }
        }
    }

    /**
     * Initiates a WebRTC call using the centralized Signaling Server.
     */
    fun startOnlineCall() {
        viewModelScope.launch {
            try {
                Logger.i { "Initiating Online Call..." }
                _handshakeStage.value = HandshakeStage.INITIALIZING_HARDWARE
                sessionManager.createPeerConnection()
                val offer = sessionManager.createOffer()
                if (offer != null) {
                    signalingManager.send(ServerSignalingMessage(type = ServerSignalingMessage.Type.OFFER, sdp = offer))
                    _handshakeStage.value = HandshakeStage.EXCHANGING_SDP_OFFER
                }
            } catch (e: Exception) {
                Logger.e(e) { "Start Online Call Error" }
                _handshakeStage.value = HandshakeStage.FAILED
            }
        }
    }

    private fun startFileServer() {
        viewModelScope.launch {
            fileTransferManager.startFileServer()
        }
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            val identity = identityManager.getOrCreateIdentity(com.tej.protojunc.deviceName)
            _userIdentity.value = identity
        }
    }

    private fun observeServerDiscovery() {
        viewModelScope.launch {
            serverDiscovery.discoveredServer.collectLatest { info ->
                if (info != null) {
                    Logger.i { "Auto-discovered server at ${info.host}:${info.port}" }
                    updateServerConfig(info.host, info.port)
                }
            }
        }
    }

    private fun checkServerPeriodically() {
        viewModelScope.launch {
            while (true) {
                try {
                    val isRunning = healthCheck.isServerRunning()
                    Logger.d { "Server Health Check: $isRunning" }
                    if (_serverStatus.value && !isRunning) {
                        // Server went down while we were potentially using it
                        if (_handshakeStage.value != HandshakeStage.IDLE && 
                            _handshakeStage.value != HandshakeStage.COMPLETED &&
                            _handshakeStage.value != HandshakeStage.FAILED) {
                            Logger.w { "Signaling server disappeared during active stage: ${_handshakeStage.value}" }
                            _viewModelError.value = "Signaling Server connection lost"
                            _handshakeStage.value = HandshakeStage.FAILED
                        }
                    }
                    _serverStatus.value = isRunning
                } catch (e: Exception) {
                    Logger.e(e) { "Health check failed" }
                    _serverStatus.value = false
                }
                delay(5000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        serverDiscovery.stopDiscovery()
        viewModelScope.launch {
            sessionManager.close()
        }
    }

    fun updateServerConfig(host: String, port: Int) {
        com.tej.protojunc.signalingServerHost = host
        com.tej.protojunc.signalingServerPort = port
        healthCheck = ServerHealthCheck(host, port)
        viewModelScope.launch {
            try {
                _serverStatus.value = healthCheck.isServerRunning()
            } catch (e: Exception) {
                _serverStatus.value = false
            }
        }
    }
    
    private val _localSdp = MutableStateFlow<String?>(null)
    val localSdp: StateFlow<String?> = _localSdp.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _handshakeStage = MutableStateFlow(HandshakeStage.IDLE)
    val handshakeStage: StateFlow<HandshakeStage> = _handshakeStage.asStateFlow()

    val connectionState = sessionManager.connectionState
    val progressMessage = sessionManager.progressMessage

    private val _viewModelError = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = kotlinx.coroutines.flow.combine(
        sessionManager.errorMessage,
        _viewModelError
    ) { sessionError, vmError ->
        sessionError ?: vmError
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), null)

    fun clearError() {
        viewModelScope.launch {
            sessionManager.reset()
            sessionManager.close()
        }
        _viewModelError.value = null
        _handshakeStage.value = HandshakeStage.IDLE
        _isInitializing.value = false
        _isProcessing.value = false
    }

    fun setHandshakeStage(stage: HandshakeStage) {
        _handshakeStage.value = stage
    }

    fun initiateBleHandshake(discoveryManager: DiscoveryManager, peer: PeerDiscovered, onReady: () -> Unit) {
        if (_isInitializing.value || _isProcessing.value) return
        _isInitializing.value = true
        _viewModelError.value = null
        
        viewModelScope.launch {
            try {
                _handshakeStage.value = HandshakeStage.INITIALIZING_HARDWARE
                sessionManager.createPeerConnection()
                
                _handshakeStage.value = HandshakeStage.STARTING_DISCOVERY
                val encodedOffer = discoveryManager.connectToPeer(peer)
                
                if (encodedOffer.isEmpty()) {
                    throw IllegalStateException("Received 0 bytes from peer via BLE")
                }

                _handshakeStage.value = HandshakeStage.EXCHANGING_SDP_OFFER
                val (sdp, _) = SdpMinifier.decodePayload(encodedOffer)
                
                if (sdp.length < 50) {
                    throw IllegalStateException("Decoded SDP is too short or invalid")
                }

                _handshakeStage.value = HandshakeStage.EXCHANGING_SDP_ANSWER
                sessionManager.handleRemoteDescription(sdp, SessionDescriptionType.Offer)
                
                val answer = sessionManager.createAnswer()
                if (answer == null) throw IllegalStateException("Local Answer generation failed")
                
                val identity = _userIdentity.value ?: throw IllegalStateException("Identity not loaded")
                val encodedAnswer = SdpMinifier.encodePayload(answer, ServerSignalingMessage.Type.ANSWER, identity.deviceId)
                _localSdp.value = encodedAnswer

                discoveryManager.writeToPeer(peer, encodedAnswer)
                
                _handshakeStage.value = HandshakeStage.GATHERING_ICE_CANDIDATES
                _isInitializing.value = false
                onReady()
            } catch (e: Exception) {
                Logger.e(e) { "Handshake Failure" }
                if (e is CancellationException) throw e
                _handshakeStage.value = HandshakeStage.FAILED
                _isInitializing.value = false
                _viewModelError.value = "Handshake Failed: ${e.message ?: "Unknown Error"}"
                sessionManager.close()
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            sessionManager.close()
            sessionManager.reset()
        }
        _localSdp.value = null
        _isInitializing.value = false
        _isProcessing.value = false
        _viewModelError.value = null
        _handshakeStage.value = HandshakeStage.IDLE
    }

    fun prepareInvite(onReady: () -> Unit) {
        if (_isInitializing.value || _isProcessing.value) return
        _isInitializing.value = true
        _isProcessing.value = true
        _viewModelError.value = null
        
        viewModelScope.launch {
            try {
                sessionManager.createPeerConnection()
                val offer = sessionManager.createOffer()
                if (offer == null) throw IllegalStateException("Offer generation failed")
                
                val identity = _userIdentity.value ?: throw IllegalStateException("Identity not loaded")
                _localSdp.value = offer.let { SdpMinifier.encodePayload(it, ServerSignalingMessage.Type.OFFER, identity.deviceId) }
                _isInitializing.value = false
                _isProcessing.value = false
                if (_localSdp.value != null) onReady()
            } catch (e: Exception) {
                Logger.e(e) { "Invite Error" }
                if (e is CancellationException) throw e
                _isInitializing.value = false
                _isProcessing.value = false
                _viewModelError.value = "Invite Error: ${e.message}"
                sessionManager.close()
            }
        }
    }

    fun handleOfferScanned(encodedOffer: String) {
        if (_isInitializing.value || _isProcessing.value) return
        _isInitializing.value = true
        _viewModelError.value = null
        
        viewModelScope.launch {
            try {
                val (offerSdp, _) = SdpMinifier.decodePayload(encodedOffer)
                if (offerSdp == "DECODE_ERROR" || offerSdp.length < 50) {
                    throw IllegalStateException("Invalid QR/Manual Code")
                }
                
                sessionManager.createPeerConnection()
                sessionManager.handleRemoteDescription(offerSdp, SessionDescriptionType.Offer)
                val answer = sessionManager.createAnswer()
                if (answer == null) throw IllegalStateException("Answer generation failed")
                
                val identity = _userIdentity.value ?: throw IllegalStateException("Identity not loaded")
                _localSdp.value = SdpMinifier.encodePayload(answer, ServerSignalingMessage.Type.ANSWER, identity.deviceId)
                _isInitializing.value = false
            } catch (e: Exception) {
                Logger.e(e) { "Offer Processing Failure" }
                if (e is CancellationException) throw e
                _isInitializing.value = false
                _viewModelError.value = "Handshake Error: ${e.message}"
                sessionManager.close()
            }
        }
    }

    fun handleAnswerScanned(encodedAnswer: String, onConnected: () -> Unit) {
        if (_isInitializing.value || _isProcessing.value) return
        _isProcessing.value = true
        _viewModelError.value = null
        
        viewModelScope.launch {
            try {
                val (answerSdp, _) = SdpMinifier.decodePayload(encodedAnswer)
                if (answerSdp == "DECODE_ERROR" || answerSdp.length < 50) {
                    throw IllegalStateException("Invalid Answer Code")
                }
                
                sessionManager.handleRemoteDescription(answerSdp, SessionDescriptionType.Answer)
                _isProcessing.value = false
                onConnected()
            } catch (e: Exception) {
                Logger.e(e) { "Answer Processing Failure" }
                if (e is CancellationException) throw e
                _isProcessing.value = false
                _viewModelError.value = "Connection Error: ${e.message}"
            }
        }
    }

    fun endCall() {
        viewModelScope.launch {
            sessionManager.close()
        }
        _localSdp.value = null
        _handshakeStage.value = HandshakeStage.IDLE
    }
}