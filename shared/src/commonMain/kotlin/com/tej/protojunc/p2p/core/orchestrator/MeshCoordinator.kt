package com.tej.protojunc.p2p.core.orchestrator

import com.tej.protojunc.webrtc.WebRtcSessionManager
import com.tej.protojunc.core.models.SignalingMessage
import com.tej.protojunc.signaling.SignalingClient
import com.tej.protojunc.discovery.DiscoveryManager
import com.tej.protojunc.models.IceCandidateModel
import com.tej.protojunc.models.PairedDeviceRepository
import com.tej.protojunc.models.PairedDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger

/**
 * Orchestrates a multi-peer mesh network for group calls.
 * Phase 3: High-Value Features.
 */
class MeshCoordinator(
    val localId: String,
    private val isHost: Boolean,
    private val scope: CoroutineScope,
    val signalingClient: SignalingClient,
    private val discoveryManager: DiscoveryManager,
    private val pairedDeviceRepository: PairedDeviceRepository,
    private val onManualConnect: suspend (String) -> Boolean
) {
    private val _peers = MutableStateFlow<Map<String, WebRtcSessionManager>>(emptyMap())
    val peers = _peers.asStateFlow()
    
    private val _pendingInvites = MutableStateFlow<Set<String>>(emptySet())
    val pendingInvites = _pendingInvites.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()
    
    val pairedDevices: List<PairedDevice> = pairedDeviceRepository.getPairedDevices()
    
    val discoveredPeers = discoveryManager.meshDiscoveredPeers

    data class ChatMessage(val senderId: String, val content: String, val timestamp: Long)

    init {
        observeSignaling()
        // Always start discovery to populate UI list
        scope.launch { 
            try {
                discoveryManager.startDiscovery()
            } catch (e: Exception) {
                Logger.e { "Discovery start failed: ${e.message}" }
            }
        }
        
        if (isHost) {
            startAutoInviting()
        }
    }
    
    fun sendChatMessage(content: String) {
        scope.launch {
            try {
                val msg = SignalingMessage(
                    type = SignalingMessage.Type.MESSAGE,
                    senderId = localId,
                    payload = content
                )
                signalingClient.sendMessage(msg)
                // Add local copy immediately for UI responsiveness, but we could wait if we had ACKs
                _chatMessages.update { it + ChatMessage(localId, content, com.tej.protojunc.getCurrentTimeMillis()) }
            } catch (e: Exception) {
                Logger.e { "Failed to send chat message: ${e.message}" }
            }
        }
    }

    fun startVoiceCall() {
        scope.launch {
            _peers.value.values.forEach { session ->
                session.createPeerConnection(videoEnabled = false)
                val offer = session.createOffer()
                signalingClient.sendMessage(SignalingMessage(
                    type = SignalingMessage.Type.VOICE_CALL,
                    sdp = offer,
                    senderId = localId
                ))
            }
        }
    }

    fun startVideoCall() {
        scope.launch {
            _peers.value.values.forEach { session ->
                session.createPeerConnection(videoEnabled = true)
                val offer = session.createOffer()
                signalingClient.sendMessage(SignalingMessage(
                    type = SignalingMessage.Type.VIDEO_CALL,
                    sdp = offer,
                    senderId = localId
                ))
            }
        }
    }

    fun connectToPairedDevice(device: PairedDevice) {
        scope.launch { 
            _pendingInvites.update { it + device.id }
            onManualConnect(device.id) 
            _pendingInvites.update { it - device.id }
        }
    }

    private fun startAutoInviting() {
        scope.launch {
            // Auto-invite any new peer found in the list
            discoveryManager.meshDiscoveredPeers.collect { peers ->
                peers.forEach { peer ->
                    if (!_peers.value.containsKey(peer.id) && !_pendingInvites.value.contains(peer.id)) {
                        Logger.i { "Mesh: Auto-inviting discovered peer: ${peer.name} (${peer.id})" }
                        invitePeer(peer.id)
                    }
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
        val peerId = message.senderId ?: return
        
        if (message.type == SignalingMessage.Type.MESSAGE) {
            _chatMessages.update { it + ChatMessage(peerId, message.payload ?: "", com.tej.protojunc.getCurrentTimeMillis()) }
            return
        }

        val session = _peers.value[peerId] ?: createPeerSession(peerId)

        when (message.type) {
            SignalingMessage.Type.VOICE_CALL,
            SignalingMessage.Type.VIDEO_CALL -> {
                Logger.i { "Mesh: Incoming Call from $peerId" }
                // We don't automatically accept here, the UI should show an alert
                // but we need to ensure the session is ready if accepted.
                // Signaling messages flow through signalingClient.messages which UI collects.
            }
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

    suspend fun invitePeer(peerId: String) = withContext(Dispatchers.IO) {
        if (_pendingInvites.value.contains(peerId)) return@withContext
        _pendingInvites.update { it + peerId }
        
        try {
            // Ensure transport is connected first
            val connected = onManualConnect(peerId)
            if (!connected) {
                Logger.e { "Failed to connect to $peerId. Aborting invite." }
                _pendingInvites.update { it - peerId }
                return@withContext
            }
            
            val session = _peers.value[peerId] ?: createPeerSession(peerId)
            
            // PeerConnection creation can be slow, keep it off main
            session.createPeerConnection()
            val offer = session.createOffer()
            
            signalingClient.sendMessage(SignalingMessage(
                type = SignalingMessage.Type.OFFER,
                sdp = offer,
                senderId = localId
            ))
        } finally {
            _pendingInvites.update { it - peerId }
        }
    }

    fun leaveMesh() {
        _peers.value.values.forEach { 
            scope.launch { it.close() }
        }
        _peers.value = emptyMap()
    }
}
