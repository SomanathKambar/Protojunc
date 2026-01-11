package com.tej.directo

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val deviceName: String = UIDevice.currentDevice.name
}

actual fun getPlatform(): Platform = IOSPlatform()

actual var signalingServerHost: String = "localhost"
actual var signalingServerPort: Int = 8080
actual val deviceName: String = UIDevice.currentDevice.name