package com.tej.protojunc.core.signaling

import com.tej.protojunc.core.common.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Defines the contract for a communication engine.
 * This interface abstracts the underlying protocol (WebRTC, XMPP, BLE, etc.).
 *
 * Adheres to SOLID principles:
 * - ISP: Clients depend only on this interface, not concrete implementations.
 * - DIP: High-level modules depend on abstractions.
 */
interface CommunicationEngine {

    /**
     * The current state of the connection.
     * Emits updates as the connection lifecycle changes.
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Initiates the connection process.
     * Implementations should handle authentication and setup here.
     */
    suspend fun connect()

    /**
     * Terminates the connection and releases resources.
     */
    suspend fun disconnect()

    /**
     * Sends a message or data payload.
     * @param data The data to send.
     */
    suspend fun sendData(data: ByteArray)
}
