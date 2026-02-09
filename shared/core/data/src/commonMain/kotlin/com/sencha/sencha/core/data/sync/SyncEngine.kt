package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.BlobStatus
import com.sencha.sencha.core.domain.sync.SyncEvent
import com.sencha.sencha.core.domain.sync.UlidGenerator
import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.jobs.JobError
import com.sencha.sencha.core.jobs.JobErrorCode
import com.sencha.sencha.core.jobs.JobFailureException
import com.sencha.sencha.core.jobs.JobHandle
import com.sencha.sencha.core.jobs.JobId
import com.sencha.sencha.core.jobs.JobProgress
import com.sencha.sencha.core.jobs.JobEngine
import com.sencha.sencha.core.jobs.JobExecutionContext
import kotlin.time.Clock


data class SyncConfig(
    val batchSize: Int = 50,
)

data class SyncProgress(
    val processed: Int,
    val total: Int?,
    val phase: String,
)

class SyncEngine(
    private val eventStore: EventStore,
    private val blobStore: BlobStore,
    private val api: SyncApi,
    private val blobTransfer: BlobTransfer,
    private val jobEngine: JobEngine,
    private val clock: Clock = Clock.System,
    private val config: SyncConfig = SyncConfig(),
) {
    fun enqueueUploadEvents(): JobHandle<SyncProgress> {
        return jobEngine.submit(uploadEventsJob())
    }

    fun enqueueDownloadEvents(): JobHandle<SyncProgress> {
        return jobEngine.submit(downloadEventsJob())
    }

    fun enqueueBlobUploads(): JobHandle<SyncProgress> {
        return jobEngine.submit(uploadBlobsJob())
    }

    private fun uploadEventsJob(): JobDefinition<SyncProgress> = object : JobDefinition<SyncProgress> {
        override val id = JobId("sync-upload-${UlidGenerator.newUlid()}")
        override val description = "Upload pending events"

        override suspend fun run(context: JobExecutionContext<SyncProgress>) {
            try {
                var processed = 0
                while (context.isActive) {
                    val batch = eventStore.pendingEvents(config.batchSize)
                    if (batch.isEmpty()) break
                    context.updateProgress(
                        JobProgress(
                            current = processed.toLong(),
                            total = null,
                            message = "Uploading events",
                        )
                    )
                    api.uploadEvents(batch)
                    eventStore.markUploaded(batch.map(SyncEvent::eventId))
                    processed += batch.size
                    context.emitOutput(SyncProgress(processed, null, "upload"))
                }
                eventStore.updateLastSyncAt(clock.now().toEpochMilliseconds())
                eventStore.updateLastErrorMessage(null)
            } catch (throwable: Throwable) {
                eventStore.updateLastErrorMessage(throwable.message ?: "Unknown error")
                throw JobFailureException(
                    JobError(
                        code = JobErrorCode.UNKNOWN,
                        message = throwable.message ?: "Upload failed",
                        cause = throwable::class.simpleName,
                    )
                )
            }
        }
    }

    private fun downloadEventsJob(): JobDefinition<SyncProgress> = object : JobDefinition<SyncProgress> {
        override val id = JobId("sync-download-${UlidGenerator.newUlid()}")
        override val description = "Download remote events"

        override suspend fun run(context: JobExecutionContext<SyncProgress>) {
            try {
                var cursor = eventStore.lastAckCursor()
                var processed = 0
                while (context.isActive) {
                    val response = api.fetchEventsSince(cursor, config.batchSize)
                    if (response.events.isEmpty()) break
                    eventStore.mergeRemote(response.events)
                    cursor = response.nextCursor
                    eventStore.updateLastAckCursor(cursor)
                    processed += response.events.size
                    context.emitOutput(SyncProgress(processed, null, "download"))
                }
                eventStore.updateLastSyncAt(clock.now().toEpochMilliseconds())
                eventStore.updateLastErrorMessage(null)
            } catch (throwable: Throwable) {
                eventStore.updateLastErrorMessage(throwable.message ?: "Unknown error")
                throw JobFailureException(
                    JobError(
                        code = JobErrorCode.UNKNOWN,
                        message = throwable.message ?: "Download failed",
                        cause = throwable::class.simpleName,
                    )
                )
            }
        }
    }

    private fun uploadBlobsJob(): JobDefinition<SyncProgress> = object : JobDefinition<SyncProgress> {
        override val id = JobId("sync-blobs-${UlidGenerator.newUlid()}")
        override val description = "Upload pending blobs"

        override suspend fun run(context: JobExecutionContext<SyncProgress>) {
            try {
                var processed = 0
                while (context.isActive) {
                    val pending = blobStore.pendingBlobs(config.batchSize)
                    if (pending.isEmpty()) break
                    for (blob in pending) {
                        val presign = api.presign(
                            PresignRequest(
                                operation = PresignOperation.UPLOAD,
                                chatId = blob.chatId,
                                blobId = blob.blobId,
                                sha256 = blob.sha256,
                                mime = blob.mime,
                                size = blob.size,
                                extension = blob.mime.substringAfter('/', "bin"),
                            )
                        )
                        blobStore.updateStatus(blob.blobId, BlobStatus.UPLOADING, presign.key, blob.localPath)
                        try {
                            blobTransfer.upload(presign, blob)
                            blobStore.updateStatus(blob.blobId, BlobStatus.UPLOADED, presign.key, blob.localPath)
                        } catch (throwable: Throwable) {
                            blobStore.updateStatus(blob.blobId, BlobStatus.FAILED, presign.key, blob.localPath)
                            throw throwable
                        }
                        context.emitOutput(SyncProgress(processed, null, "blob-upload"))
                        processed += 1
                    }
                    context.updateProgress(JobProgress(current = processed.toLong(), total = null, message = "Preparing blobs"))
                    break
                }
                eventStore.updateLastSyncAt(clock.now().toEpochMilliseconds())
                eventStore.updateLastErrorMessage(null)
            } catch (throwable: Throwable) {
                eventStore.updateLastErrorMessage(throwable.message ?: "Unknown error")
                throw JobFailureException(
                    JobError(
                        code = JobErrorCode.UNKNOWN,
                        message = throwable.message ?: "Blob upload failed",
                        cause = throwable::class.simpleName,
                    )
                )
            }
        }
    }
}
