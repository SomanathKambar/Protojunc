# Protojunc

Protojunc is a Universal P2P Communication Platform built with **Kotlin Multiplatform** and **Compose Multiplatform**. It enables seamless Video, Voice, and Text communication across devices using a variety of signaling and discovery methods, ranging from local Bluetooth/WiFi-Direct to Global Online Servers.

## 🚀 Key Features

- **Multi-Modal Communication**: Support for Video Calls, Voice Calls (Audio-only), and real-time Text Messaging.
- **Hybrid Signaling**: 
  - **Online**: Ktor-based WebSocket signaling with mDNS auto-discovery for local networks.
  - **Local P2P**: BLE Discovery, Bluetooth Sockets, and WiFi Direct (In-Progress).
  - **Manual**: QR-Code based SDP exchange for air-gapped environments.
- **Plug-and-Play Architecture**: Decoupled signaling and discovery logic using the Strategy Pattern.
- **Real-time Monitoring**: Built-in Server Dashboard with live event logs for trunk-call management.

## 🛠 Tech Stack

- **Frontend**: Compose Multiplatform (Android & iOS)
- **Backend**: Ktor Server (Kotlin JVM)
- **Networking**: Ktor Client (WebSockets, HTTP)
- **WebRTC**: [WebRTC-KMP](https://github.com/shepeliev/webrtc-kmp) for cross-platform P2P media.
- **Discovery**: 
  - **mDNS**: JmDNS (Server) & NsdManager (Android) for zero-config connection.
  - **BLE**: Kable for Bluetooth Low Energy discovery.
- **Serialization**: Kotlinx Serialization (JSON & Protobuf)
- **Logging**: Kermit for unified multiplatform logs.

## 🏗 Project Structure

- `:composeApp`: Shared UI logic using Compose Multiplatform.
- `:shared`: Core WebRTC logic, Call Orchestrator, and platform-specific drivers.
- `:server`: Ktor Signaling Server with dashboard and mDNS broadcaster.
- `:core:signaling`: Abstract signaling interfaces and Ktor/XMPP implementations.
- `:core:common`: Shared utilities, health checks, and discovery interfaces.

## 🚦 Current Status

| Feature | Status | Notes |
| :--- | :--- | :--- |
| **Online Mode** | ✅ Functional | mDNS Auto-discovery, Video/Voice/Message works. |
| **Server Dashboard** | ✅ Functional | Visit `http://<server-ip>:8080/dashboard` |
| **Bluetooth (BLE)** | ⚠️ Experimental | Basic discovery works, handshake needs refinement. |
| **WiFi Direct** | 🛠 In-Progress | Modular drivers defined. |
| **XMPP (Enterprise)** | 🛠 In-Progress | Architectural hooks ready. |

## 💻 Getting Started

### 1. Run the Signaling Server
The server is located in the `/server` protojuncry. It broadcasts itself on the local network automatically.

```bash
./gradlew :server:run
```
- **Dashboard**: `http://localhost:8080/dashboard`
- **mDNS Service**: `_protojunc._tcp.local.`

### 2. Run the Android App
1. Open the project in Android Studio.
2. Ensure your phone is on the same WiFi as the server.
3. Launch the `:composeApp` module.
4. The app will auto-discover the server (Server dot will turn Green).

### 3. Run the iOS App
1. Navigate to `/iosApp` and run `pod install`.
2. Open `iosApp.xcworkspace` in Xcode.
3. Run on a physical device or simulator.

## 🤝 Contributing

Contributions are welcome! Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.