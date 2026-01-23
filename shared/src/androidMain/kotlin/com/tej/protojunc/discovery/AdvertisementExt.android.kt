package com.tej.protojunc.discovery

import com.juul.kable.Advertisement

actual fun Advertisement.stableId(): String {
    // Use pure Java reflection to avoid KotlinReflectionNotSupportedError (missing kotlin-reflect.jar)
    try {
        // Kable's Android implementation typically has an 'address' property.
        // In Java, this corresponds to getAddress().
        val method = this.javaClass.getMethod("getAddress")
        val addr = method.invoke(this)
        if (addr is String) {
            return addr
        }
    } catch (e: Exception) {
        // Fallback if getAddress() is missing or fails
    }
    
    // Fallback: use name or hash if address not found
    return name ?: "Unknown-${this.hashCode()}"
}
