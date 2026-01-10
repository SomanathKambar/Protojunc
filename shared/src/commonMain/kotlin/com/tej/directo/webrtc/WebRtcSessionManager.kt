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

            // Initialize Camera & Mic
            _progressMessage.value = "Starting Media..."
            val stream = MediaDevices.getUserMedia(audio = true, video = true)
            stream.audioTracks.forEach { track -> pc.addTrack(track, stream) }
            stream.videoTracks.forEach { track ->
                pc.addTrack(track, stream)
                localVideoTrack.value = track
            }

            scope.launch {
                pc.onIceCandidate.collect { candidate ->
                    _iceCandidates.value = _iceCandidates.value + candidate
                    Logger.d { "New ICE Candidate received" }
                }
            }

            scope.launch {
                pc.onConnectionStateChange.collect { state ->
                    Logger.d { "Connection State Change: $state" }
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
                            _errorMessage.value = "Connection Failed"
                            WebRtcState.Failed
                        }
                        PeerConnectionState.Disconnected -> {
                            WebRtcState.Failed
                        }
                        else -> _connectionState.value
                    }
                }
            }

            scope.launch {
                pc.onTrack.collect { event ->
                    val track = event.track
                    Logger.d { "Received Remote Track: ${track?.kind}" }
                    if (track is VideoTrack) {
                        remoteVideoTrack.value = track
                    }
                }
            }
            
            _progressMessage.value = "Ready"
            _connectionState.value = WebRtcState.Ready
        } catch (e: Exception) {
            Logger.e(e) { "Engine Error" }
            _errorMessage.value = "Engine Error: ${e.message}"
            _connectionState.value = WebRtcState.Failed
        }
    }

    private suspend fun PeerConnection.waitForIceGathering() {
        if (iceGatheringState == IceGatheringState.Complete) return
        
        // Wait up to 2 seconds for ICE gathering
        var attempts = 0
        while (iceGatheringState != IceGatheringState.Complete && attempts < 20) {
            delay(100)
            attempts++
            // If we already have a few candidates (Host + SRFLX), 1 second is enough to proceed
            if (attempts > 10 && _iceCandidates.value.size >= 2) break
        }
        Logger.d { "ICE Gathering finished/timed out after ${attempts * 100}ms. Candidates: ${_iceCandidates.value.size}" }
    }

    suspend fun createOffer(): String? {
        val pc = peerConnection ?: return null
        _progressMessage.value = "Generating Offer..."
        val offer = pc.createOffer(OfferAnswerOptions())
        pc.setLocalDescription(offer)
        
        _progressMessage.value = "Gathering Candidates..."
        pc.waitForIceGathering()

        if (peerConnection != pc) return null
        
        _progressMessage.value = "Ready"
        return pc.localDescription?.sdp
    }

    suspend fun handleRemoteDescription(sdp: String, type: SessionDescriptionType) {
        _progressMessage.value = "Validating SDP..."
        
        // RCA: WebRTC native layer often returns Null if the string doesn't contain 'v=0' or 'm='
        if (!sdp.contains("v=0") || !sdp.contains("m=")) {
            val error = "Malformed SDP: Essential markers missing. Content: ${sdp.take(30)}..."
            _errorMessage.value = error
            throw IllegalArgumentException(error)
        }

        _progressMessage.value = "Applying Remote Description..."
        try {
            val description = SessionDescription(type, sdp)
            peerConnection?.setRemoteDescription(description)
        } catch (e: Exception) {
            val error = "Native SDP Error: ${e.message}"
            _errorMessage.value = error
            throw e
        }
    }

    suspend fun createAnswer(): String? {
        val pc = peerConnection ?: return null
        _progressMessage.value = "Generating Answer..."
        val answer = pc.createAnswer(OfferAnswerOptions())
        pc.setLocalDescription(answer)
        
        _progressMessage.value = "Gathering Candidates..."
        pc.waitForIceGathering()

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
