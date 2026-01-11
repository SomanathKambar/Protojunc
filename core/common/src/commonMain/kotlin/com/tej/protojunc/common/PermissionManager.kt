package com.tej.protojunc.common

import kotlinx.coroutines.flow.StateFlow

enum class PermissionType {
    STORAGE, CAMERA, MICROPHONE, BLUETOOTH
}

interface PermissionManager {
    fun isGranted(type: PermissionType): Boolean
    suspend fun request(type: PermissionType): Boolean
}

expect fun createPermissionManager(context: Any?): PermissionManager
