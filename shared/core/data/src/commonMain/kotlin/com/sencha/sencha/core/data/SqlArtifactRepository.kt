package com.sencha.sencha.core.data

import com.sencha.sencha.core.data.sync.BlobStore
import com.sencha.sencha.core.data.sync.EventStore
import com.sencha.sencha.core.data.sync.db.SyncDatabase
import com.sencha.sencha.core.data.sync.SyncJson
import com.sencha.sencha.core.domain.Artifact
import com.sencha.sencha.core.domain.ArtifactBlobInfo
import com.sencha.sencha.core.domain.ArtifactId
import com.sencha.sencha.core.domain.ArtifactMeta
import com.sencha.sencha.core.domain.ArtifactOrigin
import com.sencha.sencha.core.domain.ArtifactRef
import com.sencha.sencha.core.domain.ArtifactRepository
import com.sencha.sencha.core.domain.ArtifactType
import com.sencha.sencha.core.domain.CreateAudioArtifactRequest
import com.sencha.sencha.core.domain.CreateTextArtifactRequest
import com.sencha.sencha.core.domain.sync.ArtifactBlobPayload
import com.sencha.sencha.core.domain.sync.ArtifactCreatedPayload
import com.sencha.sencha.core.domain.sync.BlobUploadedPayload
import com.sencha.sencha.core.domain.sync.SyncEvent
import com.sencha.sencha.core.domain.sync.SyncEventTypes
import com.sencha.sencha.core.domain.sync.UlidGenerator
import com.sencha.sencha.core.domain.sync.BlobRecord
import com.sencha.sencha.core.domain.sync.BlobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Clock

