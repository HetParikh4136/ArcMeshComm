# ArcMeshComm

ArcMeshComm is a native Android offline mesh communication prototype built with Kotlin and Jetpack Compose. The app now includes a working local mesh engine, AES-GCM packet encryption, node discovery simulation, store-and-forward delivery, packet inspection, persisted chat history, BLE readiness checks, and a foreground relay-monitor service.

## Implemented Features

- Modular Kotlin codebase split into UI, mesh, crypto, BLE, service, database, and model packages.
- Compose app with Dashboard, Chat, Nodes, Packets, and Guide screens.
- AES-256/GCM encryption for every outbound and simulated inbound message packet.
- Flood-style routing model with hop counts, TTL, nonce, checksum, and delivery status.
- Store-and-forward behavior for offline nodes, including queue flushing when a route returns.
- Local message persistence using SharedPreferences-backed storage.
- BLE hardware and runtime-permission readiness checks for Android 8+ through Android 15/16 targets.
- Foreground service scaffold for background mesh monitoring.

## Project Structure

```text
app/src/main/java/com/archy/arcmeshcomm/
├── ble/          BLE capability and permission readiness
├── crypto/       AES-GCM encryption helpers
├── database/     Local message persistence
├── mesh/         Mesh state engine and routing simulation
├── models/       Shared domain models
├── service/      Foreground relay-monitor service
├── ui/           Compose application screens
└── ui/theme/     Material 3 theme
```

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Notes

The app is fully usable as an offline mesh prototype on one device: it encrypts, stores, routes, queues, and inspects packets locally. BLE over-air GATT packet exchange is represented by Android permissions/readiness and service hooks so it can be connected to physical multi-device transport next.
