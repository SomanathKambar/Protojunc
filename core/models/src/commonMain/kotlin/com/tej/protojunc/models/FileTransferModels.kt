package com.tej.protojunc.models

import kotlinx.serialization.Serializable

@Serializable
data class FileMetadata(
    val name: String,
    val size: Long,
    val type: String
)

@Serializable
sealed class TransferStatus {
    @Serializable
    object Idle : TransferStatus()
    @Serializable
    data class Progress(val bytesTransferred: Long, val totalBytes: Long) : TransferStatus()
    @Serializable
    object Completed : TransferStatus()
    @Serializable
    data class Error(val message: String) : TransferStatus()
}
