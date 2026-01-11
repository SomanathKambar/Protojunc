package com.tej.protojunc.webrtc

import com.shepeliev.webrtckmp.*
import com.tej.protojunc.models.IceCandidateModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger

enum class WebRtcState {
    Idle, Initializing, Ready, Connecting, Connected, Failed, Closed
}

class WebRtcSessionManager {
    private var peerConnection: PeerConnection? = null
    private var managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
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

    suspend fun createPeerConnection(videoEnabled: Boolean = true) = withContext(Dispatchers.Main) {
        try {
            close() 
            reset()
            
            _progressMessage.value = "Initializing Engine..."
            _connectionState.value = WebRtcState.Initializing
            
            val config = RtcConfiguration(
                iceServers = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
            )
            
            Logger.d { "Creating PeerConnection..." }
            val pc = PeerConnection(config)
            peerConnection = pc

            // Initialize Camera & Mic
            _progressMessage.value = "Starting Media..."
            try {
                Logger.d { "Requesting User Media (Audio: true, Video: $videoEnabled)..." }
                val stream = MediaDevices.getUserMedia(audio = true, video = videoEnabled)
                
                if (videoEnabled) {
                    val videoTrack = stream.videoTracks.firstOrNull()
                    if (videoTrack != null) {
                        localVideoTrack.value = videoTrack
                    }
                    stream.videoTracks.forEach { track -> 
                        Logger.d { "Adding video track: ${track.id}" }
                        pc.addTrack(track, stream) 
                    }
                }

                stream.audioTracks.forEach { track -> 
                    Logger.d { "Adding audio track: ${track.id}" }
                    pc.addTrack(track, stream) 
                }
            } catch (mediaError: Exception) {
                Logger.e(mediaError) { "Failed to get user media" }
                _errorMessage.value = "Camera/Mic Error: ${mediaError.message}"
                // Don't throw, maybe we can still proceed with receive-only or something, 
                // but usually WebRTC needs local tracks for P2P if expected.
            }

            pc.onIceCandidate
                .onEach { candidate ->
                    if (candidate != null) {
                        Logger.d { "New ICE Candidate: ${candidate.sdpMid}" }
                        _iceCandidates.emit(candidate.toModel())
                    }
                }
                .launchIn(managerScope)

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
                .launchIn(managerScope)

            pc.onTrack
                .onEach { event ->
                    val track = event.track
                    if (track != null) {
                        Logger.d { "Remote track received: ${track.id} kind: ${track.kind}" }
                        if (track is VideoTrack) {
                            remoteVideoTrack.value = track
                        }
                    }
                }
                .launchIn(managerScope)
            
            _progressMessage.value = "Ready"
            _connectionState.value = WebRtcState.Ready
        } catch (e: Exception) {
            Logger.e(e) { "Engine Error" }
            _errorMessage.value = "Engine Error: ${e.message}"
            _connectionState.value = WebRtcState.Failed
            throw e
        }
    }

    suspend fun addIceCandidate(model: IceCandidateModel) = withContext(Dispatchers.Main) {
        try {
            peerConnection?.addIceCandidate(createIceCandidate(model))
        } catch (e: Exception) {
            Logger.e(e) { "Error adding ICE candidate" }
        }
    }

    suspend fun createOffer(): String? = withContext(Dispatchers.Main) {
        val pc = peerConnection ?: return@withContext null
        return@withContext try {
            val offer = pc.createOffer(OfferAnswerOptions())
            pc.setLocalDescription(offer)
            
            var attempts = 0
            while (pc.iceGatheringState != IceGatheringState.Complete && attempts < 20) {
                delay(100)
                attempts++
            }
            pc.localDescription?.sdp
        } catch (e: Exception) {
            Logger.e(e) { "Error creating offer" }
            _errorMessage.value = "Offer Error: ${e.message}"
            null
        }
    }

    suspend fun handleRemoteDescription(sdp: String, type: SessionDescriptionType) = withContext(Dispatchers.Main) {
        try {
            val description = SessionDescription(type, sdp)
            peerConnection?.setRemoteDescription(description)
        } catch (e: Exception) {
            Logger.e(e) { "Error handling remote description" }
            _errorMessage.value = "SDP Error: ${e.message}"
            throw e
        }
    }

    suspend fun createAnswer(): String? = withContext(Dispatchers.Main) {
        val pc = peerConnection ?: return@withContext null
        return@withContext try {
            val answer = pc.createAnswer(OfferAnswerOptions())
            pc.setLocalDescription(answer)
            
            var attempts = 0
            while (pc.iceGatheringState != IceGatheringState.Complete && attempts < 20) {
                delay(100)
                attempts++
            }
            pc.localDescription?.sdp
        } catch (e: Exception) {
            Logger.e(e) { "Error creating answer" }
            _errorMessage.value = "Answer Error: ${e.message}"
            null
        }
    }

    suspend fun close() = withContext(Dispatchers.Main) {
        try {
            managerScope.coroutineContext.cancelChildren()
            peerConnection?.close()
        } catch (e: Exception) {
            Logger.e(e) { "Error closing PeerConnection" }
        } finally {
            peerConnection = null
            remoteVideoTrack.value = null
            localVideoTrack.value = null
            _connectionState.value = WebRtcState.Closed
        }
    }
}
