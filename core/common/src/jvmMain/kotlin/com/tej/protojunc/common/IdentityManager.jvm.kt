package com.tej.protojunc.common

import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File

actual fun createDataStore(context: Any?): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(
        produceFile = { File("protojunc_identity.preferences_pb") }
    )
}

actual fun generateEd25519KeyPair(): Pair<String, String> {
    return try {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val kp = kpg.generateKeyPair()
        
        val encoder = Base64.getEncoder()
        val pub = encoder.encodeToString(kp.public.encoded)
        val priv = encoder.encodeToString(kp.private.encoded)
        
        pub to priv
    } catch (e: Exception) {
        // Fallback or handle error
        "FALLBACK_PUB_${System.currentTimeMillis()}" to "FALLBACK_PRIV"
    }
}
