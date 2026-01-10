package com.tej.directo.webrtc

import com.shepeliev.webrtckmp.*
import com.tej.directo.models.IceCandidateModel
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
    
    private val _iceCandidates = MutableSharedFlow<IceCandidateModel>(extraBufferCapacity = 64)
    val iceCandidates = _iceCandidates.asSharedFlow()

    fun reset() {
        _errorMessage.value = null
        _progressMessage.value = null
        _connectionState.value = WebRtcState.Idle
        remoteVideoTrack.value = null
        localVideoTrack.value = null
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
            try {
                Logger.d { "Requesting User Media (Selfie)..." }
                val stream = MediaDevices.getUserMedia(audio = true, video = true)
                
                val videoTrack = stream.videoTracks.firstOrNull()
                if (videoTrack != null) {
                    localVideoTrack.value = videoTrack
                }

                stream.audioTracks.forEach { track -> pc.addTrack(track, stream) }
                stream.videoTracks.forEach { track -> pc.addTrack(track, stream) }
            } catch (mediaError: Exception) {
                Logger.e(mediaError) { "Failed to get user media" }
                _errorMessage.value = "Camera Error: ${mediaError.message}"
            }

            pc.onIceCandidate
                .onEach { candidate ->
                    _iceCandidates.emit(candidate.toModel())
                }
                .launchIn(scope)

            pc.onConnectionStateChange
                .onEach { state ->
                    Logger.d { "PeerConnection State Change: $state" }
                    _connectionState.value = when(state) {
                        PeerConnectionState.New -> WebRtcState.Ready
                        PeerConnectionState.Connecting -> WebRtcState.Connecting
                        PeerConnectionState.Connected -> WebRtcState.Connected
                        PeerConnectionState.Failed -> WebRtcState.Failed
                        PeerConnectionState.Disconnected -> WebRtcState.Failed
                        PeerConnectionState.Closed -> WebRtcState.Closed
                        else -> _connectionState.value
                    }
                }
                .launchIn(scope)

            pc.onTrack
                .onEach { event ->
                    val track = event.track
                    if (track is VideoTrack) {
                        remoteVideoTrack.value = track
                    }
                }
                .launchIn(scope)
            
            _progressMessage.value = "Ready"
            _connectionState.value = WebRtcState.Ready
        } catch (e: Exception) {
            Logger.e(e) { "Engine Error" }
            _errorMessage.value = "Engine Error: ${e.message}"
            _connectionState.value = WebRtcState.Failed
        }
    }

    suspend fun addIceCandidate(model: IceCandidateModel) {
        peerConnection?.addIceCandidate(createIceCandidate(model))
    }

    suspend fun createOffer(): String? {
        val pc = peerConnection ?: return null
        val offer = pc.createOffer(OfferAnswerOptions())
        pc.setLocalDescription(offer)
        
        // Wait for ICE gathering to finish or time out
        var attempts = 0
        while (pc.iceGatheringState != IceGatheringState.Complete && attempts < 20) {
            delay(100)
            attempts++
        }
        
        return pc.localDescription?.sdp
    }

    suspend fun handleRemoteDescription(sdp: String, type: SessionDescriptionType) {
        try {
            val description = SessionDescription(type, sdp)
            peerConnection?.setRemoteDescription(description)
        } catch (e: Exception) {
            _errorMessage.value = "Native SDP Error: ${e.message}"
            throw e
        }
    }

    suspend fun createAnswer(): String? {
        val pc = peerConnection ?: return null
        val answer = pc.createAnswer(OfferAnswerOptions())
        pc.setLocalDescription(answer)
        
        var attempts = 0
        while (pc.iceGatheringState != IceGatheringState.Complete && attempts < 20) {
            delay(100)
            attempts++
        }
        
        return pc.localDescription?.sdp
    }

    fun close() {
        peerConnection?.close()
        peerConnection = null
        _connectionState.value = WebRtcState.Closed
    }
}
