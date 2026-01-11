package com.tej.protojunc

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val deviceName: String = Build.MODEL
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual var signalingServerHost: String = "10.0.2.2"
actual var signalingServerPort: Int = 8080
actual val deviceName: String = Build.MODEL