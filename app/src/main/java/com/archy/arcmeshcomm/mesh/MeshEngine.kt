package com.archy.arcmeshcomm.mesh

import android.content.Context
import com.archy.arcmeshcomm.crypto.AesGcmCipher
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
import kotlin.math.max
import kotlin.random.Random

class MeshEngine private constructor(context: Context) {
    private val store = LocalMessageStore(context.applicationContext)
    private val cipher = AesGcmCipher("ArcMeshComm local demo session key")

    private val localNode = MeshNode(
        id = "NODE-ALPHA",
        callsign = "Alpha",
        role = "Command handset",
        status = NodeStatus.ONLINE,
        rssi = -32,
        hops = 0,
        batteryPercent = 91,
        lastSeenMillis = System.currentTimeMillis()
    )

    private val initialNodes = listOf(
        MeshNode("NODE-BRAVO", "Bravo", "Field relay", NodeStatus.ONLINE, -48, 1, 84, now()),
        MeshNode("NODE-CHARLIE", "Charlie", "Medic team", NodeStatus.RELAY, -67, 2, 71, now() - 90_000),
        MeshNode("NODE-DELTA", "Delta", "Supply point", NodeStatus.OFFLINE, -91, 3, 58, now() - 680_000)
    )

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
            selectedPeerId = "NODE-BRAVO",
            radioEnabled = true,
            serviceRunning = false,
            activeKeyLabel = "AES-256/GCM local session"
        )
    )

    val state: StateFlow<MeshUiState> = _state

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
        val status = if (peer.status == NodeStatus.OFFLINE) DeliveryStatus.QUEUED else DeliveryStatus.DELIVERED
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

        _state.update {
            val updatedMessages = (it.messages + message).takeLast(200)
            store.saveMessages(updatedMessages)
            it.copy(
                messages = updatedMessages,
                packets = (listOf(packet) + it.packets).take(80),
                events = (listOf(
                    event(
                        title = if (status == DeliveryStatus.QUEUED) "Packet queued" else "Packet delivered",
                        detail = "${peer.callsign} via ${route.joinToString(" -> ")}",
                        severity = if (status == DeliveryStatus.QUEUED) EventSeverity.WARNING else EventSeverity.SUCCESS
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
        val callsign = listOf("Echo", "Foxtrot", "Hotel", "Sierra", "Vega").random()
        val id = "NODE-${callsign.uppercase()}"
        val node = MeshNode(
            id = id,
            callsign = callsign,
            role = listOf("Responder", "Relay scout", "Operations team").random(),
            status = NodeStatus.ONLINE,
            rssi = Random.nextInt(-76, -38),
            hops = Random.nextInt(1, 4),
            batteryPercent = Random.nextInt(42, 99),
            lastSeenMillis = now()
        )
        _state.update {
            val nodes = (listOf(node) + it.nodes.filterNot { existing -> existing.id == id }).take(8)
            it.copy(
                nodes = nodes,
                selectedPeerId = node.id,
                events = (listOf(event("Node discovered", "${node.callsign} advertising at ${node.rssi} dBm.", EventSeverity.SUCCESS)) + it.events).take(80)
            )
        }
    }

    fun retryQueuedPackets() {
        _state.update { current ->
            val promotedNodes = current.nodes.map {
                if (it.status == NodeStatus.OFFLINE) it.copy(status = NodeStatus.RELAY, rssi = -74, lastSeenMillis = now()) else it
            }
            val messages = current.messages.map {
                if (it.status == DeliveryStatus.QUEUED) it.copy(status = DeliveryStatus.DELIVERED) else it
            }
            val packets = current.packets.map {
                if (it.status == DeliveryStatus.QUEUED) it.copy(status = DeliveryStatus.DELIVERED) else it
            }
            store.saveMessages(messages)
            current.copy(
                nodes = promotedNodes,
                messages = messages,
                packets = packets,
                events = (listOf(event("Store-and-forward flush", "Queued packets were delivered through recovered relay paths.", EventSeverity.SUCCESS)) + current.events).take(80)
            )
        }
    }

    fun toggleRadio() {
        _state.update {
            val enabled = !it.radioEnabled
            it.copy(
                radioEnabled = enabled,
                events = (listOf(event(if (enabled) "Radio enabled" else "Radio paused", "Local mesh transport state changed.", EventSeverity.INFO)) + it.events).take(80)
            )
        }
    }

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
                route = listOf("Alpha"),
                encryptedPreview = "local-only"
            )
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
