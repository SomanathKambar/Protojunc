package com.tej.directo.p2p.core.orchestrator

import com.tej.directo.webrtc.WebRtcSessionManager
import com.tej.directo.signaling.*
import com.tej.directo.models.IceCandidateModel
import com.shepeliev.webrtckmp.SessionDescriptionType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger
import com.tej.directo.webrtc.HandshakeStage

class CallSessionOrchestrator(
    val webRtcManager: WebRtcSessionManager,
    private val scope: CoroutineScope,
    private val onHandshakeStageChanged: (HandshakeStage) -> Unit = {}
) {
    private var activeSignalingClient: SignalingClient? = null
    private var connectionJob: Job? = null
    private var stateObservationJob: Job? = null
    private var messageObservationJob: Job? = null
    
    private val _signalingState = MutableStateFlow(SignalingState.IDLE)
    val signalingState = _signalingState.asStateFlow()
    
    private val _chatMessages = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val chatMessages = _chatMessages.asSharedFlow()

    private var isHostRole: Boolean = false
    private var currentMode: SignalingMessage.Type = SignalingMessage.Type.VIDEO_CALL
    
    fun setSignalingClient(client: SignalingClient) {
        val oldClient = activeSignalingClient
        activeSignalingClient = client
        
        // Cancel previous jobs
        connectionJob?.cancel()
        stateObservationJob?.cancel()
        messageObservationJob?.cancel()

        // 1. START HARDWARE IMMEDIATELY
        scope.launch {
            onHandshakeStageChanged(HandshakeStage.INITIALIZING_HARDWARE)
            webRtcManager.createPeerConnection(videoEnabled = currentMode == SignalingMessage.Type.VIDEO_CALL)
            onHandshakeStageChanged(HandshakeStage.STARTING_DISCOVERY)
        }

        // 2. START CONNECTION (Non-blocking loop)
        connectionJob = scope.launch {
            oldClient?.disconnect()
            client.connect()
        }

        // 3. OBSERVE STATE & TRIGGER JOIN
        stateObservationJob = client.state
            .onEach { state ->
                _signalingState.value = state 
                if (state == SignalingState.CONNECTED) {
                    onHandshakeStageChanged(HandshakeStage.PEER_FOUND)
                    Logger.i { "Signaling Connected. Sending JOIN..." }
                    scope.launch {
                        client.sendMessage(SignalingMessage(type = SignalingMessage.Type.MESSAGE, sdp = "👋 Ready to Call!", senderId = "local"))
                        client.sendMessage(SignalingMessage(type = SignalingMessage.Type.JOIN, senderId = "local"))
                    }
                } else if (state == SignalingState.ERROR) {
                    onHandshakeStageChanged(HandshakeStage.RECONNECTING)
                }
            }
            .launchIn(scope)
        
        messageObservationJob = client.messages
            .onEach { 
                Logger.d { "Orchestrator handling message: ${it.type}" }
                handleSignalingMessage(it) 
            }
            .launchIn(scope)
    }

    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        try {
            when (message.type) {
                SignalingMessage.Type.JOIN -> {
                    Logger.i { "Peer joined room. Current state: ${_signalingState.value}, Role: ${if (isHostRole) "Host" else "Joiner"}" }
                    if (isHostRole) {
                        if (webRtcManager.connectionState.value == com.tej.directo.webrtc.WebRtcState.Ready || 
                            webRtcManager.connectionState.value == com.tej.directo.webrtc.WebRtcState.Idle) {
                            Logger.i { "Host starting handshake with new peer..." }
                            scope.launch {
                                activeSignalingClient?.sendMessage(SignalingMessage(type = currentMode, senderId = "local"))
                                startCallInternal()
                            }
                        } else {
                            Logger.d { "Host already in call or connecting, ignoring redundant JOIN" }
                        }
                    } else {
                        Logger.i { "Joiner sees new peer, re-announcing presence..." }
                        scope.launch {
                            activeSignalingClient?.sendMessage(SignalingMessage(type = SignalingMessage.Type.JOIN, senderId = "local"))
                        }
                    }
                }
                SignalingMessage.Type.VIDEO_CALL, 
                SignalingMessage.Type.VOICE_CALL -> {
                    Logger.i { "Remote requested mode: ${message.type}" }
                    currentMode = message.type
                    if (!isHostRole) {
                        val videoEnabled = currentMode == SignalingMessage.Type.VIDEO_CALL
                        scope.launch(Dispatchers.Default) {
                            webRtcManager.createPeerConnection(videoEnabled = videoEnabled)
                        }
                    }
                }
                SignalingMessage.Type.OFFER -> {
                    Logger.i { "Received Offer, creating answer..." }
                    onHandshakeStageChanged(HandshakeStage.EXCHANGING_SDP_OFFER)
                    if (webRtcManager.connectionState.value == com.tej.directo.webrtc.WebRtcState.Idle) {
                         webRtcManager.createPeerConnection(videoEnabled = currentMode == SignalingMessage.Type.VIDEO_CALL)
                    }
                    webRtcManager.handleRemoteDescription(message.sdp!!, SessionDescriptionType.Offer)
                    onHandshakeStageChanged(HandshakeStage.EXCHANGING_SDP_ANSWER)
                    val answer = webRtcManager.createAnswer()
                    activeSignalingClient?.sendMessage(SignalingMessage(
                        type = SignalingMessage.Type.ANSWER,
                        sdp = answer,
                        senderId = "local"
                    ))
                    onHandshakeStageChanged(HandshakeStage.GATHERING_ICE_CANDIDATES)
                }
                SignalingMessage.Type.ANSWER -> {
                    Logger.i { "Received Answer, setting remote description..." }
                    onHandshakeStageChanged(HandshakeStage.EXCHANGING_SDP_ANSWER)
                    webRtcManager.handleRemoteDescription(message.sdp!!, SessionDescriptionType.Answer)
                    onHandshakeStageChanged(HandshakeStage.WAITING_FOR_REMOTE_VIDEO)
                }
                SignalingMessage.Type.ICE_CANDIDATE -> {
                    Logger.d { "Received ICE Candidate" }
                    webRtcManager.addIceCandidate(IceCandidateModel(
                        sdp = message.iceCandidate!!,
                        sdpMid = message.sdpMid,
                        sdpMLineIndex = message.sdpMLineIndex ?: 0
                    ))
                }
                SignalingMessage.Type.BYE -> {
                    Logger.i { "Received BYE, terminating session..." }
                    scope.launch(Dispatchers.Default) {
                        webRtcManager.close()
                        onHandshakeStageChanged(HandshakeStage.IDLE)
                    }
                }
                SignalingMessage.Type.MESSAGE -> {
                    Logger.i { "Received Text Message" }
                    message.sdp?.let { _chatMessages.tryEmit(it) }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error handling signaling message: ${message.type}" }
            onHandshakeStageChanged(HandshakeStage.FAILED)
        }
    }

    suspend fun sendTextMessage(text: String) {
        activeSignalingClient?.sendMessage(SignalingMessage(
            type = SignalingMessage.Type.MESSAGE,
            sdp = text,
            senderId = "local"
        ))
    }

    suspend fun startCall(isHost: Boolean) {
        startCall(isHost, SignalingMessage.Type.VIDEO_CALL)
    }

    suspend fun startCall(isHost: Boolean, mode: SignalingMessage.Type) {
        this.isHostRole = isHost
        this.currentMode = mode
        if (!isHost) {
            onHandshakeStageChanged(HandshakeStage.STARTING_DISCOVERY)
        } else {
            onHandshakeStageChanged(HandshakeStage.PEER_FOUND)
        }
    }

    private suspend fun startCallInternal() {
        val videoEnabled = currentMode == SignalingMessage.Type.VIDEO_CALL
        onHandshakeStageChanged(HandshakeStage.INITIALIZING_HARDWARE)
        webRtcManager.createPeerConnection(videoEnabled = videoEnabled)
        
        onHandshakeStageChanged(HandshakeStage.EXCHANGING_SDP_OFFER)
        val offer = webRtcManager.createOffer()
        activeSignalingClient?.sendMessage(SignalingMessage(
            type = SignalingMessage.Type.OFFER,
            sdp = offer,
            senderId = "local"
        ))
        onHandshakeStageChanged(HandshakeStage.GATHERING_ICE_CANDIDATES)
        
        webRtcManager.iceCandidates
            .onEach { model ->
                activeSignalingClient?.sendMessage(SignalingMessage(
                    type = SignalingMessage.Type.ICE_CANDIDATE,
                    iceCandidate = model.sdp,
                    sdpMid = model.sdpMid,
                    sdpMLineIndex = model.sdpMLineIndex,
                    senderId = "local"
                ))
            }
            .launchIn(scope)
            
        webRtcManager.connectionState
            .onEach { 
                if (it == com.tej.directo.webrtc.WebRtcState.Connected) {
                    onHandshakeStageChanged(HandshakeStage.LINK_ESTABLISHED)
                } else if (it == com.tej.directo.webrtc.WebRtcState.Failed) {
                    onHandshakeStageChanged(HandshakeStage.FAILED)
                }
            }
            .launchIn(scope)
    }

    suspend fun endCall() = withContext(Dispatchers.Default) {
        Logger.i { "Ending call and notifying peer..." }
        try {
            activeSignalingClient?.sendMessage(SignalingMessage(type = SignalingMessage.Type.BYE, senderId = "local"))
            activeSignalingClient?.disconnect()
        } catch (e: Exception) {
            Logger.e(e) { "Error during endCall signaling" }
        } finally {
            webRtcManager.close()
            onHandshakeStageChanged(HandshakeStage.IDLE)
        }
    }
}
