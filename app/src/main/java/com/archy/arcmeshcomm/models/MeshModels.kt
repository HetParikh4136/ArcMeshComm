package com.archy.arcmeshcomm.models

enum class NodeStatus {
    ONLINE,
    RELAY,
    OFFLINE
}

enum class MessageDirection {
    OUTBOUND,
    INBOUND,
    SYSTEM
}

enum class DeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    FAILED
}

enum class EventSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class MeshNode(
    val id: String,
    val callsign: String,
    val role: String,
    val status: NodeStatus,
    val rssi: Int,
    val hops: Int,
    val batteryPercent: Int,
    val lastSeenMillis: Long
)

data class MeshMessage(
    val id: String,
    val peerId: String,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val direction: MessageDirection,
    val status: DeliveryStatus,
    val route: List<String>,
    val encryptedPreview: String
)

data class MeshPacket(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val timestamp: Long,
    val hopCount: Int,
    val ttl: Int,
    val nonce: String,
    val cipherText: String,
    val checksum: String,
    val status: DeliveryStatus
)

data class NetworkEvent(
    val id: String,
    val title: String,
    val detail: String,
    val timestamp: Long,
    val severity: EventSeverity
)

data class MeshUiState(
    val localNode: MeshNode,
    val nodes: List<MeshNode>,
    val messages: List<MeshMessage>,
    val packets: List<MeshPacket>,
    val events: List<NetworkEvent>,
    val selectedPeerId: String,
    val radioEnabled: Boolean,
    val serviceRunning: Boolean,
    val activeKeyLabel: String
)
