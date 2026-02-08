package com.sencha.sencha.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelId(val value: String)

@Serializable
enum class ModelCapability {
    LLM,
    STT,
    TTS,
    VISION,
    VIDEO,
}

@Serializable
enum class ModelCategory {
    CHAT,
    SPEECH_TO_TEXT,
    TEXT_TO_SPEECH,
    VISION,
    VIDEO,
}

@Serializable
data class ModelResourceProfile(
    val minRamMb: Int? = null,
    val minDiskMb: Int? = null,
    val requiresNetwork: Boolean = false,
)

@Serializable
data class ModelDescriptor(
    val id: ModelId,
    val displayName: String,
    val capabilities: Set<ModelCapability>,
    val parameters: ModelParameters = ModelParameters(),
    val resources: ModelResourceProfile = ModelResourceProfile(),
)

@Serializable
data class ModelParameters(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)

fun ModelDescriptor.categories(): Set<ModelCategory> = buildSet {
    if (ModelCapability.LLM in capabilities) add(ModelCategory.CHAT)
    if (ModelCapability.STT in capabilities) add(ModelCategory.SPEECH_TO_TEXT)
    if (ModelCapability.TTS in capabilities) add(ModelCategory.TEXT_TO_SPEECH)
    if (ModelCapability.VISION in capabilities) add(ModelCategory.VISION)
    if (ModelCapability.VIDEO in capabilities) add(ModelCategory.VIDEO)
}
