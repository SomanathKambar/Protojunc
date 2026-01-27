# 📱 Protojunc: Simple User & Technical Guide

Welcome to **Protojunc**! This guide explains what this app does, how it works behind the scenes, and how you can get it running. We have designed this to be easy to understand even if you aren't a "tech expert."

---

## 🌟 1. What is Protojunc?
Protojunc is a "Universal Communication" app. Think of it like a smart walkie-talkie that can send video, voice, and text. 

**The Special Part:** It works even if you don't have internet! It can talk to other phones nearby using Bluetooth or local Wi-Fi, or it can talk across the world using a central server.

### Key Features:
*   **Video & Voice Calls:** High-quality P2P (Phone-to-Phone) calls.
*   **Offline Mode:** Connect via Bluetooth when there is no cell service.
*   **Smart Handover:** If you walk out of Bluetooth range, it can automatically try to switch to the Internet to keep the call alive.
*   **Clinical Dashboard:** A special "Live View" for teams to see status updates in real-time.

---

## 🏗 2. How it Works (The Simple Version)

To make a call, the app uses two main parts:

### A. The "Switchboard Operator" (The Server)
Imagine two people in a dark room trying to find each other to shake hands. They can't see each other, so they call out to a "Friend" (the Server) standing in the hallway. 
1.  **Phone A** tells the Server: "I am here, tell Phone B to find me."
2.  **The Server** tells **Phone B**: "Phone A is ready, here is how to reach them."
3.  Once they "shake hands," the Server steps away. The two phones now talk **directly** to each other. This makes the call fast and private.

### B. The "Handshake" (SDP & ICE)
*   **SDP (The Business Card):** When you start a call, your phone creates a "Business Card" (called an SDP). It contains technical details about what kind of video your phone supports.
*   **ICE (The Map):** Your phone also looks for all possible ways to be reached (Wi-Fi, Data, etc.) and sends these "Map Pins" (ICE Candidates) to the other phone.

---

## 🚀 3. How to Start & Run

### Step 1: Start the Signaling Server
The server must be running first so the phones can find each other.
1.  Open a terminal on your computer.
2.  Navigate to the project folder.
3.  Run the command:  
    `./gradlew :server:run`
4.  The server is now live! You can see who is connected by opening `http://localhost:8080/dashboard` in your browser.

### Step 2: Start the Android App
1.  Open the project in **Android Studio**.
2.  Connect two Android phones to your computer.
3.  Click **Run** to install the app on both phones.
4.  **Important:** Make sure both phones are on the same Wi-Fi network as your computer.

### Step 3: Connect Devices
1.  On **Phone A**, tap "Online Call" and select **"Start Call" (Host)**.
2.  On **Phone B**, tap "Online Call" and select **"Join Call"**.
3.  The phones will "handshake" via the server and the video will start!

---

## 🛠 4. Connection Modes Explained

| Mode | Best For... | How it Works |
| :--- | :--- | :--- |
| **Online Call** | Long distance | Uses the internet/server to find the peer. |
| **BLE / Bluetooth** | No Internet (Offline) | Uses Bluetooth to find and talk to nearby phones. |
| **QR Code** | Maximum Security | You scan a code on the other person's screen to connect manually. |
| **XMPP** | Office/Enterprise | Uses professional chat servers (like old-school office messengers). |

---

## 👨‍💻 5. Under the Hood (For Techies)

*   **Signaling:** We use **Ktor WebSockets**. This is a permanent open pipe between the phone and the server used *only* for the handshake.
*   **Media:** We use **WebRTC-KMP**. This handles the heavy lifting of encoding video and sending it over the network.
*   **Data Size:** We use an **SDP Minifier**. Standard "Business Cards" (SDPs) are huge. We shrink them down to tiny codes so they can even fit inside a single QR code or a small Bluetooth message.
*   **XMPP/Smack:** For enterprise modes, we use the **Smack library** on Android to handle professional messaging protocols.

---

## ❓ Troubleshooting
*   **"Can't see the other device":** Check that the **Server IP** in the app settings matches your computer's IP address.
*   **"Handshake Failed":** Ensure both devices have **Camera and Microphone permissions** turned on.
*   **"Echo/Self-View Only":** This usually happens if the "Room Code" is the same for two different groups. Use a unique Room Code!

---
*Created for the Protojunc Project - 2026*
