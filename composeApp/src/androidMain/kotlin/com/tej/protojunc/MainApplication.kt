package com.tej.protojunc
import android.app.Application
import com.shepeliev.webrtckmp.WebRtc
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.tej.protojunc.core.signaling.di.createSignalingModule

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@MainApplication)
            modules(createSignalingModule(com.tej.protojunc.signalingServerHost, com.tej.protojunc.signalingServerPort))
        }
        
        try {
            // WebRtc.init(this) 
        } catch (e: Exception) {}
    }
}
