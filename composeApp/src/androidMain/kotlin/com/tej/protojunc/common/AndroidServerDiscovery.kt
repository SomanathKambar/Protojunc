package com.tej.protojunc.common

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import co.touchlab.kermit.Logger

class AndroidServerDiscovery(context: Context) : ServerDiscovery {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _discoveredServer = MutableStateFlow<ServerInfo?>(null)
    override val discoveredServer: StateFlow<ServerInfo?> = _discoveredServer

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Logger.d { "mDNS Discovery started" }
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Logger.d { "mDNS Service found: ${service.serviceName}" }
            if (service.serviceType == "_protojunc._tcp.") {
                nsdManager.resolveService(service, resolveListener)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Logger.d { "mDNS Service lost" }
            _discoveredServer.value = null
        }

        override fun onDiscoveryStopped(regType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Logger.e { "mDNS Resolve failed: $errorCode" }
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Logger.i { "mDNS Service resolved: ${serviceInfo.host.hostAddress}:${serviceInfo.port}" }
            _discoveredServer.value = ServerInfo(serviceInfo.host.hostAddress, serviceInfo.port)
        }
    }

    override fun startDiscovery() {
        nsdManager.discoverServices("_protojunc._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {}
    }
}
