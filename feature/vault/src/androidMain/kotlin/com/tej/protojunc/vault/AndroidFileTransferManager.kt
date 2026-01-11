package com.tej.protojunc.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import co.touchlab.kermit.Logger
import com.tej.protojunc.models.FileMetadata
import com.tej.protojunc.models.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket

class AndroidFileTransferManager(private val context: Context) : FileTransferManager {

    private val _incomingTransfers = MutableStateFlow<List<FileMetadata>>(emptyList())
    override val incomingTransfers: StateFlow<List<FileMetadata>> = _incomingTransfers.asStateFlow()

    private val _transferStatus = MutableStateFlow<TransferStatus>(TransferStatus.Idle)
    override val transferStatus: StateFlow<TransferStatus> = _transferStatus.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    override val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val PORT = 8889

    override suspend fun sendFile(ip: String, filePath: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(filePath)
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor?.getColumnIndex(OpenableColumns.SIZE)
        
        cursor?.moveToFirst()
        val fileName = cursor?.getString(nameIndex ?: 0) ?: "unknown"
        val fileSize = cursor?.getLong(sizeIndex ?: 0) ?: 0L
        cursor?.close()

        try {
            Socket(ip, PORT).use { socket ->
                val output = socket.getOutputStream()
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                
                // 1. Send Metadata (Name|Size)
                val metadata = "$fileName|$fileSize\n"
                output.write(metadata.toByteArray())
                
                // 2. Send Chunks
                val buffer = ByteArray(8192)
                var totalSent = 0L

                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    _transferStatus.value = TransferStatus.Progress(totalSent, fileSize)
                }
                
                inputStream.close()
                _transferStatus.value = TransferStatus.Completed
                Logger.i { "File sent: $fileName" }
            }
        } catch (e: Exception) {
            Logger.e(e) { "File send failed" }
            _transferStatus.value = TransferStatus.Error(e.message ?: "Unknown Error")
        }
    }

    override suspend fun startFileServer() = withContext(Dispatchers.IO) {
        if (_isServerRunning.value) return@withContext
        
        try {
            serverSocket = ServerSocket(PORT)
            _isServerRunning.value = true
            Logger.i { "File server started on port $PORT" }
            
            while (isActive) {
                val client = serverSocket?.accept() ?: break
                handleIncomingFile(client)
            }
        } catch (e: Exception) {
            if (isActive) Logger.e(e) { "File server failed" }
        } finally {
            stopFileServer()
        }
    }

    override fun stopFileServer() {
        _isServerRunning.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Logger.e(e) { "Error closing server socket" }
        }
        serverSocket = null
    }

    private fun handleIncomingFile(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val reader = input.bufferedReader()
            
            // 1. Read Metadata
            val metadataLine = reader.readLine() ?: return
            val parts = metadataLine.split("|")
            val name = parts[0]
            val size = parts[1].toLong()
            
            _incomingTransfers.value += FileMetadata(name, size, "unknown")
            
            // 2. Save to Private Storage
            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val outputFile = File(downloadDir, "vault_$name")
            val outputStream = FileOutputStream(outputFile)
            
            val buffer = ByteArray(8192)
            var totalReceived = 0L
            
            _transferStatus.value = TransferStatus.Progress(0, size)

            while (totalReceived < size) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                totalReceived += bytesRead
                _transferStatus.value = TransferStatus.Progress(totalReceived, size)
            }
            
            outputStream.close()
            _transferStatus.value = TransferStatus.Completed
            Logger.i { "File received and saved to: ${outputFile.absolutePath}" }
        } catch (e: Exception) {
            Logger.e(e) { "Incoming file failed" }
            _transferStatus.value = TransferStatus.Error(e.message ?: "Receive Error")
        } finally {
            socket.close()
        }
    }
}