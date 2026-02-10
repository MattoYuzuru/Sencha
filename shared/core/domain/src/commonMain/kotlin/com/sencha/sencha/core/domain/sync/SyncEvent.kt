package com.sencha.sencha.core.domain.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class EventPayloadEnvelope(
    val schemaVersion: Int,
    val data: JsonElement,
)

@Serializable
data class SyncEvent(
    val eventId: String,
    val deviceId: String,
    val chatId: String,
    val type: String,
    val payload: EventPayloadEnvelope,
    val createdAtEpochMillis: Long,
)

object SyncEventTypes {
    const val CHAT_CREATED = "chat.created"
    const val MESSAGE_CREATED = "message.created"
    const val ARTIFACT_CREATED = "artifact.created"
    const val ARTIFACT_UPDATED = "artifact.updated"
    const val BLOB_UPLOADED = "blob.uploaded"
}
