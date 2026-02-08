package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId

@JvmInline
value class ModelProviderId(val value: String)

data class ModelKey(
    val providerId: ModelProviderId,
    val modelId: ModelId,
)

data class ModelEntry(
    val key: ModelKey,
    val descriptor: ModelDescriptor,
)

interface ModelCatalog {
    fun listModels(): List<ModelEntry>

    fun findByKey(key: ModelKey): ModelEntry?
}

class ResolveModelUseCase(private val catalog: ModelCatalog) {
    fun execute(key: ModelKey): ModelEntry? = catalog.findByKey(key)
}
