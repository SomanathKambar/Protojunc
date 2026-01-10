package com.tej.directo.util

import com.tej.directo.models.SignalingPayload
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer

@OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
object SignalingEncoder {
    
    private val protoBuf = ProtoBuf {
        encodeDefaults = true
    }

    /**
     * Encodes payload: Protobuf -> Gzip -> Base64
     */
    fun encode(payload: SignalingPayload): String {
        val bytes = protoBuf.encodeToByteArray(SignalingPayload.serializer(), payload)
        
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.write(bytes) }
        val compressedBytes = buffer.readByteArray()
        
        return Base64.UrlSafe.encode(compressedBytes)
    }

    /**
     * Decodes payload: Base64 -> Gzip -> Protobuf
     */
    fun decode(encoded: String): SignalingPayload {
        val cleaned = encoded.trim()
        if (cleaned.isEmpty()) throw IllegalArgumentException("Received empty payload")

        return try {
            val compressedBytes = Base64.UrlSafe.decode(cleaned)
            
            val buffer = Buffer()
            buffer.write(compressedBytes)
            val decompressedBytes = GzipSource(buffer).buffer().use { it.readByteArray() }
            
            protoBuf.decodeFromByteArray(SignalingPayload.serializer(), decompressedBytes)
        } catch (e: Exception) {
            // Fallback for non-compressed raw SDP (backward compatibility during transition)
            if (cleaned.startsWith("v=0") || cleaned.contains("§")) {
                val type = if (cleaned.contains("a=setup:active") || cleaned.contains("§Sactive")) "ANSWER" else "OFFER"
                SignalingPayload(cleaned, type, emptyList(), 0)
            } else {
                throw e
            }
        }
    }
}
