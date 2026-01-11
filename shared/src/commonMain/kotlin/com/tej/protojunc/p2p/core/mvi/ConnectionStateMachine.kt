package com.tej.protojunc.p2p.core.mvi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Discovering : ConnectionState()
    data class Connecting(val deviceId: String) : ConnectionState()
    data class Connected(val deviceId: String) : ConnectionState()
    data class Failed(val error: Throwable) : ConnectionState()
}

sealed class ConnectionAction {
    data object StartDiscovery : ConnectionAction()
    data class Connect(val deviceId: String) : ConnectionAction()
    data object Disconnect : ConnectionAction()
    data class HandleSignal(val data: ByteArray) : ConnectionAction()
}

interface ConnectionStateMachine {
    val state: StateFlow<ConnectionState>
    fun dispatch(action: ConnectionAction)
}

class DefaultConnectionStateMachine(
    private val scope: CoroutineScope
) : ConnectionStateMachine {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override fun dispatch(action: ConnectionAction) {
        Logger.d(tag = "ConnectionStateMachine") { "Dispatching action: $action" }
        scope.launch {
            when (action) {
                is ConnectionAction.StartDiscovery -> {
                    _state.value = ConnectionState.Discovering
                    // Logic to trigger discovery would be hooked up here or observed by DiscoveryManager
                }
                is ConnectionAction.Connect -> {
                    _state.value = ConnectionState.Connecting(action.deviceId)
                }
                is ConnectionAction.Disconnect -> {
                    _state.value = ConnectionState.Idle
                }
                is ConnectionAction.HandleSignal -> {
                    // Handle incoming signal
                }
            }
        }
    }
}
