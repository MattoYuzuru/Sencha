package com.sencha.sencha.server

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    val code: String,
    val deviceId: String,
    val devicePublicKeyHex: String,
    val deviceName: String? = null,
)

@Serializable
data class RegisterDeviceResponse(
    val userId: String,
    val deviceId: String,
    val sessionToken: String,
)

@Serializable
data class WhoAmIResponse(
    val userId: String,
    val deviceId: String,
)

@Serializable
data class EventPayloadEnvelope(
    val schemaVersion: Int,
    val data: kotlinx.serialization.json.JsonElement,
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

@Serializable
data class EventBatchRequest(
    val events: List<SyncEvent>,
)

@Serializable
data class EventBatchResponse(
    val accepted: Int,
)

@Serializable
data class EventsSinceResponse(
    val events: List<SyncEvent>,
    val nextCursor: Long,
)

@Serializable
data class PresignRequest(
    val operation: PresignOperation,
    val chatId: String,
    val blobId: String,
    val sha256: String,
    val mime: String,
    val size: Long,
    val extension: String,
)

@Serializable
enum class PresignOperation {
    UPLOAD,
    DOWNLOAD,
}

@Serializable
data class PresignResponse(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val key: String,
    val expiresAtEpochMillis: Long,
)

@Serializable
data class NodeUpsertRequest(
    val nodeId: String? = null,
    val name: String,
    val address: String,
    val lastSeenEpochMillis: Long? = null,
)

@Serializable
data class NodeInfo(
    val nodeId: String,
    val name: String,
    val address: String,
    val lastSeenEpochMillis: Long?,
)

@Serializable
data class NodesResponse(
    val nodes: List<NodeInfo>,
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val requestId: String? = null,
)
