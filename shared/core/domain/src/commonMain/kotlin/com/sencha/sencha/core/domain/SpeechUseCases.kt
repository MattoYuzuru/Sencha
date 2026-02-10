package com.sencha.sencha.core.domain

import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.jobs.JobEngine
import com.sencha.sencha.core.jobs.JobError
import com.sencha.sencha.core.jobs.JobErrorCode
import com.sencha.sencha.core.jobs.JobExecutionContext
import com.sencha.sencha.core.jobs.JobFailureException
import com.sencha.sencha.core.jobs.JobHandle
import com.sencha.sencha.core.jobs.JobId
import com.sencha.sencha.core.jobs.JobState
import com.sencha.sencha.core.model.AudioInputInfo
import com.sencha.sencha.core.model.SttParams
import com.sencha.sencha.core.model.SttValidationError
import com.sencha.sencha.core.model.SttValidationErrorCode
import com.sencha.sencha.core.model.TtsParams
import com.sencha.sencha.core.model.TtsValidationError
import com.sencha.sencha.core.model.TtsValidationErrorCode
import com.sencha.sencha.core.security.Hashing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock

class SttUseCase(
    private val artifacts: ArtifactRepository,
    private val jobs: JobRepository,
    private val providers: ModelProviderRegistry,
    private val jobEngine: JobEngine,
    private val mediaStore: MediaStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { explicitNulls = false }

    fun transcribe(entry: ModelEntry, input: AudioInput, params: SttParams): JobHandle<SttResult> {
        val jobId = JobId("job-stt-${clock.now().toEpochMilliseconds()}-${Random.nextInt()}")
        val jobPayload = SttJobPayload(
            inputPath = input.localPath,
            inputMime = input.mimeType,
            inputSizeBytes = input.sizeBytes,
            inputDurationMillis = input.durationMillis,
            fileName = input.fileName,
            params = params,
        )
        val jobRecord = JobRecord(
            id = jobId.value,
            type = JobType.STT_TRANSCRIBE,
            state = JobState.QUEUED,
            modelKey = entry.key,
            payloadJson = json.encodeToString(SttJobPayload.serializer(), jobPayload),
            createdAtEpochMillis = clock.now().toEpochMilliseconds(),
            updatedAtEpochMillis = clock.now().toEpochMilliseconds(),
        )
        jobs.upsert(jobRecord)

        val capabilities = entry.descriptor.sttCapabilities
        if (capabilities == null) {
            val handle = submitFailureJob<SttResult>(
                jobEngine = jobEngine,
                jobId = jobId,
                error = JobError(JobErrorCode.VALIDATION, "STT is not supported by this model"),
            )
            trackJob(handle, jobRecord)
            return handle
        }
        val inputInfo = AudioInputInfo(
            mimeType = input.mimeType,
            sizeBytes = input.sizeBytes,
            durationMillis = input.durationMillis,
        )
        val validation = capabilities.validate(inputInfo, params)
        if (validation.isNotEmpty()) {
            val error = validation.first().toJobError("Invalid STT parameters")
            val handle = submitFailureJob<SttResult>(jobEngine, jobId, error)
            trackJob(handle, jobRecord)
            return handle
        }

        val bytes = try {
            input.data ?: mediaStore.read(input.localPath)
        } catch (throwable: Throwable) {
            val handle = submitFailureJob<SttResult>(
                jobEngine = jobEngine,
                jobId = jobId,
                error = JobError(
                    code = JobErrorCode.UNKNOWN,
                    message = throwable.message ?: "Failed to read input audio",
                    cause = throwable::class.simpleName,
                ),
            )
            trackJob(handle, jobRecord)
            return handle
        }
        val inputWithBytes = if (input.data == null) input.copy(data = bytes) else input
        val sha256 = Hashing.sha256Hex(bytes)
        val sourceBlob = ArtifactBlobInfo(
            blobId = "blob-${clock.now().toEpochMilliseconds()}-${Random.nextInt()}",
            sha256 = sha256,
            sizeBytes = bytes.size.toLong(),
            mimeType = input.mimeType,
            localPath = input.localPath,
        )

        val provider = providers.providerFor(entry.key)
        if (provider == null) {
            val handle = submitFailureJob<SttResult>(
                jobEngine = jobEngine,
                jobId = jobId,
                error = JobError(JobErrorCode.VALIDATION, "Provider not available for STT"),
            )
            trackJob(handle, jobRecord)
            return handle
        }

        val handle = try {
            jobEngine.submit(
                provider.createSttJob(
                    SttRequest(
                        jobId = jobId,
                        modelKey = entry.key,
                        input = inputWithBytes,
                        params = params,
                    )
                )
            )
        } catch (throwable: Throwable) {
            val failure = submitFailureJob<SttResult>(
                jobEngine = jobEngine,
                jobId = jobId,
                error = JobError(
                    code = JobErrorCode.UNKNOWN,
                    message = throwable.message ?: "Failed to start STT job",
                    cause = throwable::class.simpleName,
                ),
            )
            trackJob(failure, jobRecord)
            return failure
        }

        trackJob(handle, jobRecord)

        scope.launch {
            val final = handle.snapshot.first { it.state in setOf(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELED) }
            if (final.state == JobState.SUCCEEDED) {
                val result = handle.output.first()
                val meta = ArtifactMeta(
                    durationMillis = input.durationMillis,
                    language = result.language,
                    format = "text/plain",
                    sizeBytes = result.text.length.toLong(),
                    sha256 = null,
                    createdAtEpochMillis = clock.now().toEpochMilliseconds(),
                )
                val origin = ArtifactOrigin(
                    jobId = jobId.value,
                    sourceMessageId = null,
                    sourceFileName = input.fileName,
                    modelId = entry.key.modelId.value,
                )
                artifacts.createTextArtifact(
                    CreateTextArtifactRequest(
                        origin = origin,
                        text = result.text,
                        meta = meta,
                        sourceAudio = sourceBlob,
                    )
                )
            }
        }

        return handle
    }

    private fun <T> trackJob(handle: JobHandle<T>, jobRecord: JobRecord) {
        scope.launch {
            handle.snapshot.collect { snapshot ->
                jobs.upsert(
                    jobRecord.copy(
                        state = snapshot.state,
                        errorCode = snapshot.error?.code,
                        errorMessage = snapshot.error?.message,
                        updatedAtEpochMillis = clock.now().toEpochMilliseconds(),
                    )
                )
            }
        }
    }
}

