# Directo: P2P WebRTC Video/Voice Platform - Enhanced Plan

## Phase 1: Modular Architecture [COMPLETED]
- [x] **Strategy Pattern Refactor**: Decouple Signaling and Discovery from WebRTC logic.
- [x] **Core Interfaces**: Defined `SignalingClient` and `DiscoveryClient` for plug-and-play support.
- [x] **Call Orchestrator**: Implemented `CallSessionOrchestrator` to manage multi-modal handshakes.
- [x] **Aggressive SDP Minification**: Token-based compression to fit payloads in BLE/QR.

## Phase 2: Local High-Speed (WiFi Direct) [IN-PROGRESS]
- [ ] **WiFi Direct Provider**: Implement `DiscoveryClient` using Android `WifiP2pManager`.
- [ ] **Socket Signaling**: Establish direct TCP/UDP socket for signaling exchange over WiFi Direct.
- [ ] **Fast Handshake**: Auto-connect and exchange SDP without manual pairing where possible.

## Phase 3: Legacy Robustness (Bluetooth Sockets) [PENDING]
- [ ] **RFCOMM Implementation**: Use Classic Bluetooth Sockets for signaling where BLE fails.
- [ ] **Pairing Flow**: Integrated pairing request management in the connection flow.

## Phase 4: Internet P2P (Ktor Signaling Server) [PENDING]
- [ ] **Ktor WebSocket Server**: Implement signaling room logic in the `server` module.
- [ ] **STUN/TURN Integration**: Configure production ICE servers for NAT traversal.
- [ ] **Online Discovery**: Peer lookup via Room Codes over the internet.

## Phase 5: Enterprise Integration (XMPP) [PENDING]
- [ ] **XMPP Signaling**: Implement `SignalingClient` using XMPP Jingle (XEP-0166).
- [ ] **Intermittent Tracking**: UI/UX for tracking connection progress and sophisticated error handling.
- [ ] **Navigation & Persistence**: Advanced call history and contact management.

## Phase 6: Production WebRTC [PENDING]
- [ ] **Media Optimization**: Dynamic bitrate adjustment based on network conditions.
- [ ] **Cross-Platform Parity**: Ensure iOS targets are fully functional with the new modular core.