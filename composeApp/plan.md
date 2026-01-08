# KMP Serverless P2P Collaboration Project Plan (Native UI Edition)

## Phase 1: Foundation [COMPLETED]
- [x] Setup Version Catalog (2026 Stack)
- [x] Configure `build.gradle.kts` for Shared (Android + iOS targets)
- [ ] Define Platform-Native entry points (iOS: Framework / Android: Library)

## Phase 2: Serverless Signaling & Protocol [IN-PROGRESS]
- [ ] Define Protobuf Handshake Schema in `commonMain`
- [ ] Implement MVI State Machine (Common Logic for Connection)

## Phase 3: Hardware Discovery (BLE/QR) [PENDING]
- [ ] Shared Kable (BLE) logic
- [ ] Shared QR decoding logic (Binary -> SDP)

## Phase 4: WebRTC Media Pipeline [PENDING]
- [ ] PeerConnection Management in KMP
- [ ] Native Media Hooks (Android Foreground Service / iOS ReplayKit)

## Phase 5: Persistence & Registry [PENDING]
- [ ] Room KMP for Peer History
- [ ] Okio for File Storage (Snapshots)
