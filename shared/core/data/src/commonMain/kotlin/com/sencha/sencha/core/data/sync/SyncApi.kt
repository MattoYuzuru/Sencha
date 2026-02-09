package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.NodeInfo
import com.sencha.sencha.core.domain.sync.SyncEvent
import kotlinx.serialization.Serializable

interface SyncApi {
    suspend fun registerDevice(request: RegisterDeviceRequest): RegisterDeviceResponse
    suspend fun whoAmI(): WhoAmIResponse
    suspend fun uploadEvents(events: List<SyncEvent>): EventBatchResponse
    suspend fun fetchEventsSince(cursor: Long?, limit: Int): EventsSinceResponse
    suspend fun presign(request: PresignRequest): PresignResponse
    suspend fun fetchNodes(): List<NodeInfo>
    suspend fun upsertNode(request: NodeUpsertRequest): NodeInfo
    suspend fun health(): HealthResponse
}

@Serializable
data class RegisterDeviceRequest(
    val code: String,
    val deviceId: String,
    val devicePublicKeyHex: String,
    val deviceName: String?,
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
    val nodeId: String?,
    val name: String,
    val address: String,
    val lastSeenEpochMillis: Long?,
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
)
