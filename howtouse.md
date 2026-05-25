# ArcMeshComm - How to Use

## Requirements

- Android Studio Ladybug or newer.
- JDK 17 or newer recommended for the current Gradle/Android plugin stack.
- Android device or emulator running API 26+.
- Bluetooth enabled on a physical device for BLE readiness checks.

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

The Dashboard shows mesh status, active packet counts, queued messages, and recent network events. Use **Message** to open Chat or **Simulate RX** to generate an encrypted inbound relay packet.

### Chat

Select a peer chip at the top, type a message, and tap send. The app encrypts the message with AES-GCM, creates a packet with nonce/checksum/TTL metadata, stores it locally, and marks it delivered or queued depending on the selected node state.

### Nodes

The Nodes screen shows BLE readiness and known mesh peers. Tap **Permissions** to grant Android nearby-device permissions. Tap **Discover** to add a simulated nearby node and switch the active chat target.

### Packets

Packets lists encrypted packet metadata, including sender, receiver, hop count, TTL, nonce preview, cipher preview, checksum, and delivery status. **Flush Queue** simulates store-and-forward delivery when an offline route becomes available. **Clear** resets local message and packet history.

### Guide

The Guide screen provides the in-app quick workflow for start, discovery, messaging, relay, and inspection.

## Background Service

Use the Bluetooth icon in the top app bar to toggle the foreground relay-monitor service. Android may show a persistent notification while it is active.

## Current Prototype Behavior

ArcMeshComm is implemented as a single-device working mesh prototype. It performs real local encryption, packet creation, queueing, persistence, and UI state management. BLE hardware readiness is checked through Android APIs; physical multi-device BLE GATT transfer is the next integration layer.
