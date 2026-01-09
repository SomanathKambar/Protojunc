package com.tej.directo.util

import com.tej.directo.models.SignalingPayload
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object SignalingEncoder {
    
    private const val START_MARKER = "D1:"
    private const val END_MARKER = ":Z"

    fun encode(payload: SignalingPayload): String {
        // format: D1:{TYPE}|{SDP}:Z
        val raw = "${payload.type}|${payload.sdp}"
        val encoded = Base64.encode(raw.encodeToByteArray())
        return "$START_MARKER$encoded$END_MARKER"
    }

    fun decode(encoded: String): SignalingPayload {
        val cleaned = encoded.trim()
        
        if (!cleaned.startsWith(START_MARKER) || !cleaned.endsWith(END_MARKER)) {
            val reason = when {
                !cleaned.startsWith(START_MARKER) -> "Missing START marker (Data Corrupted)"
                !cleaned.endsWith(END_MARKER) -> "Missing END marker (Data Truncated via BLE)"
                else -> "Invalid Envelope"
            }
            throw IllegalArgumentException("$reason. Received ${cleaned.length} bytes.")
        }

        // Strip markers
        val base64Part = cleaned.removePrefix(START_MARKER).removeSuffix(END_MARKER)
        
        val decodedRaw = try {
            Base64.decode(base64Part).decodeToString()
        } catch (e: Exception) {
            throw IllegalArgumentException("Base64 Decode Failed. Content: ${base64Part.take(10)}...")
        }

        if (decodedRaw.contains("|")) {
            val parts = decodedRaw.split("|", limit = 2)
            return SignalingPayload(
                sdp = parts[1],
                type = parts[0],
                iceCandidates = emptyList(),
                timestamp = 0
            )
        } else {
            throw IllegalArgumentException("Invalid Handshake Format: Missing Type Delimiter")
        }
    }
}
