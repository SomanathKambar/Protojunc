package com.tej.directo
import android.app.Application
import com.shepeliev.webrtckmp.WebRtc

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // If this still fails, I will remove it and trust the App Startup initializer
        try {
            // WebRtc.init(this) // Wait, I will just remove it to be safe and use a different approach if needed
        } catch (e: Exception) {}
    }
}
