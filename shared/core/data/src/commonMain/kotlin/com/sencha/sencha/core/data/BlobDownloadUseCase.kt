package com.sencha.sencha.core.data

import com.sencha.sencha.core.data.sync.BlobStore
import com.sencha.sencha.core.data.sync.BlobTransfer
import com.sencha.sencha.core.data.sync.PresignOperation
import com.sencha.sencha.core.data.sync.PresignRequest
import com.sencha.sencha.core.data.sync.SyncApi
import com.sencha.sencha.core.domain.MediaStore
import com.sencha.sencha.core.domain.sync.BlobStatus
import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.jobs.JobEngine
import com.sencha.sencha.core.jobs.JobError
import com.sencha.sencha.core.jobs.JobErrorCode
import com.sencha.sencha.core.jobs.JobExecutionContext
import com.sencha.sencha.core.jobs.JobFailureException
import com.sencha.sencha.core.jobs.JobHandle
import com.sencha.sencha.core.jobs.JobId
import com.sencha.sencha.core.jobs.JobProgress
import kotlin.random.Random
import kotlin.time.Clock

class BlobDownloadUseCase(
    private val blobStore: BlobStore,
    private val api: SyncApi,
    private val blobTransfer: BlobTransfer,
    private val mediaStore: MediaStore,
    private val jobEngine: JobEngine,
    private val clock: Clock = Clock.System,
) {
    fun download(blobId: String): JobHandle<String> {
        val job = object : JobDefinition<String> {
            override val id = JobId("job-blob-download-${clock.now().toEpochMilliseconds()}-${Random.nextInt()}")
            override val description = "Download blob"

            override suspend fun run(context: JobExecutionContext<String>) {
                val blob = blobStore.find(blobId) ?: throw JobFailureException(
                    JobError(JobErrorCode.VALIDATION, "Blob not found")
                )
                val remoteKey = blob.remoteKey ?: throw JobFailureException(
                    JobError(JobErrorCode.VALIDATION, "Remote blob not available")
                )
                context.updateProgress(JobProgress(message = "Requesting download"))
                val presign = api.presign(
                    PresignRequest(
                        operation = PresignOperation.DOWNLOAD,
                        chatId = blob.chatId,
                        blobId = blob.blobId,
                        sha256 = blob.sha256,
                        mime = blob.mime,
                        size = blob.size,
                        extension = blob.mime.substringAfter('/', "bin"),
                    )
                )
                context.updateProgress(JobProgress(message = "Downloading"))
                val bytes = blobTransfer.download(presign)
                val extension = blob.mime.substringAfter('/', "bin")
                val path = mediaStore.createBlobPath(blob.blobId, extension)
                mediaStore.write(path, bytes)
                blobStore.updateStatus(blob.blobId, BlobStatus.UPLOADED, remoteKey, path)
                context.emitOutput(path)
            }
        }
        return jobEngine.submit(job)
    }
}
