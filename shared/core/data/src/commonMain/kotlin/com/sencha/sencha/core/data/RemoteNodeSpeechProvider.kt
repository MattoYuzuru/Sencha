package com.sencha.sencha.core.data

import com.sencha.sencha.core.data.sync.SyncEndpointPolicy
import com.sencha.sencha.core.domain.ChatDelta
import com.sencha.sencha.core.domain.ChatRequest
import com.sencha.sencha.core.domain.MediaStore
import com.sencha.sencha.core.domain.ModelProvider
import com.sencha.sencha.core.domain.ModelProviderId
import com.sencha.sencha.core.domain.ModelProviderInfo
import com.sencha.sencha.core.domain.ModelProviderKind
import com.sencha.sencha.core.domain.SttRequest
import com.sencha.sencha.core.domain.SttResult
import com.sencha.sencha.core.domain.TtsRequest
import com.sencha.sencha.core.domain.TtsResult
import com.sencha.sencha.core.domain.SttSegment
import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.jobs.JobError
import com.sencha.sencha.core.jobs.JobErrorCode
import com.sencha.sencha.core.jobs.JobExecutionContext
import com.sencha.sencha.core.jobs.JobFailureException
import com.sencha.sencha.core.jobs.JobProgress
import com.sencha.sencha.core.model.AudioInputInfo
import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import com.sencha.sencha.core.model.ModelResourceProfile
import com.sencha.sencha.core.model.ModelRuntime
import com.sencha.sencha.core.model.ModelSource
import com.sencha.sencha.core.model.ModelType
import com.sencha.sencha.core.model.SttCapabilities
import com.sencha.sencha.core.model.SttValidationErrorCode
import com.sencha.sencha.core.model.TtsCapabilities
import com.sencha.sencha.core.model.TtsVoice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class RemoteNodeSpeechProvider(
    private val baseUrlProvider: () -> String,
    private val mediaStore: MediaStore,
    private val isDebug: Boolean,
    private val allowlistedHosts: Set<String>,
    private val client: HttpClient = HttpClientFactory.create(),
) : ModelProvider {
    override val info = ModelProviderInfo(
        id = ModelProviderId("node-speech"),
        displayName = "Remote node",
        kind = ModelProviderKind.REMOTE,
    )

    private val sttCapabilities = SttCapabilities(
        inputFormats = setOf("audio/mpeg", "audio/wav", "audio/m4a", "audio/mp4"),
        maxDurationMillis = 10 * 60 * 1000L,
        maxSizeBytes = 25L * 1024L * 1024L,
        languages = null,
        supportsDiarization = false,
        supportsTimestamps = false,
    )

    private val ttsCapabilities = TtsCapabilities(
        voices = listOf(TtsVoice(id = "default", displayName = "Default")),
        outputFormats = setOf("audio/m4a", "audio/wav"),
        sampleRatesHz = null,
        maxChars = 2000,
    )

    private val sttDescriptor = ModelDescriptor(
        id = ModelId("remote-stt"),
        displayName = "Remote STT",
        capabilities = setOf(ModelCapability.STT),
        types = setOf(ModelType.STT),
        resources = ModelResourceProfile(requiresNetwork = true),
        runtime = ModelRuntime.REMOTE_NODE,
        source = ModelSource.remote(),
        artifact = null,
        sttCapabilities = sttCapabilities,
        ttsCapabilities = null,
    )

    private val ttsDescriptor = ModelDescriptor(
        id = ModelId("remote-tts"),
        displayName = "Remote TTS",
        capabilities = setOf(ModelCapability.TTS),
        types = setOf(ModelType.TTS),
        resources = ModelResourceProfile(requiresNetwork = true),
        runtime = ModelRuntime.REMOTE_NODE,
        source = ModelSource.remote(),
        artifact = null,
        sttCapabilities = null,
        ttsCapabilities = ttsCapabilities,
    )

    fun sttModelDescriptor(): ModelDescriptor = sttDescriptor

    fun ttsModelDescriptor(): ModelDescriptor = ttsDescriptor

    override suspend fun listModels(): Result<List<ModelDescriptor>> {
        return Result.success(listOf(sttDescriptor, ttsDescriptor))
    }

    override fun createChatJob(request: ChatRequest): JobDefinition<ChatDelta> {
        return object : JobDefinition<ChatDelta> {
            override val id = request.modelKey.modelId.let { com.sencha.sencha.core.jobs.JobId("unsupported-chat-${it.value}") }
            override val description = "Chat not supported"

            override suspend fun run(context: JobExecutionContext<ChatDelta>) {
                throw JobFailureException(
                    JobError(
                        code = JobErrorCode.VALIDATION,
                        message = "Chat is not supported by remote speech provider",
                    )
                )
            }
        }
    }

    override fun createSttJob(request: SttRequest): JobDefinition<SttResult> {
        return object : JobDefinition<SttResult> {
            override val id = request.jobId
            override val description = "Remote STT transcription"

            override suspend fun run(context: JobExecutionContext<SttResult>) {
                val endpoint = validateEndpoint()
                val inputInfo = AudioInputInfo(
                    mimeType = request.input.mimeType,
                    sizeBytes = request.input.sizeBytes,
                    durationMillis = request.input.durationMillis,
                )
                val validation = sttCapabilities.validate(inputInfo, request.params)
                if (validation.isNotEmpty()) {
                    val code = when (validation.first().code) {
                        SttValidationErrorCode.UNSUPPORTED_FORMAT -> JobErrorCode.UNSUPPORTED_FORMAT
                        SttValidationErrorCode.TOO_LARGE -> JobErrorCode.TOO_LARGE
                        else -> JobErrorCode.VALIDATION
                    }
                    throw JobFailureException(
                        JobError(
                            code = code,
                            message = "Unsupported STT parameters",
                        )
                    )
                }

                val bytes = request.input.data ?: mediaStore.read(request.input.localPath)
                context.updateProgress(JobProgress(message = "Uploading audio"))

                val response = try {
                    client.submitFormWithBinaryData(
                        url = "$endpoint/v1/stt/transcribe",
                        formData = formData {
                            append("model", request.modelKey.modelId.value)
                            request.params.language?.let { append("language", it) }
                            append("diarization", request.params.diarization.toString())
                            append("timestamps", request.params.timestamps.toString())
                            append(
                                "audio",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, request.input.mimeType)
                                    val fileName = request.input.fileName ?: "audio"
                                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                }
                            )
                        },
                    ) {
                        accept(ContentType.Application.Json)
                    }
                } catch (throwable: Throwable) {
                    throw JobFailureException(
                        JobError(
                            code = JobErrorCode.NETWORK,
                            message = "Network error",
                            cause = throwable.message,
                        )
                    )
                }

                if (response.status != HttpStatusCode.OK) {
                    throw JobFailureException(
                        JobError(
                            code = JobErrorCode.REMOTE_ERROR,
                            message = "HTTP ${response.status.value}",
                        )
                    )
                }

                context.updateProgress(JobProgress(message = "Transcribing"))
                val payload = response.body<SttResponse>()
                if (!payload.error.isNullOrBlank()) {
                    throw JobFailureException(
                        JobError(
                            code = JobErrorCode.REMOTE_ERROR,
                            message = payload.error,
                        )
                    )
                }
                val segments = payload.segments?.map { segment ->
                    SttSegment(
                        startMillis = segment.startMillis,
                        endMillis = segment.endMillis,
                        text = segment.text,
                    )
                }.orEmpty()
                context.emitOutput(
                    SttResult(
                        text = payload.text,
                        segments = segments,
                        language = payload.language,
                    )
                )
            }
        }
    }

    override fun createTtsJob(request: TtsRequest): JobDefinition<TtsResult> {
        return object : JobDefinition<TtsResult> {
            override val id = request.jobId
            override val description = "Remote TTS synthesis"

            override suspend fun run(context: JobExecutionContext<TtsResult>) {
                val endpoint = validateEndpoint()
                val validation = ttsCapabilities.validate(request.input.text, request.params)
                if (validation.isNotEmpty()) {
                    throw JobFailureException(
                        JobError(
                            code = JobErrorCode.VALIDATION,
                            message = "Unsupported TTS parameters",
                        )
                    )
                }

                context.updateProgress(JobProgress(message = "Synthesizing"))

                val response = try {
                    client.post("$endpoint/v1/tts/synthesize") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.parse(request.params.format))
                        setBody(
                            TtsRequestPayload(
                                model = request.modelKey.modelId.value,
                                text = request.input.text,
                                voiceId = request.params.voiceId,
                                format = request.params.format,
                                sampleRateHz = request.params.sampleRateHz,
                            )
                        )
                    }
                } catch (throwable: Throwable) {
                    throw JobFailureException(
                        JobError(
                            code = JobErrorCode.NETWORK,
                            message = "Network error",
                            cause = throwable.message,
                        )
                    )
                }

                if (response.status != HttpStatusCode.OK) {
                    throw JobFailureException(
                        JobError(
                            code = JobErrorCode.REMOTE_ERROR,
                            message = "HTTP ${response.status.value}",
                        )
                    )
                }

                val bytes = response.body<ByteArray>()
                val contentType = response.contentType()?.toString() ?: request.params.format
                context.emitOutput(
                    TtsResult(
                        audioBytes = bytes,
                        mimeType = contentType,
                        sampleRateHz = request.params.sampleRateHz,
                        durationMillis = null,
                    )
                )
            }
        }
    }

    private fun validateEndpoint(): String {
        val baseUrl = baseUrlProvider().trim()
        if (baseUrl.isBlank()) {
            throw JobFailureException(
                JobError(
                    code = JobErrorCode.OFFLINE,
                    message = "Remote node address is not configured",
                )
            )
        }
        return SyncEndpointPolicy.validate(
            baseUrl = baseUrl,
            isDebug = isDebug,
            allowlistedHosts = allowlistedHosts,
        ).getOrElse { error ->
            throw JobFailureException(
                JobError(
                    code = JobErrorCode.VALIDATION,
                    message = error.message ?: "Invalid endpoint",
                )
            )
        }
    }
}

@Serializable
private data class SttResponse(
    val text: String = "",
    val segments: List<SttResponseSegment>? = null,
    val language: String? = null,
    val error: String? = null,
)

@Serializable
private data class SttResponseSegment(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)

@Serializable
private data class TtsRequestPayload(
    val model: String,
    val text: String,
    val voiceId: String,
    val format: String,
    val sampleRateHz: Int? = null,
)
