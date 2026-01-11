package com.tej.protojunc.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class AndroidPermissionManager(private val context: Context) : PermissionManager {
    
    override fun isGranted(type: PermissionType): Boolean {
        val permission = when (type) {
            PermissionType.STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            }
            PermissionType.CAMERA -> Manifest.permission.CAMERA
            PermissionType.MICROPHONE -> Manifest.permission.RECORD_AUDIO
            PermissionType.BLUETOOTH -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Manifest.permission.BLUETOOTH_SCAN + "," + 
                    Manifest.permission.BLUETOOTH_ADVERTISE + "," + 
                    Manifest.permission.BLUETOOTH_CONNECT
                } else {
                    Manifest.permission.BLUETOOTH
                }
            }
        }
        // Handle comma-separated permissions for modern Android
        return permission.split(",").all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override suspend fun request(type: PermissionType): Boolean {
        // Implementation typically requires an Activity launcher
        // For production, we'd use a transparent Activity or a library like MOKO Permissions
        // For now, we return the check status
        return isGranted(type)
    }
}

actual fun createPermissionManager(context: Any?): PermissionManager = 
    AndroidPermissionManager(context as Context)
