package com.tej.directo.util

import com.tej.directo.models.SignalingPayload
import com.tej.directo.models.IceCandidateModel

object SdpMinifier {
    
    private val TOKENS = mapOf(
        "a=candidate:" to "§C",
        "a=fingerprint:sha-256 " to "§F",
        "a=setup:" to "§S",
        "a=mid:" to "§M",
        "a=ice-ufrag:" to "§U",
        "a=ice-pwd:" to "§P",
        "a=rtpmap:" to "§R",
        "a=fmtp:" to "§T",
        "a=ssrc:" to "§X",
        "IN IP4 " to "§I",
        " UDP/TLS/RTP/SAVPF " to "§B"
    )

    /**
     * Reduces the size of the SDP by removing redundant lines and codecs.
     */
    fun minify(sdp: String): String {
        val lines = sdp.split("\n", "\r").filter { it.isNotBlank() }
        val minifiedLines = mutableListOf<String>()
        
        var keepCandidateCount = 0
        val maxCandidates = 3
        
        val keptPayloads = setOf("111", "96", "97")

        for (line in lines) {
            val trimmed = line.trim()
            
            val shouldKeep = when {
                trimmed.startsWith("v=") || trimmed.startsWith("o=") || 
                trimmed.startsWith("s=") || trimmed.startsWith("t=") ||
                trimmed.startsWith("c=") -> true
                
                trimmed.startsWith("m=") -> true
                
                trimmed.startsWith("a=fingerprint:") || trimmed.startsWith("a=setup:") ||
                trimmed.startsWith("a=mid:") || trimmed.startsWith("a=group:BUNDLE") ||
                trimmed.startsWith("a=ice-ufrag:") || trimmed.startsWith("a=ice-pwd:") ||
                trimmed.startsWith("a=rtcp-mux") || trimmed.startsWith("a=msid-semantic:") -> true
                
                trimmed.startsWith("a=rtpmap:111") || trimmed.startsWith("a=fmtp:111") -> true
                trimmed.startsWith("a=rtpmap:96") || trimmed.startsWith("a=fmtp:96") -> true
                trimmed.startsWith("a=rtpmap:97") || trimmed.startsWith("a=fmtp:97") -> true
                
                trimmed.startsWith("a=ssrc:") || trimmed.startsWith("a=msid:") -> true
                
                trimmed.startsWith("a=candidate:") -> {
                    if (keepCandidateCount < maxCandidates) {
                        keepCandidateCount++
                        true
                    } else false
                }
                else -> false
            }
            
            if (shouldKeep) {
                var processedLine = if (trimmed.startsWith("m=")) {
                    sanitizeMLine(trimmed, keptPayloads)
                } else {
                    trimmed
                }
                
                // Token replacement to save more space
                for ((key, value) in TOKENS) {
                    processedLine = processedLine.replace(key, value)
                }
                
                minifiedLines.add(processedLine)
            }
        }
        
        return minifiedLines.joinToString("\r\n") + "\r\n"
    }

    private fun sanitizeMLine(line: String, keptPayloads: Set<String>): String {
        val parts = line.split(" ")
        if (parts.size < 4) return line
        
        val newParts = mutableListOf<String>()
        newParts.add(parts[0]) // m=audio
        newParts.add(parts[1]) // port
        newParts.add(parts[2]) // proto
        
        for (i in 3 until parts.size) {
            if (keptPayloads.contains(parts[i])) {
                newParts.add(parts[i])
            }
        }
        
        if (newParts.size == 3) newParts.add(parts[3])
        return newParts.joinToString(" ")
    }

    private fun expand(minifiedSdp: String): String {
        var result = minifiedSdp
        for ((key, value) in TOKENS) {
            result = result.replace(value, key)
        }
        return result
    }

    /**
     * Converts a raw SDP string into an encoded SignalingPayload string.
     */
    fun encodePayload(sdp: String, type: String): String {
        val minified = minify(sdp)
        val payload = SignalingPayload(
            sdp = minified,
            type = type,
            iceCandidates = emptyList(),
            timestamp = 0
        )
        return SignalingEncoder.encode(payload)
    }

    /**
     * Decodes an encoded SignalingPayload string back into a raw SDP.
     */
    fun decodePayload(encoded: String): Pair<String, String> {
        return try {
            val payload = SignalingEncoder.decode(encoded)
            expand(payload.sdp) to payload.type
        } catch (e: Exception) {
            "DECODE_ERROR" to "UNKNOWN"
        }
    }
}
