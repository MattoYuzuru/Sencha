package com.sencha.sencha.core.model

data class ModelId(val value: String)

enum class ModelCapability {
    LLM,
    STT,
    TTS,
    VISION,
    VIDEO,
}

data class ModelDescriptor(
    val id: ModelId,
    val displayName: String,
    val capabilities: Set<ModelCapability>,
    val parameters: ModelParameters = ModelParameters(),
)

data class ModelParameters(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)
