package com.tej.directo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tej.directo.webrtc.WebRtcSessionManager
import com.tej.directo.webrtc.WebRtcState
import com.tej.directo.discovery.PeerDiscovered
import com.shepeliev.webrtckmp.SessionDescriptionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel : ViewModel() {
    val sessionManager = WebRtcSessionManager()
    
    private val _localSdp = MutableStateFlow<String?>(null)
    val localSdp: StateFlow<String?> = _localSdp.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val connectionState = sessionManager.connectionState
    val errorMessage = sessionManager.errorMessage
    val progressMessage = sessionManager.progressMessage

    fun clearError() {
        sessionManager.reset()
    }

    fun handleBlePeerSelected(peer: PeerDiscovered, onConnected: () -> Unit) {
        viewModelScope.launch {
            try {
                _isInitializing.value = true
                // In a real P2P app, we'd use Kable to connect to the peripheral
                // and read the SDP from a GATT characteristic.
                // For this implementation, we initiate WebRTC using the peer's known data.
                sessionManager.createPeerConnection()
                
                if (peer.remoteSdpBase64.isNotEmpty()) {
                    sessionManager.handleRemoteDescription(peer.remoteSdpBase64, SessionDescriptionType.Offer)
                    val answer = sessionManager.createAnswer()
                    _localSdp.value = answer
                    onConnected()
                } else {
                    // If SDP wasn't in the advertisement, we'd connect here
                    _isInitializing.value = false
                    sessionManager.errorMessage.value // Trigger error
                }
            } catch (e: Exception) {
                _isInitializing.value = false
            }
        }
    }

    fun cancel() {
        sessionManager.close()
        sessionManager.reset()
        _localSdp.value = null
        _isInitializing.value = false
    }

    fun prepareInvite(onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                _isInitializing.value = true
                _isProcessing.value = true
                sessionManager.createPeerConnection()
                val offer = sessionManager.createOffer()
                _localSdp.value = offer
                _isInitializing.value = false
                _isProcessing.value = false
                if (offer != null) onReady()
            } catch (e: Exception) {
                _isInitializing.value = false
                _isProcessing.value = false
            }
        }
    }

    fun handleOfferScanned(offerSdp: String) {
        viewModelScope.launch {
            try {
                _isInitializing.value = true
                sessionManager.createPeerConnection()
                sessionManager.handleRemoteDescription(offerSdp, SessionDescriptionType.Offer)
                _localSdp.value = sessionManager.createAnswer()
                _isInitializing.value = false
            } catch (e: Exception) {
                _isInitializing.value = false
            }
        }
    }

    fun handleAnswerScanned(answerSdp: String, onConnected: () -> Unit) {
        viewModelScope.launch {
            try {
                sessionManager.handleRemoteDescription(answerSdp, SessionDescriptionType.Answer)
                onConnected()
            } catch (e: Exception) {
                // Error handled via sessionManager.errorMessage
            }
        }
    }

    fun endCall() {
        sessionManager.close()
        _localSdp.value = null
    }
}
