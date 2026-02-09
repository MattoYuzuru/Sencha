package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ChatDelta
import com.sencha.sencha.core.domain.ChatRequest
import com.sencha.sencha.core.domain.InferenceConfig
import com.sencha.sencha.core.domain.LocalModelStore
import com.sencha.sencha.core.domain.LocalTextInferenceEngine
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
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelRuntime
import com.sencha.sencha.core.model.ModelSource
import kotlin.random.Random
import kotlin.time.Clock

class LocalTextLLMProvider(
    private val store: LocalModelStore,
    private val engine: LocalTextInferenceEngine,
) : ModelProvider {
    override val info = ModelProviderInfo(
        id = ModelProviderId("local"),
        displayName = "Local",
        kind = ModelProviderKind.LOCAL,
    )

    override suspend fun listModels(): Result<List<ModelDescriptor>> {
        val models = store.list().map { record ->
            record.descriptor.copy(
                runtime = ModelRuntime.LLAMA_CPP,
                source = ModelSource.local(),
                resources = record.descriptor.resources.copy(requiresNetwork = false),
            )
        }
        return Result.success(models)
    }

    override fun createChatJob(request: ChatRequest): JobDefinition<ChatDelta> {
        return object : JobDefinition<ChatDelta> {
            override val id = JobId(
                "job-local-${request.modelKey.modelId.value}-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}"
            )
            override val description = "Local text generation"

            override suspend fun run(context: JobExecutionContext<ChatDelta>) {
                try {
                    val record = store.find(request.modelKey.modelId)
                        ?: throw JobFailureException(
                            JobError(
                                code = JobErrorCode.VALIDATION,
                                message = "Model not installed: ${request.modelKey.modelId.value}",
                            )
                        )
                    val descriptor = record.descriptor
                    val config = InferenceConfig(
                        maxContextTokens = descriptor.resources.maxContextTokens ?: 2048,
                        maxOutputTokens = descriptor.parameters.maxTokens ?: 256,
                        temperature = descriptor.parameters.temperature ?: 0.7,
                    )

                    val loadResult = engine.load(record, config)
                    if (loadResult.isFailure) {
                        engine.unload()
                        throw JobFailureException(toJobError(loadResult.exceptionOrNull()))
                    }

                    var emitted = 0L
                    val result = engine.generate(request) { token, isFinal ->
                        emitted += 1
                        context.updateProgress(JobProgress(current = emitted, total = null))
                        context.emitOutput(ChatDelta(text = token, isFinal = isFinal))
                    }

                    if (result.isFailure) {
                        engine.unload()
                        throw JobFailureException(toJobError(result.exceptionOrNull()))
                    }
                    context.emitOutput(ChatDelta(text = "", isFinal = true))
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    engine.unload()
                    throw cancel
                }
            }
        }
    }

    private fun toJobError(throwable: Throwable?): JobError {
        val message = throwable?.message ?: "Inference failed"
        val code = when {
            throwable is IllegalArgumentException -> JobErrorCode.VALIDATION
            message.contains("context", ignoreCase = true) -> JobErrorCode.VALIDATION
            message.contains("контекст", ignoreCase = true) -> JobErrorCode.VALIDATION
            message.contains("ram", ignoreCase = true) -> JobErrorCode.OUT_OF_MEMORY
            message.contains("memory", ignoreCase = true) -> JobErrorCode.OUT_OF_MEMORY
            message.contains("памят", ignoreCase = true) -> JobErrorCode.OUT_OF_MEMORY
            else -> JobErrorCode.UNKNOWN
        }
        return JobError(
            code = code,
            message = message,
            cause = throwable?.let { it::class.simpleName },
        )
    }
}
