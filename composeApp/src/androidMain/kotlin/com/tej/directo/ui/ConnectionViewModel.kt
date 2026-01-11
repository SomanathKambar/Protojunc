package com.tej.directo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tej.directo.webrtc.WebRtcSessionManager
import com.tej.directo.webrtc.WebRtcState
import com.tej.directo.discovery.PeerDiscovered
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.tej.directo.util.SdpMinifier
import com.tej.directo.webrtc.HandshakeStage
import com.tej.directo.discovery.DiscoveryManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.tej.directo.common.ServerHealthCheck
import com.tej.directo.common.AndroidServerDiscovery
import com.tej.directo.signalingServerHost

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    val sessionManager = WebRtcSessionManager()
    
    private val _serverStatus = MutableStateFlow(false)
    val serverStatus: StateFlow<Boolean> = _serverStatus.asStateFlow()

    private val serverDiscovery = AndroidServerDiscovery(application)
    private var healthCheck = ServerHealthCheck(signalingServerHost, com.tej.directo.signalingServerPort)

    init {
        checkServerPeriodically()
        observeServerDiscovery()
        serverDiscovery.startDiscovery()
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
                _serverStatus.value = healthCheck.isServerRunning()
                delay(3000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        serverDiscovery.stopDiscovery()
    }

    fun updateServerConfig(host: String, port: Int) {
        com.tej.directo.signalingServerHost = host
        com.tej.directo.signalingServerPort = port
        healthCheck = ServerHealthCheck(host, port)
        viewModelScope.launch {
            _serverStatus.value = healthCheck.isServerRunning()
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
        }
        _viewModelError.value = null
        _handshakeStage.value = HandshakeStage.IDLE
    }

    fun setHandshakeStage(stage: HandshakeStage) {
        _handshakeStage.value = stage
    }

    fun initiateBleHandshake(discoveryManager: DiscoveryManager, peer: PeerDiscovered, onReady: () -> Unit) {
        if (_isInitializing.value || _isProcessing.value) return
        _isInitializing.value = true
        
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
                    throw IllegalStateException("Decoded SDP is too short: $sdp")
                }

                _handshakeStage.value = HandshakeStage.EXCHANGING_SDP_ANSWER
                sessionManager.handleRemoteDescription(sdp, SessionDescriptionType.Offer)
                
                val answer = sessionManager.createAnswer()
                if (answer == null) throw IllegalStateException("Local Answer generation failed")
                
                val encodedAnswer = SdpMinifier.encodePayload(answer, "ANSWER")
                _localSdp.value = encodedAnswer

                discoveryManager.writeToPeer(peer, encodedAnswer)
                
                _handshakeStage.value = HandshakeStage.GATHERING_ICE_CANDIDATES
                _isInitializing.value = false
                onReady()
            } catch (e: Exception) {
                _handshakeStage.value = HandshakeStage.FAILED
                _isInitializing.value = false
                Logger.e(e) { "Handshake Failure" }
                _viewModelError.value = "Handshake Failed: ${e.message ?: "Unknown Error"}"
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            sessionManager.close()
        }
        sessionManager.reset()
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
        viewModelScope.launch {
            try {
                sessionManager.createPeerConnection()
                val offer = sessionManager.createOffer()
                if (offer == null) throw IllegalStateException("Offer generation failed")
                
                _localSdp.value = offer.let { SdpMinifier.encodePayload(it, "OFFER") }
                _isInitializing.value = false
                _isProcessing.value = false
                if (_localSdp.value != null) onReady()
            } catch (e: Exception) {
                _isInitializing.value = false
                _isProcessing.value = false
                _viewModelError.value = "Invite Error: ${e.message}"
            }
        }
    }

    fun handleOfferScanned(encodedOffer: String) {
        if (_isInitializing.value || _isProcessing.value) return
        _isInitializing.value = true
        viewModelScope.launch {
            try {
                val (offerSdp, _) = SdpMinifier.decodePayload(encodedOffer)
                if (offerSdp == "DECODE_ERROR") throw IllegalStateException("Invalid QR/Manual Code")
                
                sessionManager.createPeerConnection()
                sessionManager.handleRemoteDescription(offerSdp, SessionDescriptionType.Offer)
                val answer = sessionManager.createAnswer()
                if (answer == null) throw IllegalStateException("Answer generation failed")
                
                _localSdp.value = SdpMinifier.encodePayload(answer, "ANSWER")
                _isInitializing.value = false
            } catch (e: Exception) {
                _isInitializing.value = false
                Logger.e(e) { "Offer Processing Failure" }
                _viewModelError.value = "Handshake Error: ${e.message}"
            }
        }
    }

    fun handleAnswerScanned(encodedAnswer: String, onConnected: () -> Unit) {
        if (_isInitializing.value || _isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val (answerSdp, _) = SdpMinifier.decodePayload(encodedAnswer)
                if (answerSdp == "DECODE_ERROR") throw IllegalStateException("Invalid Answer Code")
                
                sessionManager.handleRemoteDescription(answerSdp, SessionDescriptionType.Answer)
                _isProcessing.value = false
                onConnected()
            } catch (e: Exception) {
                _isProcessing.value = false
                Logger.e(e) { "Answer Processing Failure" }
                _viewModelError.value = "Connection Error: ${e.message}"
            }
        }
    }

    fun endCall() {
        viewModelScope.launch {
            sessionManager.close()
        }
        _localSdp.value = null
    }
}