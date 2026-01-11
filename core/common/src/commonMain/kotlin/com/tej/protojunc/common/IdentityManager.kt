package com.tej.protojunc.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import co.touchlab.kermit.Logger

data class UserIdentity(
    val deviceId: String,
    val publicKey: String,
    val displayName: String,
    val isVaultOpen: Boolean = false
)

interface IdentityManager {
    val identity: Flow<UserIdentity?>
    suspend fun getOrCreateIdentity(displayName: String): UserIdentity
    suspend fun updateDisplayName(newName: String)
}

class DataStoreIdentityManager(
    private val dataStore: DataStore<Preferences>
) : IdentityManager {

    private val DEVICE_ID = stringPreferencesKey("device_id")
    private val PUBLIC_KEY = stringPreferencesKey("public_key")
    private val PRIVATE_KEY = stringPreferencesKey("private_key")
    private val DISPLAY_NAME = stringPreferencesKey("display_name")

    override val identity: Flow<UserIdentity?> = dataStore.data.map { prefs ->
        val id = prefs[DEVICE_ID] ?: return@map null
        val pub = prefs[PUBLIC_KEY] ?: ""
        val name = prefs[DISPLAY_NAME] ?: "Device-$id"
        UserIdentity(id, pub, name)
    }

    override suspend fun getOrCreateIdentity(displayName: String): UserIdentity {
        val current = identity.first()
        if (current != null) return current

        val newId = generateRandomId()
        // In a real production app, we generate Ed25519 here
        val (pub, priv) = generateEd25519KeyPair()

        dataStore.edit { prefs ->
            prefs[DEVICE_ID] = newId
            prefs[PUBLIC_KEY] = pub
            prefs[PRIVATE_KEY] = priv
            prefs[DISPLAY_NAME] = displayName
        }

        return UserIdentity(newId, pub, displayName)
    }

    override suspend fun updateDisplayName(newName: String) {
        dataStore.edit { prefs ->
            prefs[DISPLAY_NAME] = newName
        }
    }
    
    private fun generateRandomId(): String = 
        (1..8).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("")
}

expect fun createDataStore(context: Any? = null): DataStore<Preferences>

expect fun generateEd25519KeyPair(): Pair<String, String>
