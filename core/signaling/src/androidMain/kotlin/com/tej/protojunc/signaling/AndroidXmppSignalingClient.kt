package com.tej.protojunc.signaling

import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.impl.JidCreate
import com.tej.protojunc.signaling.util.SignalingEncoder
import com.tej.protojunc.core.models.SignalingMessage

class AndroidXmppSignalingClient(
    private val jid: String,
    private val password: String = "password",
    private val host: String = "10.0.2.2", // Default to localhost for emulator
    private val domain: String = "example.com"
) : SignalingClient {

    private val _state = MutableStateFlow(SignalingState.IDLE)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    override val messages = _messages.asSharedFlow()

    private var connection: AbstractXMPPConnection? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun connect() {
        if (_state.value == SignalingState.CONNECTED) return
        _state.value = SignalingState.CONNECTING

        withContext(Dispatchers.IO) {
            try {
                val config = XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(domain)
                    .setHost(host)
                    .setPort(5222)
                    .setUsernameAndPassword(jid.split("@")[0], password)
                    .setSecurityMode(org.jivesoftware.smack.ConnectionConfiguration.SecurityMode.disabled)
                    .build()

                val conn = XMPPTCPConnection(config)
                connection = conn
                conn.connect()
                conn.login()

                val chatManager = ChatManager.getInstanceFor(conn)
                chatManager.addIncomingListener { from, message, chat ->
                    val body = message.body
                    if (body != null) {
                        scope.launch {
                            try {
                                val signalingMsg = SignalingEncoder.decode(body)
                                _messages.emit(signalingMsg)
                            } catch (e: Exception) {
                                Logger.e { "Failed to decode XMPP message: ${e.message}" }
                            }
                        }
                    }
                }

                _state.value = SignalingState.CONNECTED
                Logger.i { "XMPP Connected and Authenticated as $jid" }

            } catch (e: Exception) {
                Logger.e(e) { "XMPP Connection Failed" }
                _state.value = SignalingState.ERROR
            }
        }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        val conn = connection ?: return
        if (!conn.isAuthenticated) return

        withContext(Dispatchers.IO) {
            try {
                val chatManager = ChatManager.getInstanceFor(conn)
                val targetJid = JidCreate.from("admin@$domain") 
                val chat = chatManager.chatWith(targetJid.asEntityBareJidIfPossible())
                
                val text = SignalingEncoder.encode(message)
                chat.send(text)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to send XMPP message" }
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            connection?.disconnect()
            connection = null
            _state.value = SignalingState.IDLE
        }
    }
}