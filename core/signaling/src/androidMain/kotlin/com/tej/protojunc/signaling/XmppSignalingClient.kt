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

actual class XmppSignalingClient actual constructor(
    private val jid: String,
    private val password: String,
    private val host: String,
    private val domain: String
) : SignalingClient {

    private val _state = MutableStateFlow(SignalingState.IDLE)
    override actual val state: StateFlow<SignalingState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    override actual val messages: Flow<SignalingMessage> = _messages.asSharedFlow()

    private var connection: AbstractXMPPConnection? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Track the last peer we received a message from so we can reply
    private var lastRemoteJid: org.jxmpp.jid.Jid? = null

    override actual suspend fun connect() {
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
                    .setSendPresence(true)
                    .build()

                val conn = XMPPTCPConnection(config)
                connection = conn
                conn.connect()
                conn.login()

                val chatManager = ChatManager.getInstanceFor(conn)
                chatManager.addIncomingListener { from, message, chat ->
                    val body = message.body
                    if (body != null) {
                        lastRemoteJid = from.asBareJid()
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

    override actual suspend fun sendMessage(message: SignalingMessage) {
        val conn = connection ?: return
        if (!conn.isAuthenticated) return

        withContext(Dispatchers.IO) {
            try {
                val chatManager = ChatManager.getInstanceFor(conn)
                
                // If we don't have a lastRemoteJid, we try to derive it from senderId if it looks like a JID
                // or we use a fallback if absolutely necessary.
                // In P2P signaling, the first message is usually JOIN which sets lastRemoteJid.
                val target = lastRemoteJid ?: JidCreate.from("admin@$domain") 
                
                val chat = chatManager.chatWith(target.asEntityBareJidIfPossible())
                val text = SignalingEncoder.encode(message)
                chat.send(text)
                Logger.d { "XMPP Sent ${message.type} to $target" }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to send XMPP message" }
            }
        }
    }

    override actual suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            connection?.disconnect()
            connection = null
            _state.value = SignalingState.IDLE
        }
    }
}
