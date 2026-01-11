# Protojunc 2.0: Sovereign Communication Suite Plan

## Vision
To transform Protojunc from a technical experiment into a production-grade, local-first, privacy-focused communication platform.

## Roadmap

### Phase 1: Unified Signaling & Identity ✅ (DONE)
- [x] **Protobuf Signaling Migration**: Type-safe signaling using Gzip + Base64 over all transports.
- [x] **Sovereign Identity**: Permanent local device fingerprints stored in encrypted DataStore.
- [x] **E2EE Signaling**: Established cryptographic handshakes for peer-to-peer security.

### Phase 2: The Unified Link Layer ✅ (DONE)
- [x] **LinkOrchestrator**: Intelligent multi-transport fallback (LAN > WiFi-Direct > BLE > Cloud).
- [x] **Background Presence**: Persistent peer discovery via low-power BLE heartbeats.
- [x] **Handover Support**: Redundant signaling broadcast during link transitions.

### Phase 3: High-Value Features & UI ✅ (DONE)
- [x] **Modular Architecture**: Extracted `:feature:vault` and `:core:ui` for production scalability.
- [x] **P2P File Vault**: Integrated Android Scoped Storage and File Picker for zero-data sharing.
- [x] **Modern Design System**: Centralized "Sovereign" UI Kit in `:core:ui`.
- [x] **Mesh Calling**: Decentralized group coordination logic in `MeshCoordinator` with BLE auto-invite.
- [x] **Adaptive Bitrate Engine**: Dynamic link-aware bitrate scaling hooks in WebRTC.

## Technical Implementation Details (Production Grade)

### **1. Advanced BLE Mesh**
- **Passive Discovery:** `PresenceManager` uses `AndroidPresenceAdvertiser` to broadcast identity heartbeats.
- **Mesh Signaling:** Discovery heartbeats carry truncated identity data, triggering automated WebRTC invites via `MeshCoordinator` without manual scanning.

### **2. P2P File Vault**
- **Scoped Storage:** `AndroidFileTransferManager` now handles Android `Uri` inputs and saves to app-private `getExternalFilesDir`.
- **System Integration:** Real file selection via `ActivityResultContracts.GetContent` integrated into the Vault UI.

### Phase 3: High-Value Features & UI ✅ (DONE)
- [x] **Modular Architecture**: Extracted `:feature:vault` and `:core:ui` for production scalability.
- [x] **P2P File Vault**: Integrated Android Scoped Storage and File Picker for zero-data sharing.
- [x] **Modern Design System**: Centralized "Sovereign" UI Kit in `:core:ui`.
- [x] **Mesh Calling**: Decentralized group coordination logic in `MeshCoordinator` with BLE auto-invite.
- [x] **Adaptive Bitrate Engine**: Dynamic link-aware bitrate scaling hooks in WebRTC.

## Production Reliability Enhancements (Refined)
- **Camera2 Fix:** Graceful track stopping in `WebRtcSessionManager` to prevent `CameraAccessException`.
- **ANR Prevention:** All Bluetooth, BLE, and Socket operations refactored to `Dispatchers.IO` and `Dispatchers.Default`.
- **Visibility Restore:** Restored "Make Me Visible" (Discoverable) button in Bluetooth settings.
- **Robust BLE Decoding:** Corrected `ByteArray` decoding in `AndroidPresence` to avoid runtime crashes during discovery.

### **4. Adaptive Bitrate**
- **Link-Aware Scaling:** `LinkOrchestrator` recommends bitrates (200kbps for BLE, 5Mbps for LAN) based on the active transport priority.
- **Dynamic Optimization:** Hooks added to `WebRtcSessionManager` to adjust bandwidth mid-call.
