package com.sencha.sencha.core.domain.sync

import com.sencha.sencha.core.domain.ChatRole
import com.sencha.sencha.core.domain.ArtifactMeta
import com.sencha.sencha.core.domain.ArtifactOrigin
import com.sencha.sencha.core.domain.ArtifactType
import kotlinx.serialization.Serializable

@Serializable
data class ChatCreatedPayload(
    val title: String,
    val modelProviderId: String,
    val modelId: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class MessageCreatedPayload(
    val messageId: String,
    val role: ChatRole,
    val content: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class ArtifactBlobPayload(
    val blobId: String,
    val sha256: String,
    val size: Long,
    val mime: String,
    val remoteKey: String? = null,
)

@Serializable
data class ArtifactCreatedPayload(
    val artifactId: String,
    val type: ArtifactType,
    val origin: ArtifactOrigin,
    val meta: ArtifactMeta,
    val text: String? = null,
    val blob: ArtifactBlobPayload? = null,
    val sourceBlob: ArtifactBlobPayload? = null,
)

@Serializable
data class ArtifactUpdatedPayload(
    val artifactId: String,
    val meta: ArtifactMeta? = null,
)

@Serializable
data class BlobUploadedPayload(
    val blobId: String,
    val remoteKey: String,
)
