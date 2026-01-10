package com.tej.directo.util

import com.tej.directo.models.SignalingPayload
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray

@OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
object SignalingEncoder {
    
    private val protoBuf = ProtoBuf {
        encodeDefaults = true
    }

    fun encode(payload: SignalingPayload): String {
        val bytes = protoBuf.encodeToByteArray(SignalingPayload.serializer(), payload)
        return Base64.UrlSafe.encode(bytes)
    }

    fun decode(encoded: String): SignalingPayload {
        val cleaned = encoded.trim()
        
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("Received empty payload")
        }

        return try {
            val bytes = Base64.UrlSafe.decode(cleaned)
            protoBuf.decodeFromByteArray(SignalingPayload.serializer(), bytes)
        } catch (e: Exception) {
            // Fallback for raw SDP if needed, but primarily handle encoded payload
            if (cleaned.startsWith("v=0")) {
                val type = if (cleaned.contains("a=setup:active")) "ANSWER" else "OFFER"
                SignalingPayload(cleaned, type, emptyList(), 0)
            } else {
                throw e
            }
        }
    }
}
