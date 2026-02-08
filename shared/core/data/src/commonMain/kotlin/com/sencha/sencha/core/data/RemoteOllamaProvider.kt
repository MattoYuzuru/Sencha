package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ChatDelta
import com.sencha.sencha.core.domain.ChatRequest
import com.sencha.sencha.core.domain.ChatRole
import com.sencha.sencha.core.domain.ModelProvider
import com.sencha.sencha.core.domain.ModelProviderId
import com.sencha.sencha.core.domain.ModelProviderInfo
import com.sencha.sencha.core.domain.ModelProviderKind
import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.jobs.JobError
import com.sencha.sencha.core.jobs.JobErrorCode
import com.sencha.sencha.core.jobs.JobExecutionContext
import com.sencha.sencha.core.jobs.JobFailureException
import com.sencha.sencha.core.jobs.JobId
import com.sencha.sencha.core.jobs.JobProgress
import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import com.sencha.sencha.core.model.ModelResourceProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.utils.io.readLine
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

class RemoteOllamaProvider(
    private val baseUrl: String,
    private val client: HttpClient = HttpClientFactory.create(),
) : ModelProvider {
    override val info = ModelProviderInfo(
        id = ModelProviderId("ollama"),
        displayName = "Ollama",
        kind = ModelProviderKind.REMOTE,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun listModels(): Result<List<ModelDescriptor>> {
        val endpoint = validateEndpoint() ?: return Result.failure(
            IllegalArgumentException("Ollama endpoint must use https unless it is localhost")
        )

        return runCatching {
            val response = client.get("$endpoint/api/tags") {
                accept(ContentType.Application.Json)
            }.body<OllamaTagsResponse>()

            response.models.map { model ->
                val capabilities = if (model.name.contains("llava", ignoreCase = true)) {
                    setOf(ModelCapability.LLM, ModelCapability.VISION)
                } else {
                    setOf(ModelCapability.LLM)
                }
                val diskMb = model.size?.let { (it / (1024 * 1024)).toInt() }

                ModelDescriptor(
                    id = ModelId(model.name),
                    displayName = model.name,
                    capabilities = capabilities,
                    resources = ModelResourceProfile(
                        minDiskMb = diskMb,
                        requiresNetwork = true,
                    ),
                )
            }
        }
    }

    override fun createChatJob(request: ChatRequest): JobDefinition<ChatDelta> {
        return object : JobDefinition<ChatDelta> {
            override val id = JobId(
                "job-ollama-${request.modelKey.modelId.value}-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}"
            )
            override val description = "Ollama chat"

            override suspend fun run(context: JobExecutionContext<ChatDelta>) {
                val endpoint = validateEndpoint() ?: throw JobFailureException(
                    JobError(
                        code = JobErrorCode.VALIDATION,
                        message = "Ollama endpoint must use https unless it is localhost",
                    )
                )

                val payload = OllamaChatRequest(
                    model = request.modelKey.modelId.value,
                    stream = true,
                    messages = request.messages.map { message ->
                        OllamaChatMessage(
                            role = message.role.toOllamaRole(),
                            content = message.content,
                        )
                    },
                )

                try {
                    val statement = client.preparePost("$endpoint/api/chat") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        accept(ContentType.Application.Json)
                        setBody(payload)
                    }

                    statement.execute { response ->
                        if (response.status != HttpStatusCode.OK) {
                            throw JobFailureException(
                                JobError(
                                    code = JobErrorCode.NETWORK,
                                    message = "HTTP ${response.status.value}",
                                )
                            )
                        }
                        context.updateProgress(JobProgress(message = "Streaming"))
                        val channel = response.bodyAsChannel()
                        while (!channel.isClosedForRead) {
                            val line = channel.readLine() ?: break
                            if (line.isBlank()) continue
                            val chunk = json.decodeFromString(OllamaChatResponseChunk.serializer(), line)
                            if (chunk.error != null) {
                                throw JobFailureException(
                                    JobError(
                                        code = JobErrorCode.NETWORK,
                                        message = chunk.error,
                                    )
                                )
                            }
                            val text = chunk.message?.content
                            if (!text.isNullOrEmpty()) {
                                context.emitOutput(ChatDelta(text = text, isFinal = chunk.done))
                            }
                            if (chunk.done) break
                        }
                        context.emitOutput(ChatDelta(text = "", isFinal = true))
                    }
                } catch (failure: JobFailureException) {
                    throw failure
                } catch (throwable: Throwable) {
                    throw JobFailureException(
                        JobError(
                            code = JobErrorCode.NETWORK,
                            message = "Network error",
                            cause = throwable.message,
                        )
                    )
                }
            }
        }
    }

    private fun validateEndpoint(): String? {
        val url = Url(baseUrl)
        val isLocalhost = url.host == "localhost" || url.host == "127.0.0.1"
        val isTls = url.protocol.name == "https"
        return if (isTls || isLocalhost) baseUrl.trimEnd('/') else null
    }

    private fun ChatRole.toOllamaRole(): String = when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }
}

@Serializable
private data class OllamaTagsResponse(
    val models: List<OllamaModel> = emptyList(),
)

@Serializable
private data class OllamaModel(
    val name: String,
    val size: Long? = null,
)

@Serializable
private data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = true,
)

@Serializable
private data class OllamaChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class OllamaChatResponseChunk(
    val message: OllamaChatMessage? = null,
    val done: Boolean = false,
    val error: String? = null,
)
