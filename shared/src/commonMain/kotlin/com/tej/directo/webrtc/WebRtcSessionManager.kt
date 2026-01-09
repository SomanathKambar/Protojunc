package com.tej.directo.webrtc

import com.shepeliev.webrtckmp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger

enum class WebRtcState {
    Idle, Initializing, Ready, Connecting, Connected, Failed, Closed
}

class WebRtcSessionManager {
    private var peerConnection: PeerConnection? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _connectionState = MutableStateFlow(WebRtcState.Idle)
    val connectionState = _connectionState.asStateFlow()

    private val _progressMessage = MutableStateFlow<String?>(null)
    val progressMessage = _progressMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    
    private val _iceCandidates = MutableStateFlow<List<IceCandidate>>(emptyList())

    fun reset() {
        _errorMessage.value = null
        _progressMessage.value = null
        _connectionState.value = WebRtcState.Idle
        remoteVideoTrack.value = null
        localVideoTrack.value = null
        _iceCandidates.value = emptyList()
    }

    suspend fun createPeerConnection() {
        try {
            close()
            reset()
            _progressMessage.value = "Initializing Engine..."
            _connectionState.value = WebRtcState.Initializing
            
            val config = RtcConfiguration(
                iceServers = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
            )
            val pc = PeerConnection(config)
            peerConnection = pc

            scope.launch {
                pc.onIceCandidate.collect { candidate ->
                    _iceCandidates.value = _iceCandidates.value + candidate
                }
            }

            scope.launch {
                pc.onConnectionStateChange.collect { state ->
                    _connectionState.value = when(state) {
                        PeerConnectionState.Connecting -> {
                            _progressMessage.value = "Connecting..."
                            WebRtcState.Connecting
                        }
                        PeerConnectionState.Connected -> {
                            _progressMessage.value = "Link Established"
                            WebRtcState.Connected
                        }
                        PeerConnectionState.Failed -> {
                            _errorMessage.value = "Connection Lost"
                            WebRtcState.Failed
                        }
                        else -> _connectionState.value
                    }
                }
            }

            scope.launch {
                pc.onTrack.collect { event ->
                    if (event.track is VideoTrack) {
                        remoteVideoTrack.value = event.track as VideoTrack
                    }
                }
            }
            
            _progressMessage.value = "Ready"
            _connectionState.value = WebRtcState.Ready
        } catch (e: Exception) {
            _errorMessage.value = "Engine Error: ${e.message}"
            _connectionState.value = WebRtcState.Failed
        }
    }

    suspend fun createOffer(): String? {
        val pc = peerConnection ?: return null
        _progressMessage.value = "Generating Offer..."
        val offer = pc.createOffer(OfferAnswerOptions())
        
        // Ensure the connection wasn't closed while creating the offer
        if (peerConnection != pc) return null
        
        pc.setLocalDescription(offer)
        
        // Instant return if possible, or wait very briefly
        _progressMessage.value = "Finalizing..."
        // If the library supports it, waiting for gathering to start is enough
        delay(300) 

        // Ensure the connection wasn't closed during the delay
        if (peerConnection != pc) return null
        
        _progressMessage.value = "Ready"
        return pc.localDescription?.sdp
    }

    suspend fun handleRemoteDescription(sdp: String, type: SessionDescriptionType) {
        _progressMessage.value = "Processing..."
        peerConnection?.setRemoteDescription(SessionDescription(type, sdp))
    }

    suspend fun createAnswer(): String? {
        val pc = peerConnection ?: return null
        _progressMessage.value = "Generating Answer..."
        val answer = pc.createAnswer(OfferAnswerOptions())
        
        // Ensure the connection wasn't closed while creating the answer
        if (peerConnection != pc) return null
        
        pc.setLocalDescription(answer)
        
        delay(300)

        // Ensure the connection wasn't closed during the delay
        if (peerConnection != pc) return null
        
        _progressMessage.value = "Ready"
        return pc.localDescription?.sdp
    }

    fun close() {
        peerConnection?.close()
        peerConnection = null
        _connectionState.value = WebRtcState.Closed
    }
}
