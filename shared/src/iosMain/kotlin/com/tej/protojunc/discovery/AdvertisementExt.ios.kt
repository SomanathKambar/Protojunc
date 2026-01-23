package com.tej.protojunc.discovery

import com.juul.kable.Advertisement

actual fun Advertisement.stableId(): String {
    return uuid.toString() // iOS-specific property
}