class SqlArtifactRepository(
    private val database: SyncDatabase,
    private val eventStore: EventStore,
    private val blobStore: BlobStore,
    private val deviceId: String,
    private val clock: Clock = Clock.System,
    private val onLocalEventAppended: (() -> Unit)? = null,
) : ArtifactRepository {
    private val queries = database.syncDatabaseQueries
    private val artifactsFlow = MutableStateFlow<List<Artifact>>(emptyList())

    init {
        refreshFromStore()
    }

    override fun artifacts(): StateFlow<List<Artifact>> = artifactsFlow.asStateFlow()

    override fun find(id: ArtifactId): Artifact? {
        val record = queries.selectArtifactById(id.value).executeAsOneOrNull() ?: return null
        return record.toArtifact()
    }

    override fun snapshot(): List<Artifact> = loadArtifacts()

    override fun createTextArtifact(request: CreateTextArtifactRequest): Artifact {
        val artifactId = ArtifactId(UlidGenerator.newUlid())
        val createdAt = request.meta.createdAtEpochMillis
        val scopeId = artifactScopeId(artifactId)

        queries.insertArtifact(
            artifact_id = artifactId.value,
            type = ArtifactType.TEXT.name,
            origin_json = SyncJson.instance.encodeToString(ArtifactOrigin.serializer(), request.origin),
            meta_json = SyncJson.instance.encodeToString(ArtifactMeta.serializer(), request.meta),
            text_content = request.text,
            blob_id = null,
            source_blob_id = request.sourceAudio?.blobId,
            created_at = createdAt,
        )

        val sourcePayload = request.sourceAudio?.let { info ->
            upsertBlob(scopeId, info, createdAt)
            info.toPayload()
        }

        val event = SyncEvent(
            eventId = UlidGenerator.newUlid(),
            deviceId = deviceId,
            chatId = scopeId,
            type = SyncEventTypes.ARTIFACT_CREATED,
            payload = com.sencha.sencha.core.domain.sync.EventPayloadEnvelope(
                schemaVersion = 1,
                data = SyncJson.instance.encodeToJsonElement(
                    ArtifactCreatedPayload.serializer(),
                    ArtifactCreatedPayload(
                        artifactId = artifactId.value,
                        type = ArtifactType.TEXT,
                        origin = request.origin,
                        meta = request.meta,
                        text = request.text,
                        blob = null,
                        sourceBlob = sourcePayload,
                    )
                ),
            ),
            createdAtEpochMillis = createdAt,
        )
        eventStore.appendLocal(event)
        onLocalEventAppended?.invoke()
        val artifact = find(artifactId) ?: error("Artifact not found after insert")
        artifactsFlow.value = loadArtifacts()
        return artifact
    }

    override fun createAudioArtifact(request: CreateAudioArtifactRequest): Artifact {
        val artifactId = ArtifactId(UlidGenerator.newUlid())
        val createdAt = request.meta.createdAtEpochMillis
        val scopeId = artifactScopeId(artifactId)

        queries.insertArtifact(
            artifact_id = artifactId.value,
            type = ArtifactType.AUDIO.name,
            origin_json = SyncJson.instance.encodeToString(ArtifactOrigin.serializer(), request.origin),
            meta_json = SyncJson.instance.encodeToString(ArtifactMeta.serializer(), request.meta),
            text_content = null,
            blob_id = request.blob.blobId,
            source_blob_id = null,
            created_at = createdAt,
        )

        val blobPayload = run {
            upsertBlob(scopeId, request.blob, createdAt)
            request.blob.toPayload()
        }

        val event = SyncEvent(
            eventId = UlidGenerator.newUlid(),
            deviceId = deviceId,
            chatId = scopeId,
            type = SyncEventTypes.ARTIFACT_CREATED,
            payload = com.sencha.sencha.core.domain.sync.EventPayloadEnvelope(
                schemaVersion = 1,
                data = SyncJson.instance.encodeToJsonElement(
                    ArtifactCreatedPayload.serializer(),
                    ArtifactCreatedPayload(
                        artifactId = artifactId.value,
                        type = ArtifactType.AUDIO,
                        origin = request.origin,
                        meta = request.meta,
                        text = null,
                        blob = blobPayload,
                        sourceBlob = null,
                    )
                ),
            ),
            createdAtEpochMillis = createdAt,
        )
        eventStore.appendLocal(event)
        onLocalEventAppended?.invoke()
        val artifact = find(artifactId) ?: error("Artifact not found after insert")
        artifactsFlow.value = loadArtifacts()
        return artifact
    }

    override fun refreshFromStore() {
        val events = eventStore.allEvents()
        events.forEach { event ->
            when (event.type) {
                SyncEventTypes.ARTIFACT_CREATED -> applyArtifactCreated(event)
                SyncEventTypes.BLOB_UPLOADED -> applyBlobUploaded(event)
            }
        }
        artifactsFlow.value = loadArtifacts()
    }

    private fun applyArtifactCreated(event: SyncEvent) {
        val payload = SyncJson.instance.decodeFromJsonElement(
            ArtifactCreatedPayload.serializer(),
            event.payload.data,
        )
        val existing = queries.selectArtifactById(payload.artifactId).executeAsOneOrNull()
        if (existing == null) {
            queries.insertArtifact(
                artifact_id = payload.artifactId,
                type = payload.type.name,
                origin_json = SyncJson.instance.encodeToString(ArtifactOrigin.serializer(), payload.origin),
                meta_json = SyncJson.instance.encodeToString(ArtifactMeta.serializer(), payload.meta),
                text_content = payload.text,
                blob_id = payload.blob?.blobId,
                source_blob_id = payload.sourceBlob?.blobId,
                created_at = payload.meta.createdAtEpochMillis,
            )
        }
        payload.blob?.let { upsertBlob(event.chatId, it.toInfo(), payload.meta.createdAtEpochMillis) }
        payload.sourceBlob?.let { upsertBlob(event.chatId, it.toInfo(), payload.meta.createdAtEpochMillis) }
    }

    private fun applyBlobUploaded(event: SyncEvent) {
        val payload = SyncJson.instance.decodeFromJsonElement(
            BlobUploadedPayload.serializer(),
            event.payload.data,
        )
        val current = blobStore.find(payload.blobId) ?: return
        blobStore.updateStatus(
            blobId = payload.blobId,
            status = BlobStatus.UPLOADED,
            remoteKey = payload.remoteKey,
            localPath = current.localPath,
        )
    }

    private fun upsertBlob(scopeId: String, info: ArtifactBlobInfo, createdAt: Long) {
        val existing = blobStore.find(info.blobId)
        val remoteKey = info.remoteKey ?: existing?.remoteKey
        val localPath = existing?.localPath ?: info.localPath
        val status = when {
            remoteKey != null -> BlobStatus.UPLOADED
            localPath != null -> BlobStatus.PENDING
            else -> BlobStatus.PENDING
        }
        blobStore.upsert(
            BlobRecord(
                blobId = info.blobId,
                chatId = scopeId,
                sha256 = info.sha256,
                size = info.sizeBytes,
                mime = info.mimeType,
                localPath = localPath,
                remoteKey = remoteKey,
                status = status,
                createdAtEpochMillis = createdAt,
            )
        )
    }

    private fun loadArtifacts(): List<Artifact> {
        return queries.selectAllArtifacts().executeAsList().map { it.toArtifact() }
    }

    private fun com.sencha.sencha.core.data.sync.db.Artifacts.toArtifact(): Artifact {
        val origin = SyncJson.instance.decodeFromString(ArtifactOrigin.serializer(), origin_json)
        val meta = SyncJson.instance.decodeFromString(ArtifactMeta.serializer(), meta_json)
        val blob = blob_id?.let { blobStore.find(it) }
        val ref = blob?.let { ArtifactRef(localPath = it.localPath, remoteKey = it.remoteKey) }
        return Artifact(
            id = ArtifactId(artifact_id),
            type = ArtifactType.valueOf(type),
            origin = origin,
            meta = meta,
            text = text_content,
            blobId = blob_id,
            sourceBlobId = source_blob_id,
            ref = ref,
        )
    }

    private fun artifactScopeId(artifactId: ArtifactId): String = "artifact-${artifactId.value}"

    private fun ArtifactBlobInfo.toPayload(): ArtifactBlobPayload {
        return ArtifactBlobPayload(
            blobId = blobId,
            sha256 = sha256,
            size = sizeBytes,
            mime = mimeType,
            remoteKey = remoteKey,
        )
    }

    private fun ArtifactBlobPayload.toInfo(): ArtifactBlobInfo {
        return ArtifactBlobInfo(
            blobId = blobId,
            sha256 = sha256,
            sizeBytes = size,
            mimeType = mime,
            remoteKey = remoteKey,
        )
    }
}
