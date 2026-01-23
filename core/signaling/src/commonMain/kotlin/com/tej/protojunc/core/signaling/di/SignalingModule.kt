package com.tej.protojunc.core.signaling.di

import com.tej.protojunc.core.signaling.CommunicationEngine
import com.tej.protojunc.core.signaling.ble.BleCommunicationEngine
import com.tej.protojunc.core.signaling.webrtc.SignalingManager
import com.tej.protojunc.core.signaling.webrtc.WebRTCCommunicationEngine
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Common signaling module.
 * @param host The signaling server host.
 * @param port The signaling server port.
 */
fun createSignalingModule(host: String, port: Int) = module {
    
    single {
        HttpClient {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single { 
        SignalingManager(
            client = get(),
            signalingUrl = "ws://$host:$port/signaling/default"
        ) 
    }

    factory<CommunicationEngine>(named("WebRTC")) {
        WebRTCCommunicationEngine(get())
    }

    factory<CommunicationEngine>(named("BLE")) {
        BleCommunicationEngine()
    }
}