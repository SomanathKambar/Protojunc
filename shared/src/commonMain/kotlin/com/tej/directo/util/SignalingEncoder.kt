package com.tej.directo.util

import com.tej.directo.models.SignalingPayload
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object SignalingEncoder {
    private val proto = ProtoBuf { encodeDefaults = false }

    fun encode(payload: SignalingPayload): String {
        val bytes = proto.encodeToByteArray(payload)
        // ToDo : In a production app, apply Zlib compression here
        return Base64.encode(bytes)
    }

    fun decode(base64: String): SignalingPayload {
        val bytes = Base64.decode(base64)
        return proto.decodeFromByteArray(bytes)
    }
}
