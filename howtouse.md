# ArcMeshComm - How to Use

## Requirements

- Android Studio Ladybug or newer.
- JDK 17 or newer recommended for the current Gradle/Android plugin stack.
- Android device or emulator running API 26+.
- Bluetooth enabled on two or more physical devices for live BLE packet transfer.

## Running the App

1. Open the `ArcMeshComm` folder in Android Studio.
2. Let Gradle sync finish.
3. Select an emulator or physical Android device.
4. Run the `app` configuration, or build from PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

## App Screens

### Dashboard

The Dashboard shows mesh status, active packet counts, queued messages, and recent network events. Use **Message** to open Chat after a physical peer appears in Nodes.

### Chat

Select a peer chip at the top, type a message, and tap send. The app encrypts the message with AES-GCM, creates a packet with nonce/checksum/TTL metadata, stores it locally, and writes it over BLE when a physical peer link exists.

### Nodes

The Nodes screen shows BLE readiness, this device's node identity, and physical mesh peers. Tap **Permissions** to grant Android nearby-device permissions. Tap **Scan** on each nearby device to advertise, scan, connect over GATT, and add physical peers to the node list. Bravo/Charlie/Delta are no longer preloaded; an empty peer list means no physical BLE peer has linked yet.

If peers do not appear, watch the BLE status line on both devices. **BLE advertising active** means the phone is visible. **BLE advertiser seen** means the other phone was detected. **BLE GATT connected** means the devices started linking. **BLE peer linked** means the node identity was read and messaging should be available. Any failed/missing status there points to the layer that is blocking discovery.

### Packets

Packets lists encrypted packet metadata, including sender, receiver, hop count, TTL, nonce preview, cipher preview, checksum, and delivery status. **Flush Queue** pushes queued packets to connected BLE peers when possible. **Clear** resets local message and packet history.

### Guide

The Guide screen provides the in-app quick workflow for start, discovery, messaging, relay, and inspection.

## Background Service

Use the Bluetooth icon in the top app bar to toggle the foreground relay-monitor service. Android may show a persistent notification while it is active.

## Current Prototype Behavior

ArcMeshComm now supports physical multi-device BLE transfer. It performs real local encryption, packet creation, queueing, persistence, GATT identity exchange, chunked packet writes, inbound decryption, and TTL-based packet relay. Emulators can still exercise the local prototype flow, but live BLE transfer requires physical devices.