class TtsUseCase(
    private val artifacts: ArtifactRepository,
    private val jobs: JobRepository,
    private val providers: ModelProviderRegistry,
    private val jobEngine: JobEngine,
    private val mediaStore: MediaStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { explicitNulls = false }

    fun synthesize(entry: ModelEntry, input: TextInput, params: TtsParams): JobHandle<TtsResult> {
        val jobId = JobId("job-tts-${clock.now().toEpochMilliseconds()}-${Random.nextInt()}")
        val jobPayload = TtsJobPayload(
            text = input.text,
            params = params,
        )
        val jobRecord = JobRecord(
            id = jobId.value,
            type = JobType.TTS_SYNTHESIZE,
            state = JobState.QUEUED,
            modelKey = entry.key,
            payloadJson = json.encodeToString(TtsJobPayload.serializer(), jobPayload),
            createdAtEpochMillis = clock.now().toEpochMilliseconds(),
            updatedAtEpochMillis = clock.now().toEpochMilliseconds(),
        )
        jobs.upsert(jobRecord)

        val capabilities = entry.descriptor.ttsCapabilities
        if (capabilities == null) {
            val handle = submitFailureJob<TtsResult>(
                jobEngine = jobEngine,
                jobId = jobId,
                error = JobError(JobErrorCode.VALIDATION, "TTS is not supported by this model"),
            )
            trackJob(handle, jobRecord)
            return handle
        }
        val validation = capabilities.validate(input.text, params)
        if (validation.isNotEmpty()) {
            val error = validation.first().toJobError("Invalid TTS parameters")
            val handle = submitFailureJob<TtsResult>(jobEngine, jobId, error)
            trackJob(handle, jobRecord)
            return handle
        }

        val provider = providers.providerFor(entry.key)
        if (provider == null) {
            val handle = submitFailureJob<TtsResult>(
                jobEngine = jobEngine,
                jobId = jobId,
                error = JobError(JobErrorCode.VALIDATION, "Provider not available for TTS"),
            )
            trackJob(handle, jobRecord)
            return handle
        }

        val handle = try {
            jobEngine.submit(
                provider.createTtsJob(
                    TtsRequest(
                        jobId = jobId,
                        modelKey = entry.key,
                        input = input,
                        params = params,
                    )
                )
            )
        } catch (throwable: Throwable) {
            val failure = submitFailureJob<TtsResult>(
                jobEngine = jobEngine,
                jobId = jobId,
                error = JobError(
                    code = JobErrorCode.UNKNOWN,
                    message = throwable.message ?: "Failed to start TTS job",
                    cause = throwable::class.simpleName,
                ),
            )
            trackJob(failure, jobRecord)
            return failure
        }

        trackJob(handle, jobRecord)

        scope.launch {
            val final = handle.snapshot.first { it.state in setOf(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELED) }
            if (final.state == JobState.SUCCEEDED) {
                val result = handle.output.first()
                val extension = mimeToExtension(result.mimeType)
                val blobId = "blob-${clock.now().toEpochMilliseconds()}-${Random.nextInt()}"
                val path = mediaStore.createBlobPath(blobId, extension)
                mediaStore.write(path, result.audioBytes)
                val sha256 = Hashing.sha256Hex(result.audioBytes)
                val meta = ArtifactMeta(
                    durationMillis = result.durationMillis,
                    language = null,
                    format = result.mimeType,
                    sizeBytes = result.audioBytes.size.toLong(),
                    sha256 = sha256,
                    createdAtEpochMillis = clock.now().toEpochMilliseconds(),
                )
                val origin = ArtifactOrigin(
                    jobId = jobId.value,
                    sourceMessageId = null,
                    sourceFileName = null,
                    modelId = entry.key.modelId.value,
                )
                artifacts.createAudioArtifact(
                    CreateAudioArtifactRequest(
                        origin = origin,
                        meta = meta,
                        blob = ArtifactBlobInfo(
                            blobId = blobId,
                            sha256 = sha256,
                            sizeBytes = result.audioBytes.size.toLong(),
                            mimeType = result.mimeType,
                            localPath = path,
                        ),
                    )
                )
            }
        }

        return handle
    }

    private fun <T> trackJob(handle: JobHandle<T>, jobRecord: JobRecord) {
        scope.launch {
            handle.snapshot.collect { snapshot ->
                jobs.upsert(
                    jobRecord.copy(
                        state = snapshot.state,
                        errorCode = snapshot.error?.code,
                        errorMessage = snapshot.error?.message,
                        updatedAtEpochMillis = clock.now().toEpochMilliseconds(),
                    )
                )
            }
        }
    }
}

