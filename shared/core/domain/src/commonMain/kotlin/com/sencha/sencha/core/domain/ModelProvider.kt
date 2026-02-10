package com.sencha.sencha.core.domain

import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.model.ModelDescriptor

enum class ModelProviderKind {
    LOCAL,
    REMOTE,
}

data class ModelProviderInfo(
    val id: ModelProviderId,
    val displayName: String,
    val kind: ModelProviderKind,
)

interface ModelProvider {
    val info: ModelProviderInfo

    suspend fun listModels(): Result<List<ModelDescriptor>>

    fun createChatJob(request: ChatRequest): JobDefinition<ChatDelta>

    fun createSttJob(request: SttRequest): JobDefinition<SttResult> = error("STT not supported")

    fun createTtsJob(request: TtsRequest): JobDefinition<TtsResult> = error("TTS not supported")
}
