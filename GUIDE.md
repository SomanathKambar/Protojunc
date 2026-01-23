# Protojunc: Running the Ecosystem

This guide explains how to run the **Protojunc Signaling Server** and the **Android Client** to test WebRTC communication.

## System Overview
The ecosystem consists of:
1.  **Signaling Server:** A centralized Ktor server (`:server`) that brokers connections.
2.  **Clients:** Android devices (`:feature:vault`) running the "Protojunc" app.

**You only need to run ONE server instance.** All clients connect to this single instance.

## 1. Running the Signaling Server

The server acts as the hub for exchanging SDP offers/answers and ICE candidates between peers.

### Prerequisites
- JDK 17+ installed.
- Port `8080` must be free.

### Steps
1. Open a terminal in the project root.
2. Run the server using Gradle:
   ```bash
   ./gradlew :server:run
   ```
3. Wait for the log message:
   ```
   [SERVER] Starting Protojunc Signaling Server on port 8080...
   [SERVER] [mDNS] Discovery active: _protojunc._tcp.local.
   ```
4. **Verify:** Open your browser and go to `http://localhost:8080/dashboard`. You should see the "Protojunc Signaling Status" dashboard.

## 2. Running the Android Client

The Android app will connect to the signaling server to establish a P2P connection.

### Configuration
By default, the client is configured to connect to `ws://10.0.2.2:8080/signaling/default?device=Android`.
- **Emulator:** `10.0.2.2` maps to the host machine's `localhost`. It works out-of-the-box if the server is running on the same machine.
- **Physical Device:** You **must** change the `signalingUrl` in `SignalingManager.kt` to your computer's local IP address (e.g., `ws://192.168.1.105:8080...`).
    - **Note:** Ensure both the mobile device and the computer are on the **same Wi-Fi network**.
    - **Note:** Ensure your computer's firewall allows incoming connections on port `8080`.

### Steps
1. Connect your Android device or start an Emulator.
2. Run the app:
   ```bash
   ./gradlew :feature:vault:installDebug
   ```
   Or launch "feature.vault" from Android Studio.
3. Open the app. It will attempt to connect to the server automatically.
4. **Verify:** Check the Server Dashboard (`http://localhost:8080/dashboard`). You should see a new peer appear in the "Active Trunks" section.

## 3. Testing P2P (Simulated)

To test a full call, you need **two devices** connecting to the same room code.
1. Run the app on **Device A** (Emulator).
2. Run the app on **Device B** (Another Emulator or Physical Device).
3. Both devices should appear in the Server Dashboard under the same Room (default: `default`).
4. When implemented, one device initiating a call will trigger an `offer` -> `answer` -> `candidate` exchange, visible in the server logs.

## Troubleshooting

- **Server won't start:**
    - Check if port 8080 is used: `lsof -i :8080`.
    - Kill the process or change the port in `Application.kt`.
- **App can't connect:**
    - **Emulator:** Check internet access. Verify `10.0.2.2` is reachable.
    - **Physical Device:** Double-check IP address in `SignalingManager.kt` and firewall settings.
- **Build fails:**
    - Run `./gradlew clean` and try again.
    - Ensure you are using JDK 17.