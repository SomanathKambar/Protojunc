package com.tej.protojunc.vault

import com.tej.protojunc.models.FileMetadata
import com.tej.protojunc.models.TransferStatus
import kotlinx.coroutines.flow.StateFlow

interface FileTransferManager {
    val incomingTransfers: StateFlow<List<FileMetadata>>
    val transferStatus: StateFlow<TransferStatus>
    val isServerRunning: StateFlow<Boolean>

        suspend fun sendFile(ip: String, filePath: String)

        suspend fun startFileServer()

        fun stopFileServer()

    }

    