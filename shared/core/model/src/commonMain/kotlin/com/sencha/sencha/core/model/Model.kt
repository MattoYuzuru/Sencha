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
enum class ModelType {
    CHAT,
    STT,
    TTS,
    VISION,
    IMAGE_GEN,
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
enum class ModelFormat {
    GGUF,
    REMOTE_SERVICE,
    ON_DEVICE,
}

@Serializable
enum class ModelRuntime {
    LLAMA_CPP,
    REMOTE,
    REMOTE_NODE,
    UNKNOWN,
}

@Serializable
enum class ModelSourceType {
    LOCAL,
    REMOTE,
    DOWNLOAD,
}

@Serializable
data class ModelSource(
    val type: ModelSourceType,
    val downloadUrl: String? = null,
) {
    companion object {
        fun local(): ModelSource = ModelSource(ModelSourceType.LOCAL)
        fun remote(): ModelSource = ModelSource(ModelSourceType.REMOTE)
        fun download(url: String): ModelSource = ModelSource(ModelSourceType.DOWNLOAD, downloadUrl = url)
    }
}

@Serializable
data class ModelArtifact(
    val format: ModelFormat,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val license: String? = null,
    val quantization: String? = null,
)

@Serializable
data class ModelResourceProfile(
    val minRamMb: Int? = null,
    val minDiskMb: Int? = null,
    val maxContextTokens: Int? = null,
    val requiresNetwork: Boolean = false,
)

@Serializable
data class ModelDescriptor(
    val id: ModelId,
    val displayName: String,
    val capabilities: Set<ModelCapability>,
    val types: Set<ModelType> = emptySet(),
    val parameters: ModelParameters = ModelParameters(),
    val resources: ModelResourceProfile = ModelResourceProfile(),
    val runtime: ModelRuntime = ModelRuntime.UNKNOWN,
    val source: ModelSource = ModelSource.local(),
    val artifact: ModelArtifact? = null,
    val sttCapabilities: SttCapabilities? = null,
    val ttsCapabilities: TtsCapabilities? = null,
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

fun ModelDescriptor.types(): Set<ModelType> {
    if (types.isNotEmpty()) return types
    return buildSet {
        if (ModelCapability.LLM in capabilities) add(ModelType.CHAT)
        if (ModelCapability.STT in capabilities) add(ModelType.STT)
        if (ModelCapability.TTS in capabilities) add(ModelType.TTS)
        if (ModelCapability.VISION in capabilities) add(ModelType.VISION)
    }
}
