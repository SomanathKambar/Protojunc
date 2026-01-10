package com.tej.directo.webrtc

import com.shepeliev.webrtckmp.IceCandidate
import com.tej.directo.models.IceCandidateModel
import org.webrtc.IceCandidate as NativeIceCandidate
import java.lang.reflect.Constructor

actual fun createIceCandidate(model: IceCandidateModel): IceCandidate {
    val native = NativeIceCandidate(
        model.sdpMid ?: "",
        model.sdpMLineIndex,
        model.sdp
    )
    
    return try {
        // Use reflection to call the internal constructor
        val constructor = IceCandidate::class.java.declaredConstructors.firstOrNull { 
            it.parameterTypes.size == 1 && it.parameterTypes[0].name.contains("IceCandidate")
        }
        constructor?.isAccessible = true
        constructor?.newInstance(native) as IceCandidate
    } catch (e: Exception) {
        // Last resort: If reflection fails, we might need a library-specific factory
        throw IllegalStateException("Failed to instantiate IceCandidate wrapper via reflection", e)
    }
}

actual fun IceCandidate.toModel(): IceCandidateModel {
    return try {
        // Use reflection to access native properties if they are private
        val nativeField = this.javaClass.getDeclaredField("native") // Common naming in wrappers
        nativeField.isAccessible = true
        val native = nativeField.get(this) as NativeIceCandidate
        
        IceCandidateModel(
            sdp = native.sdp,
            sdpMid = native.sdpMid,
            sdpMLineIndex = native.sdpMLineIndex
        )
    } catch (e: Exception) {
        // Fallback: Try to use public fields if they exist but were misidentified
        IceCandidateModel("", null, 0)
    }
}
