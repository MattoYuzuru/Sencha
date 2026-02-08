package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ModelCatalog
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId

class InMemoryModelCatalog(
    initialModels: List<ModelDescriptor> = emptyList(),
) : ModelCatalog {
    private val modelsById = initialModels.associateBy { it.id }.toMutableMap()

    override fun listModels(): List<ModelDescriptor> = modelsById.values.sortedBy { it.displayName }

    override fun findById(id: ModelId): ModelDescriptor? = modelsById[id]

    fun upsert(model: ModelDescriptor) {
        modelsById[model.id] = model
    }
}
