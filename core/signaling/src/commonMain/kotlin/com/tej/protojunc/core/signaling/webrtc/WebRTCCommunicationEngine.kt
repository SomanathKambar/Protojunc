package com.tej.protojunc.core.signaling.webrtc

import com.tej.protojunc.core.common.ConnectionState
import com.tej.protojunc.core.models.SignalingMessage
import com.tej.protojunc.core.signaling.CommunicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * WebRTC implementation of CommunicationEngine.
 * Uses webrtc-kmp to provide shared WebRTC logic across platforms.
 */
class WebRTCCommunicationEngine(
    private val signalingManager: SignalingManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CommunicationEngine {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private var peerConnection: PeerConnection? = null

    init {
        // Listen to signaling messages from the server
        coroutineScope.launch {
            signalingManager.incomingMessages.collect { message ->
                handleSignalingMessage(message)
            }
        }
    }

    override suspend fun connect() {
        _connectionState.value = ConnectionState.Connecting
        try {
            signalingManager.connect()
            initializePeerConnection()
            createOffer()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Failed to connect WebRTC", e.message)
            Logger.e(e) { "WebRTC connection failed" }
        }
    }

    private fun initializePeerConnection() {
        val config = RtcConfiguration(
            iceServers = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
        )
        
        val pc = PeerConnection(config)
        peerConnection = pc

        // Handle ICE Candidates
        pc.onIceCandidate
            .onEach { candidate ->
                if (candidate != null) {
                    coroutineScope.launch {
                        signalingManager.send(
                            SignalingMessage(
                                type = "candidate",
                                candidate = candidate.candidate,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex
                            )
                        )
                    }
                }
            }
            .launchIn(coroutineScope)

        // Handle Connection State
        pc.onConnectionStateChange
            .onEach { state ->
                Logger.d { "PeerConnection state changed: $state" }
                _connectionState.value = when (state) {
                    PeerConnectionState.Connected -> ConnectionState.Connected
                    PeerConnectionState.Connecting -> ConnectionState.Connecting
                    PeerConnectionState.Failed -> ConnectionState.Error("WebRTC Failed")
                    PeerConnectionState.Disconnected -> ConnectionState.Reconnecting(1)
                    else -> _connectionState.value
                }
            }
            .launchIn(coroutineScope)
            
        // Handle Data Channel
        pc.onDataChannel
            .onEach { channel ->
                Logger.d { "Data channel received: ${channel.label}" }
            }
            .launchIn(coroutineScope)
    }

    private suspend fun createOffer() {
        peerConnection?.let { pc ->
            try {
                val offer = pc.createOffer(OfferAnswerOptions())
                pc.setLocalDescription(offer)
                signalingManager.send(SignalingMessage(type = "offer", sdp = offer.sdp))
            } catch (e: Exception) {
                Logger.e(e) { "Failed to create offer" }
            }
        }
    }

    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        try {
            when (message.type) {
                "offer" -> {
                    peerConnection?.let { pc ->
                        pc.setRemoteDescription(SessionDescription(SessionDescriptionType.Offer, message.sdp!!))
                        val answer = pc.createAnswer(OfferAnswerOptions())
                        pc.setLocalDescription(answer)
                        signalingManager.send(SignalingMessage(type = "answer", sdp = answer.sdp))
                    }
                }
                "answer" -> {
                    peerConnection?.setRemoteDescription(SessionDescription(SessionDescriptionType.Answer, message.sdp!!))
                }
                "candidate" -> {
                    peerConnection?.addIceCandidate(
                        IceCandidate(
                            sdpMid = message.sdpMid ?: "",
                            sdpMLineIndex = message.sdpMLineIndex ?: 0,
                            candidate = message.candidate!!
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error handling signaling message: ${message.type}" }
        }
    }

    override suspend fun disconnect() {
        peerConnection?.close()
        peerConnection = null
        signalingManager.close()
        _connectionState.value = ConnectionState.Idle
    }

    override suspend fun sendData(data: ByteArray) {
        // Implement DataChannel sending if needed
    }
}