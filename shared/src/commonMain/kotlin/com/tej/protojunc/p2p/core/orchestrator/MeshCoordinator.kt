package com.tej.protojunc.p2p.core.orchestrator

import com.tej.protojunc.webrtc.WebRtcSessionManager
import com.tej.protojunc.signaling.SignalingMessage
import com.tej.protojunc.signaling.SignalingClient
import com.tej.protojunc.discovery.DiscoveryManager
import com.tej.protojunc.models.IceCandidateModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger

/**
 * Orchestrates a multi-peer mesh network for group calls.
 * Phase 3: High-Value Features.
 */
class MeshCoordinator(
    private val localId: String,
    private val isHost: Boolean,
    private val scope: CoroutineScope,
    private val signalingClient: SignalingClient,
    private val discoveryManager: DiscoveryManager
) {
    private val _peers = MutableStateFlow<Map<String, WebRtcSessionManager>>(emptyMap())
    val peers = _peers.asStateFlow()

    init {
        observeSignaling()
        if (isHost) {
            startAutoDiscovery()
        }
    }

    private fun startAutoDiscovery() {
        scope.launch {
            discoveryManager.observeNearbyPeers().collect { peer ->
                if (!_peers.value.containsKey(peer.id)) {
                    Logger.i { "Mesh: Auto-inviting discovered peer: ${peer.name} (${peer.id})" }
                    invitePeer(peer.id)
                }
            }
        }
    }

    private fun observeSignaling() {
        scope.launch {
            signalingClient.messages.collect { message ->
                if (message.senderId == localId) return@collect
                handlePeerMessage(message)
            }
        }
    }

    private suspend fun handlePeerMessage(message: SignalingMessage) {
        val peerId = message.senderId
        val session = _peers.value[peerId] ?: createPeerSession(peerId)

        when (message.type) {
            SignalingMessage.Type.OFFER -> {
                Logger.i { "Mesh: Received Offer from $peerId" }
                session.createPeerConnection()
                session.handleRemoteDescription(message.sdp!!, com.shepeliev.webrtckmp.SessionDescriptionType.Offer)
                val answer = session.createAnswer()
                signalingClient.sendMessage(SignalingMessage(
                    type = SignalingMessage.Type.ANSWER,
                    sdp = answer,
                    senderId = localId
                ))
            }
            SignalingMessage.Type.ANSWER -> {
                Logger.i { "Mesh: Received Answer from $peerId" }
                session.handleRemoteDescription(message.sdp!!, com.shepeliev.webrtckmp.SessionDescriptionType.Answer)
            }
            SignalingMessage.Type.ICE_CANDIDATE -> {
                session.addIceCandidate(com.tej.protojunc.models.IceCandidateModel(
                    sdp = message.iceCandidate!!,
                    sdpMid = message.sdpMid,
                    sdpMLineIndex = message.sdpMLineIndex ?: 0
                ))
            }
            else -> { /* Other types like BYE handled per session */ }
        }
    }

    private fun createPeerSession(peerId: String): WebRtcSessionManager {
        val manager = WebRtcSessionManager()
        _peers.update { it + (peerId to manager) }
        
        // Broadcast ICE candidates for this specific peer
        scope.launch {
            manager.iceCandidates.collect { model ->
                signalingClient.sendMessage(SignalingMessage(
                    type = SignalingMessage.Type.ICE_CANDIDATE,
                    iceCandidate = model.sdp,
                    sdpMid = model.sdpMid,
                    sdpMLineIndex = model.sdpMLineIndex,
                    senderId = localId
                ))
            }
        }
        
        return manager
    }

    suspend fun invitePeer(peerId: String) {
        val session = _peers.value[peerId] ?: createPeerSession(peerId)
        session.createPeerConnection()
        val offer = session.createOffer()
        signalingClient.sendMessage(SignalingMessage(
            type = SignalingMessage.Type.OFFER,
            sdp = offer,
            senderId = localId
        ))
    }

    fun leaveMesh() {
        _peers.value.values.forEach { 
            scope.launch { it.close() }
        }
        _peers.value = emptyMap()
    }
}
