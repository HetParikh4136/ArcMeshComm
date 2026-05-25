package com.archy.arcmeshcomm.database

import android.content.Context
import com.archy.arcmeshcomm.models.DeliveryStatus
import com.archy.arcmeshcomm.models.MeshMessage
import com.archy.arcmeshcomm.models.MessageDirection
import java.util.Base64

class LocalMessageStore(context: Context) {
    private val prefs = context.getSharedPreferences("arc_mesh_store", Context.MODE_PRIVATE)

    fun loadMessages(): List<MeshMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 9) return@mapNotNull null
            runCatching {
                MeshMessage(
                    id = decode(parts[0]),
                    peerId = decode(parts[1]),
                    senderName = decode(parts[2]),
                    body = decode(parts[3]),
                    timestamp = parts[4].toLong(),
                    direction = MessageDirection.valueOf(parts[5]),
                    status = DeliveryStatus.valueOf(parts[6]),
                    route = decode(parts[7]).split(",").filter { it.isNotBlank() },
                    encryptedPreview = decode(parts[8])
                )
            }.getOrNull()
        }.toList()
    }

    fun saveMessages(messages: List<MeshMessage>) {
        val encoded = messages.takeLast(MAX_MESSAGES).joinToString("\n") { message ->
            listOf(
                encode(message.id),
                encode(message.peerId),
                encode(message.senderName),
                encode(message.body),
                message.timestamp.toString(),
                message.direction.name,
                message.status.name,
                encode(message.route.joinToString(",")),
                encode(message.encryptedPreview)
            ).joinToString("|")
        }
        prefs.edit().putString(KEY_MESSAGES, encoded).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }

    private fun encode(value: String): String {
        return Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun decode(value: String): String {
        return Base64.getDecoder().decode(value).toString(Charsets.UTF_8)
    }

    private companion object {
        const val KEY_MESSAGES = "messages"
        const val MAX_MESSAGES = 200
    }
}
