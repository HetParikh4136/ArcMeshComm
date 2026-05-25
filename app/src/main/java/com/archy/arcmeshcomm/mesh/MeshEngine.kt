package com.archy.arcmeshcomm.mesh

import android.content.Context
import com.archy.arcmeshcomm.ble.BleLocalIdentity
import com.archy.arcmeshcomm.ble.BleMeshController
import com.archy.arcmeshcomm.ble.BleMeshListener
import com.archy.arcmeshcomm.ble.BlePeer
import com.archy.arcmeshcomm.crypto.AesGcmCipher
import com.archy.arcmeshcomm.crypto.EncryptedPayload
import com.archy.arcmeshcomm.database.LocalMessageStore
import com.archy.arcmeshcomm.models.DeliveryStatus
import com.archy.arcmeshcomm.models.EventSeverity
import com.archy.arcmeshcomm.models.MeshMessage
import com.archy.arcmeshcomm.models.MeshNode
import com.archy.arcmeshcomm.models.MeshPacket
import com.archy.arcmeshcomm.models.MeshUiState
import com.archy.arcmeshcomm.models.MessageDirection
import com.archy.arcmeshcomm.models.NetworkEvent
import com.archy.arcmeshcomm.models.NodeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class MeshEngine private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = LocalMessageStore(appContext)
    private val cipher = AesGcmCipher("ArcMeshComm local demo session key")
    private val bleController = BleMeshController(appContext)
    private val seenPackets = ConcurrentHashMap.newKeySet<String>()

    private val localNode = loadLocalNode()

    private val initialNodes = emptyList<MeshNode>()

    private val _state = MutableStateFlow(
        MeshUiState(
            localNode = localNode,
            nodes = initialNodes,
            messages = seedMessages(),
            packets = emptyList(),
            events = listOf(
                event("Mesh core ready", "AES-GCM session key loaded. Flood routing TTL is set to 5.", EventSeverity.SUCCESS),
                event("BLE adapter standing by", "Grant nearby-device permissions to enable live discovery.", EventSeverity.INFO)
            ),
            selectedPeerId = "",
            radioEnabled = true,
            serviceRunning = false,
            activeKeyLabel = "AES-256/GCM local session"
        )
    )

    val state: StateFlow<MeshUiState> = _state

    init {
        bleController.setListener(object : BleMeshListener {
            override fun onPeerDiscovered(peer: BlePeer) {
                upsertBlePeer(peer)
            }

            override fun onPacketReceived(packet: MeshPacket) {
                receivePhysicalPacket(packet)
            }

            override fun onTransportEvent(title: String, detail: String, severity: EventSeverity) {
                recordEvent(title, detail, severity)
            }
        })
    }

    fun selectPeer(peerId: String) {
        _state.update { it.copy(selectedPeerId = peerId) }
    }

    fun sendMessage(body: String) {
        val cleanBody = body.trim()
        if (cleanBody.isBlank()) return

        val current = _state.value
        val peer = current.nodes.firstOrNull { it.id == current.selectedPeerId } ?: return
        val route = routeTo(peer)
        val encrypted = cipher.encrypt(cleanBody)
        val status = if (peer.status == NodeStatus.OFFLINE) DeliveryStatus.QUEUED else DeliveryStatus.SENT
        val message = MeshMessage(
            id = uuid(),
            peerId = peer.id,
            senderName = current.localNode.callsign,
            body = cleanBody,
            timestamp = now(),
            direction = MessageDirection.OUTBOUND,
            status = status,
            route = route,
            encryptedPreview = encrypted.cipherText.take(28)
        )
        val packet = MeshPacket(
            id = uuid(),
            senderId = current.localNode.id,
            receiverId = peer.id,
            timestamp = now(),
            hopCount = max(1, route.size - 1),
            ttl = 5,
            nonce = encrypted.nonce,
            cipherText = encrypted.cipherText,
            checksum = encrypted.checksum,
            status = status
        )

        val directWrites = if (status == DeliveryStatus.QUEUED || !current.radioEnabled) {
            0
        } else {
            bleController.sendPacket(packet, peer.id).let { direct ->
                if (direct == 0) bleController.sendPacket(packet) else direct
            }
        }
        val finalStatus = when {
            status == DeliveryStatus.QUEUED -> DeliveryStatus.QUEUED
            directWrites > 0 -> DeliveryStatus.SENT
            else -> DeliveryStatus.DELIVERED
        }
        val finalMessage = message.copy(status = finalStatus)
        val finalPacket = packet.copy(status = finalStatus)

        _state.update {
            val updatedMessages = (it.messages + finalMessage).takeLast(200)
            store.saveMessages(updatedMessages)
            it.copy(
                messages = updatedMessages,
                packets = (listOf(finalPacket) + it.packets).take(80),
                events = (listOf(
                    event(
                        title = when (finalStatus) {
                            DeliveryStatus.QUEUED -> "Packet queued"
                            DeliveryStatus.SENT -> "Packet sent over BLE"
                            else -> "Packet delivered"
                        },
                        detail = if (directWrites > 0) {
                            "${peer.callsign} via $directWrites BLE link(s)."
                        } else {
                            "${peer.callsign} via ${route.joinToString(" -> ")}"
                        },
                        severity = if (finalStatus == DeliveryStatus.QUEUED) EventSeverity.WARNING else EventSeverity.SUCCESS
                    )
                ) + it.events).take(80)
            )
        }
    }

    fun simulateIncoming() {
        val current = _state.value
        val peer = current.nodes.filter { it.status != NodeStatus.OFFLINE }.randomOrNull() ?: return
        val samples = listOf(
            "Checkpoint clear. Continuing silent movement.",
            "Relay window open for the next ten minutes.",
            "Battery reserve stable. No infrastructure detected.",
            "Route update received. Holding current position."
        )
        val plainText = samples.random()
        val encrypted = cipher.encrypt(plainText)
        val decrypted = runCatching { cipher.decrypt(encrypted) }.getOrDefault(plainText)
        val route = routeTo(peer).reversed()
        val message = MeshMessage(
            id = uuid(),
            peerId = peer.id,
            senderName = peer.callsign,
            body = decrypted,
            timestamp = now(),
            direction = MessageDirection.INBOUND,
            status = DeliveryStatus.DELIVERED,
            route = route,
            encryptedPreview = encrypted.cipherText.take(28)
        )
        val packet = MeshPacket(
            id = uuid(),
            senderId = peer.id,
            receiverId = current.localNode.id,
            timestamp = now(),
            hopCount = max(1, route.size - 1),
            ttl = 5,
            nonce = encrypted.nonce,
            cipherText = encrypted.cipherText,
            checksum = encrypted.checksum,
            status = DeliveryStatus.DELIVERED
        )
        _state.update {
            val updatedMessages = (it.messages + message).takeLast(200)
            store.saveMessages(updatedMessages)
            it.copy(
                selectedPeerId = peer.id,
                messages = updatedMessages,
                packets = (listOf(packet) + it.packets).take(80),
                events = (listOf(event("Inbound message", "${peer.callsign} decrypted and stored locally.", EventSeverity.INFO)) + it.events).take(80)
            )
        }
    }

    fun discoverNode() {
        startPhysicalMesh()
    }

    fun retryQueuedPackets() {
        _state.update { current ->
            val writes = current.packets
                .filter { it.status == DeliveryStatus.QUEUED }
                .sumOf { bleController.sendPacket(it) }
            val promotedNodes = current.nodes.map {
                if (it.status == NodeStatus.OFFLINE) it.copy(status = NodeStatus.RELAY, rssi = -74, lastSeenMillis = now()) else it
            }
            val messages = current.messages.map {
                if (it.status == DeliveryStatus.QUEUED) it.copy(status = if (writes > 0) DeliveryStatus.SENT else DeliveryStatus.DELIVERED) else it
            }
            val packets = current.packets.map {
                if (it.status == DeliveryStatus.QUEUED) it.copy(status = if (writes > 0) DeliveryStatus.SENT else DeliveryStatus.DELIVERED) else it
            }
            store.saveMessages(messages)
            current.copy(
                nodes = promotedNodes,
                messages = messages,
                packets = packets,
                events = (listOf(event("Store-and-forward flush", if (writes > 0) "Queued packets were pushed to $writes BLE link(s)." else "Queued packets were delivered through recovered relay paths.", EventSeverity.SUCCESS)) + current.events).take(80)
            )
        }
    }

    fun toggleRadio() {
        val nextEnabled = !_state.value.radioEnabled
        if (nextEnabled) {
            startPhysicalMesh()
        } else {
            bleController.stop()
        }
        _state.update {
            it.copy(
                radioEnabled = nextEnabled,
                events = (listOf(event(if (nextEnabled) "Radio enabled" else "Radio paused", "Local mesh transport state changed.", EventSeverity.INFO)) + it.events).take(80)
            )
        }
    }

    fun startPhysicalMesh(): Boolean {
        val readiness = bleController.readiness()
        if (!readiness.ready) {
            recordEvent("BLE mesh not ready", "Bluetooth support, adapter state, or nearby-device permissions are missing.", EventSeverity.WARNING)
            return false
        }
        return bleController.start(BleLocalIdentity(localNode.id, localNode.callsign))
    }

    fun physicalMeshReady(): Boolean = bleController.readiness().ready

    fun setServiceRunning(running: Boolean) {
        _state.update {
            it.copy(
                serviceRunning = running,
                events = (listOf(event(if (running) "Foreground service active" else "Foreground service stopped", "Background mesh monitor state updated.", EventSeverity.INFO)) + it.events).take(80)
            )
        }
    }

    fun clearHistory() {
        store.clear()
        _state.update {
            it.copy(
                messages = seedMessages(),
                packets = emptyList(),
                events = (listOf(event("Local history cleared", "Message and packet logs were reset on this device.", EventSeverity.WARNING)) + it.events).take(80)
            )
        }
    }

    private fun seedMessages(): List<MeshMessage> {
        val persisted = store.loadMessages()
        if (persisted.isNotEmpty()) return persisted
        return listOf(
            MeshMessage(
                id = uuid(),
                peerId = "NODE-BRAVO",
                senderName = "System",
                body = "Secure local mesh is initialized. Pick a peer and send an encrypted packet.",
                timestamp = now(),
                direction = MessageDirection.SYSTEM,
                status = DeliveryStatus.DELIVERED,
                route = listOf(localNode.callsign),
                encryptedPreview = "local-only"
            )
        )
    }

    private fun upsertBlePeer(peer: BlePeer) {
        if (peer.id == localNode.id) return
        val node = MeshNode(
            id = peer.id,
            callsign = peer.callsign,
            role = "BLE mesh handset",
            status = if (peer.connected) NodeStatus.ONLINE else NodeStatus.RELAY,
            rssi = peer.rssi,
            hops = 1,
            batteryPercent = 100,
            lastSeenMillis = now()
        )
        _state.update {
            val nodes = (listOf(node) + it.nodes.filterNot { existing -> existing.id == node.id }).take(12)
            it.copy(
                nodes = nodes,
                selectedPeerId = node.id,
                events = (listOf(event("Physical peer discovered", "${node.callsign} is ready for BLE packet transfer.", EventSeverity.SUCCESS)) + it.events).take(80)
            )
        }
    }

    private fun receivePhysicalPacket(packet: MeshPacket) {
        if (packet.senderId == localNode.id || !seenPackets.add(packet.id)) return
        val current = _state.value
        val sender = current.nodes.firstOrNull { it.id == packet.senderId } ?: packet.senderId.asNode()

        if (packet.receiverId == localNode.id) {
            val decrypted = runCatching {
                cipher.decrypt(EncryptedPayload(packet.nonce, packet.cipherText, packet.checksum))
            }.getOrNull()
            if (decrypted == null) {
                recordEvent("Inbound decrypt failed", "Packet ${packet.id.take(8)} could not be opened with the local session key.", EventSeverity.ERROR)
                return
            }
            val message = MeshMessage(
                id = uuid(),
                peerId = sender.id,
                senderName = sender.callsign,
                body = decrypted,
                timestamp = now(),
                direction = MessageDirection.INBOUND,
                status = DeliveryStatus.DELIVERED,
                route = listOf(sender.callsign, localNode.callsign),
                encryptedPreview = packet.cipherText.take(28)
            )
            _state.update {
                val nodes = (listOf(sender.copy(status = NodeStatus.ONLINE, lastSeenMillis = now())) + it.nodes.filterNot { node -> node.id == sender.id }).take(12)
                val messages = (it.messages + message).takeLast(200)
                store.saveMessages(messages)
                it.copy(
                    nodes = nodes,
                    selectedPeerId = sender.id,
                    messages = messages,
                    packets = (listOf(packet.copy(status = DeliveryStatus.DELIVERED)) + it.packets).take(80),
                    events = (listOf(event("BLE packet received", "${sender.callsign} decrypted and stored locally.", EventSeverity.SUCCESS)) + it.events).take(80)
                )
            }
            return
        }

        if (packet.ttl <= 1 || !current.radioEnabled) {
            recordEvent("Relay packet dropped", "Packet ${packet.id.take(8)} reached TTL ${packet.ttl}.", EventSeverity.WARNING)
            return
        }

        val relayed = packet.copy(
            ttl = packet.ttl - 1,
            hopCount = packet.hopCount + 1,
            status = DeliveryStatus.SENT
        )
        val writes = bleController.sendPacket(relayed)
        _state.update {
            it.copy(
                packets = (listOf(relayed) + it.packets).take(80),
                events = (listOf(event("BLE packet relayed", "Forwarded ${packet.id.take(8)} to $writes nearby link(s).", EventSeverity.INFO)) + it.events).take(80)
            )
        }
    }

    private fun recordEvent(title: String, detail: String, severity: EventSeverity) {
        _state.update {
            it.copy(events = (listOf(event(title, detail, severity)) + it.events).take(80))
        }
    }

    private fun String.asNode(): MeshNode {
        val suffix = takeLast(4)
        return MeshNode(
            id = this,
            callsign = "Peer $suffix",
            role = "BLE mesh peer",
            status = NodeStatus.ONLINE,
            rssi = -70,
            hops = 1,
            batteryPercent = 100,
            lastSeenMillis = now()
        )
    }

    private fun loadLocalNode(): MeshNode {
        val prefs = appContext.getSharedPreferences("arc_mesh_identity", Context.MODE_PRIVATE)
        val existingId = prefs.getString("node_id", null)
        val existingCallsign = prefs.getString("callsign", null)
        val id = existingId ?: "NODE-${UUID.randomUUID().toString().take(8).uppercase()}"
        val callsign = existingCallsign ?: "Node ${id.takeLast(4)}"
        if (existingId == null || existingCallsign == null) {
            prefs.edit()
                .putString("node_id", id)
                .putString("callsign", callsign)
                .apply()
        }
        return MeshNode(
            id = id,
            callsign = callsign,
            role = "Command handset",
            status = NodeStatus.ONLINE,
            rssi = -32,
            hops = 0,
            batteryPercent = 91,
            lastSeenMillis = now()
        )
    }

    private fun routeTo(peer: MeshNode): List<String> {
        return when {
            peer.hops <= 1 -> listOf(localNode.callsign, peer.callsign)
            peer.status == NodeStatus.OFFLINE -> listOf(localNode.callsign, "Bravo", "Charlie", peer.callsign)
            else -> listOf(localNode.callsign, "Bravo", peer.callsign)
        }
    }

    companion object {
        @Volatile private var instance: MeshEngine? = null

        fun get(context: Context): MeshEngine {
            return instance ?: synchronized(this) {
                instance ?: MeshEngine(context.applicationContext).also { instance = it }
            }
        }

        private fun now(): Long = System.currentTimeMillis()
        private fun uuid(): String = UUID.randomUUID().toString()

        private fun event(title: String, detail: String, severity: EventSeverity): NetworkEvent {
            return NetworkEvent(uuid(), title, detail, now(), severity)
        }
    }
}
