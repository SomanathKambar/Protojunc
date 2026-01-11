package com.tej.protojunc.common

class IosPermissionManager : PermissionManager {
    override fun isGranted(type: PermissionType): Boolean = true
    override suspend fun request(type: PermissionType): Boolean = true
}

actual fun createPermissionManager(context: Any?): PermissionManager = IosPermissionManager()
