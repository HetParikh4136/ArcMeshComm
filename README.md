# ArcMeshComm

ArcMeshComm is a native Android offline mesh communication prototype built with Kotlin and Jetpack Compose. The app now includes a working local mesh engine, AES-GCM packet encryption, live BLE GATT packet transfer between nearby devices, store-and-forward delivery, packet inspection, persisted chat history, BLE readiness checks, and a foreground relay-monitor service.

## Implemented Features

- Modular Kotlin codebase split into UI, mesh, crypto, BLE, service, database, and model packages.
- Compose app with Dashboard, Chat, Nodes, Packets, and Guide screens.
- AES-256/GCM encryption for every outbound, inbound, and relayed message packet.
- Flood-style routing model with hop counts, TTL, nonce, checksum, and delivery status.
- Store-and-forward behavior for offline nodes, including queue flushing when a route returns.
- Local message persistence using SharedPreferences-backed storage.
- Persisted per-install node identity so multiple physical devices can distinguish each other.
- BLE advertising, scanning, GATT peer identity reads, chunked packet writes, and TTL relay forwarding for Android 8+ through Android 15/16 targets.
- Foreground service scaffold for background mesh monitoring.

## Project Structure

```text
app/src/main/java/com/archy/arcmeshcomm/
├── ble/          BLE readiness, scanning, advertising, GATT packet transport
├── crypto/       AES-GCM encryption helpers
├── database/     Local message persistence
├── mesh/         Mesh state engine, physical receive, and relay handling
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

The app is usable as a single-device mesh prototype and as a physical multi-device BLE prototype. Nearby ArcMeshComm installs advertise a shared service UUID, read each other's node identity over GATT, exchange encrypted packet frames through a write characteristic, and forward packets not addressed to the local node while decrementing TTL.
