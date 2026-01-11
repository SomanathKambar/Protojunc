package com.tej.protojunc

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.text.SimpleDateFormat
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.net.InetAddress

fun startDiscovery() {
    try {
        val jmdns = JmDNS.create(InetAddress.getLocalHost())
        val serviceInfo = ServiceInfo.create("_protojunc._tcp.local.", "Protojunc-Signaling", 8080, "Protojunc Signaling Server")
        jmdns.registerService(serviceInfo)
        println("[SERVER] [mDNS] Discovery active: _protojunc._tcp.local.")
    } catch (e: Exception) {
        println("[SERVER] [mDNS] Failed to start discovery: ${e.message}")
    }
}

fun main() {
    println("[SERVER] Starting Protojunc Signaling Server on port 8080...")
    startDiscovery()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

class PeerSession(
    val id: String, 
    val deviceName: String, 
    val session: DefaultWebSocketServerSession,
    val joinedAt: Long = System.currentTimeMillis(),
    var lastMessage: String = "Connecting..."
)

val rooms = ConcurrentHashMap<String, MutableList<PeerSession>>()
val serverEvents = Collections.synchronizedList(mutableListOf<String>())

fun logEvent(msg: String) {
    val time = SimpleDateFormat("HH:mm:ss").format(Date())
    serverEvents.add(0, "[$time] $msg")
    if (serverEvents.size > 20) serverEvents.removeAt(20)
    println("[SERVER] $msg")
}

fun Application.module() {
    install(io.ktor.server.plugins.calllogging.CallLogging)
    
    install(io.ktor.server.plugins.statuspages.StatusPages) {
        exception<Throwable> { call, cause ->
            logEvent("CRITICAL SERVER ERROR: ${cause.localizedMessage}")
            call.respondText(
                "Protojunc Internal Error: ${cause.message}",
                status = io.ktor.http.HttpStatusCode.InternalServerError
            )
        }
    }

    install(WebSockets) {
        pingPeriod = 15.toDuration(DurationUnit.SECONDS)
        timeout = 15.toDuration(DurationUnit.SECONDS)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respondText("Protojunc Signaling Server is Running!\nActive Rooms: ${rooms.size}\nTotal Peers: ${rooms.values.sumOf { it.size }}")
        }

        get("/dashboard") {
            try {
                val html = """
                    <html>
                    <head>
                        <title>Protojunc Server Dashboard</title>
                        <style>
                            body { font-family: 'Segoe UI', sans-serif; padding: 40px; background: #f0f2f5; color: #1c1e21; display: flex; flex-direction: column; gap: 20px; }
                            .header { display: flex; align-items: center; gap: 15px; margin-bottom: 10px; }
                            .content { display: flex; gap: 20px; }
                            .panel { background: white; padding: 20px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); flex: 1; }
                            .event-log { font-family: monospace; font-size: 0.85em; background: #1c1e21; color: #00ff00; padding: 15px; border-radius: 8px; height: 400px; overflow-y: auto; }
                            .room { border-bottom: 1px solid #eee; padding: 10px 0; }
                            .peer { display: flex; align-items: center; margin-top: 5px; font-size: 0.9em; }
                            .dot { height: 8px; width: 8px; background-color: #31a24c; border-radius: 50%; margin-right: 8px; }
                            h1, h2 { margin-top: 0; }
                        </style>
                        <meta http-equiv="refresh" content="2">
                    </head>
                    <body>
                        <div class="header">
                            <svg width="60" height="60" viewBox="0 0 1024 1024" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <rect width="1024" height="1024" rx="200" fill="#2D3436"/>
                                <path d="M320 250V700C320 820 420 880 520 880C620 880 700 820 700 700V550" stroke="#00CEC9" stroke-width="80" stroke-linecap="round"/>
                                <circle cx="550" cy="380" r="150" stroke="white" stroke-width="80"/>
                                <circle cx="550" cy="380" r="40" fill="#FAB1A0"/>
                                <circle cx="320" cy="250" r="50" fill="white"/>
                            </svg>
                            <h1>Protojunc Signaling Status</h1>
                        </div>
                        <div class="content">
                            <div class="panel">
                                <h2>Active Trunks</h2>
                            ${rooms.entries.joinToString("") { entry ->
                                """
                                <div class="room">
                                    <strong>Room: ${entry.key}</strong>
                                    ${entry.value.joinToString("") { peer -> 
                                        "<div class='peer'><span class='dot'></span> ${peer.deviceName} (${peer.id})</div>" 
                                    }}
                                </div>
                                """
                            }}
                            ${if (rooms.isEmpty()) "<p style='color:#888'>No active rooms.</p>" else ""}
                        </div>
                        <div class="panel">
                            <h2>Live Event Log</h2>
                            <div class="event-log">
                                ${serverEvents.joinToString("<br>")}
                            </div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                call.respondText(html, io.ktor.http.ContentType.Text.Html)
            } catch (e: Exception) {
                call.respondText("Dashboard Error: ${e.message}")
            }
        }
        
        route("/signaling") {
            webSocket("/{roomCode?}") {
                val roomCode = call.parameters["roomCode"] ?: "default"
                val deviceName = call.request.queryParameters["device"] ?: "Unknown-Device"
                val peerId = UUID.randomUUID().toString().take(8)
                
                logEvent("Connection Attempt: $deviceName (Room: $roomCode)")
                
                val session = PeerSession(peerId, deviceName, this)
                val roomPeers = rooms.getOrPut(roomCode) { Collections.synchronizedList(mutableListOf()) }
                roomPeers.add(session)
                
                logEvent("Trunk Established: $peerId in '$roomCode'")

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            session.lastMessage = text.take(50)
                            logEvent("Msg from $peerId: ${text.take(20)}...")
                            val targets = roomPeers.filter { it.id != peerId }
                            targets.forEach { target ->
                                try {
                                    target.session.send(text)
                                } catch (e: Exception) {
                                    logEvent("Broadcast fail to ${target.id}: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logEvent("Trunk Error ($peerId): ${e.localizedMessage}")
                } finally {
                    roomPeers.remove(session)
                    if (roomPeers.isEmpty()) rooms.remove(roomCode)
                    logEvent("Trunk Closed: $peerId")
                }
            }
        }
    }
}
