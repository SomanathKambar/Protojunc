package com.tej.directo.p2p.core.orchestrator

import com.tej.directo.webrtc.WebRtcSessionManager
import com.tej.directo.p2p.core.signaling.SignalingClient
import com.tej.directo.p2p.core.signaling.SignalingMessage
import com.tej.directo.models.IceCandidateModel
import com.shepeliev.webrtckmp.SessionDescriptionType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger

class CallSessionOrchestrator(
    val webRtcManager: WebRtcSessionManager,
    private val scope: CoroutineScope
) {
    private var activeSignalingClient: SignalingClient? = null
    
    fun setSignalingClient(client: SignalingClient) {
        val oldClient = activeSignalingClient
        activeSignalingClient = client
        
        scope.launch {
            oldClient?.disconnect()
            client.connect()
        }
        
        client.messages
            .onEach { handleSignalingMessage(it) }
            .launchIn(scope)
    }

    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        when (message.type) {
            SignalingMessage.Type.OFFER -> {
                webRtcManager.handleRemoteDescription(message.sdp!!, SessionDescriptionType.Offer)
                val answer = webRtcManager.createAnswer()
                activeSignalingClient?.sendMessage(SignalingMessage(
                    type = SignalingMessage.Type.ANSWER,
                    sdp = answer,
                    senderId = "local"
                ))
            }
            SignalingMessage.Type.ANSWER -> {
                webRtcManager.handleRemoteDescription(message.sdp!!, SessionDescriptionType.Answer)
            }
            SignalingMessage.Type.ICE_CANDIDATE -> {
                webRtcManager.addIceCandidate(IceCandidateModel(
                    sdp = message.iceCandidate!!,
                    sdpMid = message.sdpMid,
                    sdpMLineIndex = message.sdpMLineIndex ?: 0
                ))
            }
            SignalingMessage.Type.BYE -> {
                webRtcManager.close()
            }
        }
    }

    suspend fun startCall(isHost: Boolean) {
        webRtcManager.createPeerConnection()
        
        if (isHost) {
            val offer = webRtcManager.createOffer()
            activeSignalingClient?.sendMessage(SignalingMessage(
                type = SignalingMessage.Type.OFFER,
                sdp = offer,
                senderId = "local"
            ))
        }
        
        // Observe local ICE candidates and send them
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
    }
}