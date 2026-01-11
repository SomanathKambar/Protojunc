import SwiftUI
import Shared

enum Screen: Hashable {
    case qrScan
    case bluetooth
    case videoCall
}

struct ContentView: View {
    @State private var path = NavigationPath()
    @State private var errorMessage: String?
    @State private var showError = false
    
    // In a real app, use a ViewModel. For simplicity here:
    private let sessionManager = WebRtcSessionManager()
    
    var body: some View {
        if #available(iOS 16.0, *) {
            NavigationStack(path: $path) {
                HomeView(path: $path, sessionManager: sessionManager, onError: { err in
                    self.errorMessage = err
                    self.showError = true
                })
                .alert("Connection Error", isPresented: $showError) {
                    Button("OK", role: .cancel) { }
                } message: {
                    Text(errorMessage ?? "Unknown Error")
                }
                .navigationDestination(for: Screen.self) { screen in
                    switch screen {
                    case .qrScan:
                        QRScanView(path: $path)
                    case .bluetooth:
                        BluetoothView(path: $path)
                    case .videoCall:
                        VideoCallView(path: $path, sessionManager: sessionManager)
                    }
                }
            }
        } else {
            Text("Requires iOS 16+")
        }
    }
}

import AVFoundation
import CoreBluetooth

struct HomeView: View {
    @Binding var path: NavigationPath
    let sessionManager: WebRtcSessionManager
    let onError: (String) -> Void
    
    @State private var isInitializing = false
    
    var body: some View {
        ZStack {
            VStack(spacing: 24) {
                Text("Protojunc P2P Video")
                    .font(.largeTitle).bold()
                
                Button(action: { startConnection(target: .qrScan) }) {
                    Text("Connect via QR Code")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                
                Button(action: { startConnection(target: .bluetooth) }) {
                    Text("Connect via Bluetooth")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
            }
            .padding()
            
            if isInitializing {
                Color.black.opacity(0.4).edgesIgnoringSafeArea(.all)
                VStack {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(1.5)
                    Text("Initializing...").foregroundColor(.white).padding()
                }
            }
        }
        .navigationTitle("Home")
    }
    
    private func startConnection(target: Screen) {
        // Real Permission Check
        AVCaptureDevice.requestAccess(for: .video) { cameraGranted in
            AVCaptureDevice.requestAccess(for: .audio) { audioGranted in
                DispatchQueue.main.async {
                    if cameraGranted && audioGranted {
                        isInitializing = true
                        sessionManager.createPeerConnection { err in
                            isInitializing = false
                            if let err = err {
                                onError(err.localizedDescription)
                            } else {
                                path.append(target)
                            }
                        }
                    } else {
                        onError("Camera and Microphone permissions are required.")
                    }
                }
            }
        }
    }
}
            
            Button(action: { path.append(Screen.bluetooth) }) {
                Text("Connect via Bluetooth")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.green)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
        }
        .padding()
        .navigationTitle("Home")
    }
}

struct QRScanView: View {
    @Binding var path: NavigationPath
    
    var body: some View {
        VStack {
            Spacer()
            Text("QR Scanner Placeholder")
            Spacer()
            Button("Simulate Scan") {
                path.append(Screen.videoCall)
            }
            .padding()
        }
        .navigationTitle("Scan QR")
    }
}

struct BluetoothView: View {
    @Binding var path: NavigationPath
    @State private var peers: [String] = [] // Mock
    
    var body: some View {
        List {
            Section(header: Text("Actions")) {
                Button("Broadcast Presence") {
                    // Call Shared Logic
                }
            }
            
            Section(header: Text("Nearby Peers")) {
                ForEach(peers, id: \.self) { peer in
                    Button(peer) {
                        path.append(Screen.videoCall)
                    }
                }
            }
        }
        .navigationTitle("Bluetooth")
    }
}

struct VideoCallView: View {
    @Binding var path: NavigationPath
    
    var body: some View {
        ZStack {
            Color.black.edgesIgnoringSafeArea(.all)
            
            Text("Remote Video")
                .foregroundColor(.white)
            
            VStack {
                Spacer()
                Button(action: { path.removeLast(path.count) }) {
                    Image(systemName: "phone.down.fill")
                        .font(.largeTitle)
                        .padding()
                        .background(Color.red)
                        .clipShape(Circle())
                        .foregroundColor(.white)
                }
                .padding(.bottom, 50)
            }
        }
        .navigationBarBackButtonHidden(true)
    }
}