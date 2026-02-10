package com.sencha.sencha.core.model

import kotlinx.serialization.Serializable

@Serializable
data class OnlineModelManifest(
    val models: List<OnlineModelEntry> = emptyList(),
)

@Serializable
data class OnlineModelEntry(
    val modelId: String,
    val name: String,
    val type: ModelType,
    val runtime: ModelRuntime,
    val format: ModelFormat,
    val downloadUrl: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val license: String? = null,
    val resourceProfile: ModelResourceProfile = ModelResourceProfile(),
    val description: String? = null,
    val tags: List<String> = emptyList(),
)

fun OnlineModelEntry.toDescriptor(): ModelDescriptor {
    val capabilities = when (type) {
        ModelType.CHAT -> setOf(ModelCapability.LLM)
        ModelType.STT -> setOf(ModelCapability.STT)
        ModelType.TTS -> setOf(ModelCapability.TTS)
        ModelType.VISION -> setOf(ModelCapability.VISION)
        ModelType.IMAGE_GEN -> emptySet()
    }
    return ModelDescriptor(
        id = ModelId(modelId),
        displayName = name,
        capabilities = capabilities,
        types = setOf(type),
        resources = resourceProfile,
        runtime = runtime,
        source = when {
            downloadUrl != null -> ModelSource.download(downloadUrl)
            else -> ModelSource.remote()
        },
        artifact = ModelArtifact(
            format = format,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            license = license,
            quantization = null,
        ),
    )
}
