package com.tej.directo.p2p.webrtc

import com.tej.directo.p2p.webrtc.`interface`.WebRTCPeerConnectionFactory
import co.touchlab.kermit.Logger

class IOSWebRTCPeerConnectionFactory : WebRTCPeerConnectionFactory {
    
    init {
        Logger.d { "Initializing iOS WebRTC Factory" }
    }

    override fun createPeerConnection() {
         Logger.d { "Creating iOS Peer Connection" }
    }

    override fun dispose() {
        Logger.d { "Disposing iOS WebRTC Factory" }
    }
}
