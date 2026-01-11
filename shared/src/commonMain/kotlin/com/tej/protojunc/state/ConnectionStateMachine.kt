
import com.tej.protojunc.util.SignalingEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ConnectionIntent {
    object InitializeHandshake : ConnectionIntent()
    data class ReceiveRemotePayload(val base64: String) : ConnectionIntent()
    object Disconnect : ConnectionIntent()
}

sealed class ConnectionState {
    object Idle : ConnectionState()
    object GatheringICE : ConnectionState()
    data class ReadyToSend(val localPayload: String) : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Failed(val error: String) : ConnectionState()
}

class ConnectionViewModel {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun handleIntent(intent: ConnectionIntent) {
        when (intent) {
            is ConnectionIntent.InitializeHandshake -> startGathering()
            is ConnectionIntent.ReceiveRemotePayload -> processPayload(intent.base64)
            is ConnectionIntent.Disconnect -> _state.value = ConnectionState.Idle
        }
    }

    private fun startGathering() {
        _state.value = ConnectionState.GatheringICE
    }

    private fun processPayload(base64: String) {
        try {
            val payload = SignalingEncoder.decode(base64)
            _state.value = ConnectionState.Connecting
        } catch (e: Exception) {
            _state.value = ConnectionState.Failed("Invalid Signaling Data")
        }
    }
}
