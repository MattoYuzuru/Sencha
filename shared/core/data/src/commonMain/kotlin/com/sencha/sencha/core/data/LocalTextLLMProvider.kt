package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ChatDelta
import com.sencha.sencha.core.domain.ChatRequest
import com.sencha.sencha.core.domain.ChatRole
import com.sencha.sencha.core.domain.ModelProvider
import com.sencha.sencha.core.domain.ModelProviderId
import com.sencha.sencha.core.domain.ModelProviderInfo
import com.sencha.sencha.core.domain.ModelProviderKind
import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.jobs.JobExecutionContext
import com.sencha.sencha.core.jobs.JobId
import com.sencha.sencha.core.jobs.JobProgress
import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import com.sencha.sencha.core.model.ModelResourceProfile
import kotlin.random.Random
import kotlin.time.Clock

class LocalTextLLMProvider(
    private val modelId: ModelId = ModelId("local-text"),
) : ModelProvider {
    override val info = ModelProviderInfo(
        id = ModelProviderId("local"),
        displayName = "Local",
        kind = ModelProviderKind.LOCAL,
    )

    override suspend fun listModels(): Result<List<ModelDescriptor>> {
        val model = ModelDescriptor(
            id = modelId,
            displayName = "Local Text",
            capabilities = setOf(ModelCapability.LLM),
            resources = ModelResourceProfile(minRamMb = 512, minDiskMb = 256, requiresNetwork = false),
        )
        return Result.success(listOf(model))
    }

    override fun createChatJob(request: ChatRequest): JobDefinition<ChatDelta> {
        return object : JobDefinition<ChatDelta> {
            override val id = JobId(
                "job-local-${request.modelKey.modelId.value}-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}"
            )
            override val description = "Local text generation"

            override suspend fun run(context: JobExecutionContext<ChatDelta>) {
                val lastUserMessage = request.messages.lastOrNull { it.role == ChatRole.USER }?.content
                val response = if (lastUserMessage.isNullOrBlank()) {
                    "Локальная модель готова. Спроси что-нибудь."
                } else {
                    "Локальный ответ: $lastUserMessage"
                }
                val parts = response.split(" ")
                val total = parts.size.toLong().coerceAtLeast(1)

                parts.forEachIndexed { index, part ->
                    context.updateProgress(JobProgress(current = index.toLong() + 1, total = total))
                    context.emitOutput(ChatDelta(text = if (index == parts.lastIndex) part else "$part "))
                }
                context.emitOutput(ChatDelta(text = "", isFinal = true))
            }
        }
    }
}
