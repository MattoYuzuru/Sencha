package com.sencha.sencha.core.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ArtifactId(val value: String)

@Serializable
enum class ArtifactType {
    TEXT,
    AUDIO,
}

@Serializable
data class ArtifactOrigin(
    val jobId: String,
    val sourceMessageId: String? = null,
    val sourceFileName: String? = null,
    val modelId: String,
)

@Serializable
data class ArtifactMeta(
    val durationMillis: Long? = null,
    val language: String? = null,
    val format: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val createdAtEpochMillis: Long,
)

@Serializable
data class ArtifactRef(
    val localPath: String? = null,
    val remoteKey: String? = null,
)

@Serializable
data class ArtifactBlobInfo(
    val blobId: String,
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
    val localPath: String? = null,
    val remoteKey: String? = null,
)

@Serializable
data class Artifact(
    val id: ArtifactId,
    val type: ArtifactType,
    val origin: ArtifactOrigin,
    val meta: ArtifactMeta,
    val text: String? = null,
    val blobId: String? = null,
    val sourceBlobId: String? = null,
    val ref: ArtifactRef? = null,
)

data class CreateTextArtifactRequest(
    val origin: ArtifactOrigin,
    val text: String,
    val meta: ArtifactMeta,
    val sourceAudio: ArtifactBlobInfo? = null,
)

data class CreateAudioArtifactRequest(
    val origin: ArtifactOrigin,
    val meta: ArtifactMeta,
    val blob: ArtifactBlobInfo,
)
