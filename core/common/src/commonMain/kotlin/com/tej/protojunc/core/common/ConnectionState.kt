package com.tej.protojunc.core.common

import kotlinx.serialization.Serializable

/**
 * Represents the state of a communication connection.
 * Sealed class to allow exhaustive when statements.
 */
@Serializable
sealed class ConnectionState {
    /**
     * The engine is idle and not attempting to connect.
     */
    @Serializable
    data object Idle : ConnectionState()

    /**
     * The engine is currently establishing a connection.
     */
    @Serializable
    data object Connecting : ConnectionState()

    /**
     * The engine is successfully connected.
     */
    @Serializable
    data object Connected : ConnectionState()

    /**
     * The engine is attempting to reconnect after a connection loss.
     * @param attempt The current reconnection attempt number.
     */
    @Serializable
    data class Reconnecting(val attempt: Int) : ConnectionState()

    /**
     * The engine encountered an error.
     * @param message A human-readable error message.
     * @param throwable The underlying exception, if any.
     */
    @Serializable
    data class Error(val message: String, val throwable: String? = null) : ConnectionState()
}
