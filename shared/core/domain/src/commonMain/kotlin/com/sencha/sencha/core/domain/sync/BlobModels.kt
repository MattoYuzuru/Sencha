package com.sencha.sencha.core.domain.sync

import kotlinx.serialization.Serializable

@Serializable
enum class BlobStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED,
}

@Serializable
data class BlobRecord(
    val blobId: String,
    val chatId: String,
    val sha256: String,
    val size: Long,
    val mime: String,
    val localPath: String?,
    val remoteKey: String?,
    val status: BlobStatus,
    val createdAtEpochMillis: Long,
)

object BlobKeyBuilder {
    fun build(
        userId: String,
        chatId: String,
        blobId: String,
        sha256: String,
        extension: String,
    ): String {
        val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
        return "u/$userId/c/$chatId/b/$blobId-$sha256.$safeExt"
    }
}
