package com.tej.protojunc

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val deviceName: String = System.getProperty("os.name")
}

actual fun getPlatform(): Platform = JVMPlatform()

actual var signalingServerHost: String = "localhost"
actual var signalingServerPort: Int = 8080
actual val deviceName: String = System.getProperty("os.name") ?: "JVM"