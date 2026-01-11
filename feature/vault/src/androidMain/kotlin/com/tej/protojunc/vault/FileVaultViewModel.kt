package com.tej.protojunc.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.tej.protojunc.models.TransferStatus

class FileVaultViewModel(
    private val manager: FileTransferManager
) : ViewModel() {

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri = _selectedUri.asStateFlow()

    fun onFileSelected(uri: Uri?) {
        _selectedUri.value = uri
    }

    fun sendFile(ip: String) {
        val uri = _selectedUri.value ?: return
        viewModelScope.launch {
            manager.sendFile(ip, uri.toString())
        }
    }

    fun clearSelection() {
        _selectedUri.value = null
    }
}