@Serializable
private data class SttJobPayload(
    val inputPath: String,
    val inputMime: String,
    val inputSizeBytes: Long,
    val inputDurationMillis: Long? = null,
    val fileName: String? = null,
    val params: SttParams,
)

@Serializable
private data class TtsJobPayload(
    val text: String,
    val params: TtsParams,
)

private fun <T> submitFailureJob(
    jobEngine: JobEngine,
    jobId: JobId,
    error: JobError,
): JobHandle<T> {
    return jobEngine.submit(
        object : JobDefinition<T> {
            override val id: JobId = jobId
            override val description: String? = "Validation failed"

            override suspend fun run(context: JobExecutionContext<T>) {
                throw JobFailureException(error)
            }
        }
    )
}

private fun SttValidationError.toJobError(prefix: String): JobError {
    val code = when (this.code) {
        SttValidationErrorCode.UNSUPPORTED_FORMAT -> JobErrorCode.UNSUPPORTED_FORMAT
        SttValidationErrorCode.TOO_LARGE -> JobErrorCode.TOO_LARGE
        else -> JobErrorCode.VALIDATION
    }
    val detail = detail?.let { ": $it" }.orEmpty()
    return JobError(code = code, message = "$prefix: ${code.name}$detail")
}

private fun TtsValidationError.toJobError(prefix: String): JobError {
    val code = when (this.code) {
        TtsValidationErrorCode.UNSUPPORTED_FORMAT -> JobErrorCode.UNSUPPORTED_FORMAT
        else -> JobErrorCode.VALIDATION
    }
    val detail = detail?.let { ": $it" }.orEmpty()
    return JobError(code = code, message = "$prefix: ${code.name}$detail")
}

private fun mimeToExtension(mime: String): String {
    return when (mime.lowercase()) {
        "audio/m4a", "audio/mp4" -> "m4a"
        "audio/mpeg" -> "mp3"
        "audio/wav", "audio/wave", "audio/x-wav" -> "wav"
        else -> "bin"
    }
}
