package com.tej.protojunc.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.tej.protojunc.common.UserIdentity

class IosPresenceAdvertiser : PresenceAdvertiser {
    override fun startAdvertising(identity: UserIdentity) {}
    override fun stopAdvertising() {}
}

class IosPresenceScanner : PresenceScanner {
    override val discoveredPeers: Flow<NearbyPeer> = emptyFlow()
    override fun startScanning() {}
    override fun stopScanning() {}
}
