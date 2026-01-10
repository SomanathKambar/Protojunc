package com.tej.directo

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

class PeerSession(val id: String, val session: DefaultWebSocketServerSession)

val rooms = ConcurrentHashMap<String, MutableList<PeerSession>>()

fun Application.module() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/signaling/{roomCode}") {
            val roomCode = call.parameters["roomCode"] ?: "default"
            val peerId = UUID.randomUUID().toString()
            val session = PeerSession(peerId, this)
            
            val roomPeers = rooms.getOrPut(roomCode) { Collections.synchronizedList(mutableListOf()) }
            roomPeers.add(session)
            
            println("Peer $peerId joined room $roomCode. Total peers: ${roomPeers.size}")

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // Broadcast to other peers in the room
                        roomPeers.filter { it.id != peerId }.forEach {
                            it.session.send(text)
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error in room $roomCode for peer $peerId: ${e.localizedMessage}")
            } finally {
                roomPeers.remove(session)
                if (roomPeers.isEmpty()) {
                    rooms.remove(roomCode)
                }
                println("Peer $peerId left room $roomCode")
            }
        }
    }
}
