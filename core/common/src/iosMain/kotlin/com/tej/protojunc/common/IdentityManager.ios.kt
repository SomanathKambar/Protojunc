package com.tej.protojunc.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger

actual fun createDataStore(context: Any?): DataStore<Preferences> {
    // Return a dummy or throw controlled error for now
    // Real implementation requires okio and proper path resolution
    throw UnsupportedOperationException("iOS DataStore not implemented yet")
}

actual fun generateEd25519KeyPair(): Pair<String, String> {
    // Placeholder for iOS CryptoKit implementation
    return "IOS_PUB_PLACEHOLDER" to "IOS_PRIV_PLACEHOLDER"
}