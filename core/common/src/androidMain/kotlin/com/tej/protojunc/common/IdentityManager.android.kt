package com.tej.protojunc.common

import java.util.Base64
import java.security.SecureRandom
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

actual fun createDataStore(context: Any?): DataStore<Preferences> {
    val ctx = context as Context
    return androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        produceFile = { ctx.preferencesDataStoreFile("protojunc_identity") }
    )
}

actual fun generateEd25519KeyPair(): Pair<String, String> {
    // Simplified for KMP demonstration without adding heavy crypto libs yet
    // In production, use Sodium-KMP or platform-specific secure enclaves
    val random = SecureRandom()
    val pubBytes = ByteArray(32)
    val privBytes = ByteArray(32)
    random.nextBytes(pubBytes)
    random.nextBytes(privBytes)
    
    val encoder = Base64.getEncoder()
    return encoder.encodeToString(pubBytes) to encoder.encodeToString(privBytes)
}
