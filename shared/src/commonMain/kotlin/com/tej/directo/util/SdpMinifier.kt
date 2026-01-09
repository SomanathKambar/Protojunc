package com.tej.directo.util

import com.tej.directo.models.SignalingPayload
import com.tej.directo.models.IceCandidateModel

object SdpMinifier {
    
    /**
     * Reduces the size of the SDP by removing redundant lines and codecs.
     */
    fun minify(sdp: String): String {
        val lines = sdp.split("\n", "\r")
        val minifiedLines = mutableListOf<String>()
        
        // Ensure standard headers are present (WebRTC native requirement)
        var hasV = false
        var hasO = false
        var hasS = false
        var hasT = false
        var hasC = false

        var keepCandidateCount = 0
        val maxCandidates = 1 
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            val shouldKeep = when {
                trimmed.startsWith("v=") -> { hasV = true; true }
                trimmed.startsWith("o=") -> { hasO = true; true }
                trimmed.startsWith("s=") -> { hasS = true; true }
                trimmed.startsWith("t=") -> { hasT = true; true }
                trimmed.startsWith("c=") -> { hasC = true; true }
                trimmed.startsWith("m=") -> true
                trimmed.startsWith("a=fingerprint:") -> true
                trimmed.startsWith("a=setup:") -> true
                trimmed.startsWith("a=mid:") -> true
                trimmed.startsWith("a=group:BUNDLE") -> true
                trimmed.startsWith("a=msid-semantic:") -> true
                trimmed.startsWith("a=ice-ufrag:") -> true
                trimmed.startsWith("a=ice-pwd:") -> true
                trimmed.startsWith("a=rtpmap:") -> true 
                trimmed.startsWith("a=fmtp:") -> true
                trimmed.startsWith("a=candidate:") -> {
                    if (keepCandidateCount < maxCandidates) {
                        keepCandidateCount++
                        true
                    } else false
                }
                else -> false
            }
            
            if (shouldKeep) {
                minifiedLines.add(trimmed)
            }
        }
        
        // Inject missing standard headers if they were stripped
        if (!hasV) minifiedLines.add(0, "v=0")
        if (!hasT) minifiedLines.add("t=0 0")
        if (!hasS) minifiedLines.add("s=-")
        if (!hasC) minifiedLines.add("c=IN IP4 0.0.0.0")
        
        // IMPORTANT: WebRTC requires CRLF (\r\n)
        return minifiedLines.joinToString("\r\n")
    }

    private fun isEssentialCodec(line: String): Boolean {
        val l = line.lowercase()
        // Keep only one audio (Opus) and one video (VP8) to save massive space
        return l.contains("opus/48000/2") || l.contains("vp8/90000")
    }

    /**
     * Converts a raw SDP string into an encoded SignalingPayload string.
     */
    fun encodePayload(sdp: String, type: String): String {
        val minified = minify(sdp)
        val payload = SignalingPayload(
            sdp = minified,
            type = type,
            iceCandidates = emptyList(), // Candidates are already in the SDP
            timestamp = 0 // Not strictly needed for P2P
        )
        return SignalingEncoder.encode(payload)
    }

    /**
     * Decodes an encoded SignalingPayload string back into a raw SDP.
     */
    fun decodePayload(encoded: String): Pair<String, String> {
        val payload = SignalingEncoder.decode(encoded)
        return payload.sdp to payload.type
    }
}
