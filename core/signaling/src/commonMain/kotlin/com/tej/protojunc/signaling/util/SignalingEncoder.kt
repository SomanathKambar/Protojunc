package com.tej.protojunc.signaling.util

import com.tej.protojunc.core.models.SignalingMessage
import kotlinx.serialization.json.Json

object SignalingEncoder {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(message: SignalingMessage): String {
        return json.encodeToString(SignalingMessage.serializer(), message)
    }

    fun decode(encoded: String): SignalingMessage {
        return json.decodeFromString(SignalingMessage.serializer(), encoded)
    }
}