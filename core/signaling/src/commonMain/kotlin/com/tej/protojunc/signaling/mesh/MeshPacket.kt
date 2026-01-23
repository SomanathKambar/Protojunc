package com.tej.protojunc.signaling.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MeshPacket(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val senderId: String,
    @ProtoNumber(3) val targetId: String, // "BROADCAST" for flood
    @ProtoNumber(4) val type: Type,
    @ProtoNumber(5) val payload: ByteArray,
    @ProtoNumber(6) val hopCount: Int = 0,
    @ProtoNumber(7) val ttl: Int = 5,
    @ProtoNumber(8) val timestamp: Long
) {
    @Serializable
    enum class Type {
        @ProtoNumber(0) UNKNOWN,
        @ProtoNumber(1) SIGNALING,
        @ProtoNumber(2) DATA,
        @ProtoNumber(3) HELLO, // For neighbor discovery
        @ProtoNumber(4) ACK
    }
}
