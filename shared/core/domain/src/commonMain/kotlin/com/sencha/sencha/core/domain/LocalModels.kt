package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import kotlinx.serialization.Serializable

@Serializable
data class LocalModelRecord(
    val descriptor: ModelDescriptor,
    val fileUri: String,
)

@Serializable
data class ModelDownloadSpec(
    val descriptor: ModelDescriptor,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Serializable
data class ModelImportRequest(
    val uri: String,
)

@Serializable
data class ModelDownloadRequest(
    val spec: ModelDownloadSpec,
)

interface LocalModelStore {
    suspend fun list(): List<LocalModelRecord>

    suspend fun find(modelId: ModelId): LocalModelRecord?

    suspend fun upsert(record: LocalModelRecord)

    suspend fun remove(modelId: ModelId)
}

interface ModelInstaller {
    suspend fun importModel(request: ModelImportRequest): Result<LocalModelRecord>

    suspend fun downloadModel(request: ModelDownloadRequest): Result<LocalModelRecord>

    suspend fun validate(record: LocalModelRecord): Result<Unit>
}

data class InferenceConfig(
    val maxContextTokens: Int,
    val maxOutputTokens: Int,
    val temperature: Double,
)

interface LocalTextInferenceEngine {
    suspend fun load(model: LocalModelRecord, config: InferenceConfig): Result<Unit>

    suspend fun generate(request: ChatRequest, onToken: suspend (String, Boolean) -> Unit): Result<Unit>

    fun cancel()

    fun unload()
}
