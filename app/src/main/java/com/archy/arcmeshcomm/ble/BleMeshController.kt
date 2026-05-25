package com.archy.arcmeshcomm.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.archy.arcmeshcomm.models.DeliveryStatus
import com.archy.arcmeshcomm.models.EventSeverity
import com.archy.arcmeshcomm.models.MeshPacket
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class BleReadiness(
    val supported: Boolean,
    val enabled: Boolean,
    val permissionsGranted: Boolean,
    val missingPermissions: List<String>
) {
    val ready: Boolean = supported && enabled && permissionsGranted
}

data class BlePeer(
    val id: String,
    val callsign: String,
    val address: String,
    val rssi: Int,
    val connected: Boolean
)

data class BleLocalIdentity(
    val id: String,
    val callsign: String
)

interface BleMeshListener {
    fun onPeerDiscovered(peer: BlePeer)
    fun onPacketReceived(packet: MeshPacket)
    fun onTransportEvent(title: String, detail: String, severity: EventSeverity)
}

class BleMeshController(private val context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val connections = ConcurrentHashMap<String, PeerConnection>()
    private val incomingFrames = ConcurrentHashMap<String, MutableMap<String, Array<String?>>>()
    private val serviceUuid = ParcelUuid(SERVICE_UUID)

    private var gattServer: BluetoothGattServer? = null
    private var listener: BleMeshListener? = null
    private var identity: BleLocalIdentity = BleLocalIdentity("NODE-LOCAL", "Local")
    private var started = false

    fun readiness(): BleReadiness {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
        }
        return BleReadiness(
            supported = adapter != null,
            enabled = adapter?.isEnabled == true,
            permissionsGranted = missing.isEmpty(),
            missingPermissions = missing
        )
    }

    fun setListener(listener: BleMeshListener?) {
        this.listener = listener
    }

    @SuppressLint("MissingPermission")
    fun start(identity: BleLocalIdentity): Boolean {
        this.identity = identity
        if (!readiness().ready) return false
        if (started) {
            listener?.onTransportEvent(
                "BLE scan active",
                "Still advertising and scanning as ${identity.callsign}. Keep Scan open on nearby devices too.",
                EventSeverity.INFO
            )
            return true
        }

        started = true
        openGattServer()
        startScanning()
        listener?.onTransportEvent(
            "BLE mesh online",
            "Scanning now. Advertising starts after the local GATT service is registered.",
            EventSeverity.SUCCESS
        )
        return true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        started = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        connections.values.forEach { it.gatt.close() }
        connections.clear()
        incomingFrames.clear()
        gattServer?.close()
        gattServer = null
        listener?.onTransportEvent("BLE mesh offline", "Physical scan, advertising, and GATT links stopped.", EventSeverity.INFO)
    }

    fun sendPacket(packet: MeshPacket, preferredPeerId: String? = null): Int {
        val peers = connections.values.filter { connection ->
            connection.packetCharacteristic != null &&
                (preferredPeerId == null || connection.peer?.id == preferredPeerId)
        }
        peers.forEach { it.outgoing.addAll(packet.toFrames()) }
        peers.forEach { flushWrites(it) }
        return peers.size
    }

    @SuppressLint("MissingPermission")
    private fun openGattServer() {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(
                BluetoothGattCharacteristic(
                    NODE_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
            )
            addCharacteristic(
                BluetoothGattCharacteristic(
                    PACKET_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )
            )
        }
        val server = bluetoothManager?.openGattServer(appContext, serverCallback)
        if (server == null) {
            listener?.onTransportEvent("BLE GATT server failed", "Android did not create a local GATT server for packet receiving.", EventSeverity.ERROR)
            return
        }
        gattServer = server.apply {
            if (!addService(service)) {
                listener?.onTransportEvent("BLE GATT service failed", "Android rejected the ArcMesh GATT service registration.", EventSeverity.ERROR)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            listener?.onTransportEvent("BLE advertising unavailable", "This adapter cannot advertise as a mesh receiver.", EventSeverity.WARNING)
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(serviceUuid)
            .setIncludeDeviceName(false)
            .build()
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            listener?.onTransportEvent("BLE scanning unavailable", "This adapter cannot scan for nearby mesh advertisements.", EventSeverity.WARNING)
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice, rssi: Int) {
        if (!started || connections.containsKey(device.address)) return
        listener?.onPeerDiscovered(
            BlePeer(
                id = device.address.fallbackPeerId(),
                callsign = "Nearby ${device.address.takeLast(5)}",
                address = device.address,
                rssi = rssi,
                connected = false
            )
        )
        listener?.onTransportEvent("BLE advertiser seen", "Found ArcMesh advertisement from ${device.address.takeLast(5)} at $rssi dBm.", EventSeverity.INFO)
        val callback = ClientCallback(device.address, rssi)
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, callback)
        }
        connections[device.address] = PeerConnection(gatt = gatt, rssi = rssi)
    }

    @SuppressLint("MissingPermission")
    private fun flushWrites(connection: PeerConnection) {
        if (connection.writing) return
        val characteristic = connection.packetCharacteristic ?: return
        val frame = connection.outgoing.poll() ?: return
        connection.writing = true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            connection.gatt.writeCharacteristic(characteristic, frame.toByteArray(Charsets.UTF_8), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = frame.toByteArray(Charsets.UTF_8)
            @Suppress("DEPRECATION")
            if (connection.gatt.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
        }
        if (result != BluetoothGatt.GATT_SUCCESS) {
            connection.writing = false
            listener?.onTransportEvent("BLE packet write failed", "Could not enqueue a packet frame for ${connection.peer?.callsign ?: connection.gatt.device.address}.", EventSeverity.ERROR)
        }
    }

    private fun handleIdentity(address: String, rssi: Int, value: ByteArray) {
        val text = value.toString(Charsets.UTF_8)
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        val peer = BlePeer(
            id = json.optString("id", address),
            callsign = json.optString("callsign", "Peer"),
            address = address,
            rssi = rssi,
            connected = true
        )
        connections[address]?.peer = peer
        listener?.onPeerDiscovered(peer)
        listener?.onTransportEvent("BLE peer linked", "${peer.callsign} connected at $rssi dBm.", EventSeverity.SUCCESS)
        flushWrites(connections[address] ?: return)
    }

    private fun handleFrame(address: String, value: ByteArray) {
        val frame = value.toString(Charsets.UTF_8)
        val parts = frame.split("|", limit = 4)
        if (parts.size != 4) return
        val packetId = parts[0]
        val index = parts[1].toIntOrNull() ?: return
        val total = parts[2].toIntOrNull() ?: return
        val chunk = parts[3]
        if (total <= 0 || index !in 0 until total) return

        val perDevice = incomingFrames.getOrPut(address) { ConcurrentHashMap() }
        val chunks = perDevice.getOrPut(packetId) { arrayOfNulls(total) }
        if (chunks.size != total) {
            perDevice.remove(packetId)
            return
        }
        chunks[index] = chunk
        if (chunks.all { it != null }) {
            perDevice.remove(packetId)
            val json = chunks.joinToString(separator = "") { it.orEmpty() }
            meshPacketFromJson(json)?.let { listener?.onPacketReceived(it) }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            listener?.onTransportEvent("BLE advertising active", "This phone is visible to nearby ArcMesh scanners.", EventSeverity.SUCCESS)
        }

        override fun onStartFailure(errorCode: Int) {
            listener?.onTransportEvent("BLE advertising failed", "Advertiser returned error $errorCode.", EventSeverity.ERROR)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
            if (serviceUuids.contains(serviceUuid)) {
                connect(result.device, result.rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener?.onTransportEvent("BLE scan failed", "Scanner returned error $errorCode.", EventSeverity.ERROR)
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (service.uuid != SERVICE_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener?.onTransportEvent("BLE GATT service ready", "Packet receive service is registered on this phone.", EventSeverity.SUCCESS)
                startAdvertising()
            } else {
                listener?.onTransportEvent("BLE GATT service failed", "Service registration returned status $status.", EventSeverity.ERROR)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != NODE_CHARACTERISTIC_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            val payload = JSONObject()
                .put("id", identity.id)
                .put("callsign", identity.callsign)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val slice = if (offset in payload.indices) payload.copyOfRange(offset, payload.size) else ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == PACKET_CHARACTERISTIC_UUID) {
                handleFrame(device.address, value)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }

    private inner class ClientCallback(private val address: String, private val rssi: Int) : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener?.onTransportEvent("BLE GATT connected", "Discovering ArcMesh service on ${address.takeLast(5)}.", EventSeverity.INFO)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connections.remove(address)
                gatt.close()
                listener?.onTransportEvent("BLE GATT disconnected", "Peer ${address.takeLast(5)} disconnected with status $status.", EventSeverity.WARNING)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onTransportEvent("BLE service discovery failed", "Peer ${address.takeLast(5)} returned status $status.", EventSeverity.ERROR)
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                listener?.onTransportEvent("BLE service missing", "Peer ${address.takeLast(5)} did not expose the ArcMesh service.", EventSeverity.ERROR)
                return
            }
            val connection = connections[address] ?: return
            val packetCharacteristic = service.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
            if (packetCharacteristic == null) {
                listener?.onTransportEvent("BLE packet endpoint missing", "Peer ${address.takeLast(5)} has no packet write characteristic.", EventSeverity.ERROR)
                return
            }
            connection.packetCharacteristic = packetCharacteristic
            gatt.requestMtu(TARGET_MTU)
            val nodeCharacteristic = service.getCharacteristic(NODE_CHARACTERISTIC_UUID)
            if (nodeCharacteristic == null) {
                listener?.onTransportEvent("BLE identity missing", "Peer ${address.takeLast(5)} has no node identity characteristic.", EventSeverity.WARNING)
                return
            }
            if (!gatt.readCharacteristic(nodeCharacteristic)) {
                listener?.onTransportEvent("BLE identity read failed", "Android refused to start identity read for ${address.takeLast(5)}.", EventSeverity.ERROR)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            connections[address]?.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == NODE_CHARACTERISTIC_UUID) {
                handleIdentity(address, rssi, value)
            }
        }

        @Deprecated("Used on Android 12L and older.")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            onCharacteristicRead(gatt, characteristic, characteristic.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val connection = connections[address] ?: return
            connection.writing = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onTransportEvent("BLE packet write failed", "Peer ${connection.peer?.callsign ?: address} rejected a packet frame.", EventSeverity.ERROR)
            }
            flushWrites(connection)
        }
    }

    private data class PeerConnection(
        val gatt: BluetoothGatt,
        val rssi: Int,
        var mtu: Int = DEFAULT_MTU,
        var packetCharacteristic: BluetoothGattCharacteristic? = null,
        var peer: BlePeer? = null,
        var writing: Boolean = false,
        val outgoing: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    )

    companion object {
        private val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val NODE_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private const val TARGET_MTU = 512
        private const val DEFAULT_MTU = 23
        private const val FRAME_BYTES = 160

        private fun String.fallbackPeerId(): String {
            return "BLE-${abs(hashCode()).toString(16).uppercase()}"
        }

        fun requiredPermissions(): List<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

        private fun MeshPacket.toFrames(): List<String> {
            val json = toJson()
            val chunkSize = max(48, FRAME_BYTES - id.length - 8)
            val total = max(1, (json.length + chunkSize - 1) / chunkSize)
            return (0 until total).map { index ->
                val start = index * chunkSize
                val end = min(json.length, start + chunkSize)
                "$id|$index|$total|${json.substring(start, end)}"
            }
        }

        private fun MeshPacket.toJson(): String {
            return JSONObject()
                .put("id", id)
                .put("senderId", senderId)
                .put("receiverId", receiverId)
                .put("timestamp", timestamp)
                .put("hopCount", hopCount)
                .put("ttl", ttl)
                .put("nonce", nonce)
                .put("cipherText", cipherText)
                .put("checksum", checksum)
                .put("status", status.name)
                .toString()
        }

        private fun meshPacketFromJson(json: String): MeshPacket? {
            return runCatching {
                val item = JSONObject(json)
                MeshPacket(
                    id = item.getString("id"),
                    senderId = item.getString("senderId"),
                    receiverId = item.getString("receiverId"),
                    timestamp = item.getLong("timestamp"),
                    hopCount = item.getInt("hopCount"),
                    ttl = item.getInt("ttl"),
                    nonce = item.getString("nonce"),
                    cipherText = item.getString("cipherText"),
                    checksum = item.getString("checksum"),
                    status = DeliveryStatus.valueOf(item.optString("status", DeliveryStatus.SENT.name))
                )
            }.getOrNull()
        }
    }
}
