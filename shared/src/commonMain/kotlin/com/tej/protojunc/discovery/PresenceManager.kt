package com.tej.protojunc.discovery

import com.tej.protojunc.common.IdentityManager
import com.tej.protojunc.common.UserIdentity
import com.tej.protojunc.models.NearbyPeer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import co.touchlab.kermit.Logger

interface PresenceManager {
    val nearbyPeers: StateFlow<List<NearbyPeer>>
    suspend fun startPresence()
    fun stopPresence()
}

interface PresenceAdvertiser {
    fun startAdvertising(identity: UserIdentity)
    fun stopAdvertising()
}

interface PresenceScanner {
    val discoveredPeers: Flow<NearbyPeer>
    fun startScanning()
    fun stopScanning()
}

class PresenceManagerImpl(
    private val identityManager: IdentityManager,
    private val advertiser: PresenceAdvertiser,
    private val scanner: PresenceScanner,
    private val scope: CoroutineScope
) : PresenceManager {

    private val _nearbyPeers = MutableStateFlow<Map<String, NearbyPeer>>(emptyMap())
    override val nearbyPeers = _nearbyPeers
        .map { it.values.toList().sortedByDescending { peer -> peer.lastSeen } }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    private var presenceJob: Job? = null

    override suspend fun startPresence() = withContext(Dispatchers.Default) {
        if (presenceJob != null) return@withContext

        val identity = identityManager.identity.filterNotNull().first()
        
        advertiser.startAdvertising(identity)
        scanner.startScanning()

        presenceJob = scope.launch {
            launch {
                scanner.discoveredPeers.collect { peer ->
                    _nearbyPeers.update { current ->
                        current + (peer.deviceId to peer)
                    }
                }
            }

            // Cleanup stale peers (not seen for 30s)
            launch {
                while (isActive) {
                    delay(10000)
                    val now = System.currentTimeMillis()
                    _nearbyPeers.update { current ->
                        current.filter { (_, peer) -> now - peer.lastSeen < 30000 }
                    }
                }
            }
        }
        Logger.i { "Presence tracking active for ${identity.displayName}" }
    }

    override fun stopPresence() {
        advertiser.stopAdvertising()
        scanner.stopScanning()
        presenceJob?.cancel()
        presenceJob = null
        _nearbyPeers.value = emptyMap()
    }
}
