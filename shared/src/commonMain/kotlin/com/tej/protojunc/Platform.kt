package com.tej.protojunc

interface Platform {
    val name: String
    val deviceName: String
}

expect fun getPlatform(): Platform

expect var signalingServerHost: String
expect var signalingServerPort: Int
expect val deviceName: String