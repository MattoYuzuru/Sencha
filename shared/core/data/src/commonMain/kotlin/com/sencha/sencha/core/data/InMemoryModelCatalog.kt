package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ModelCatalog
import com.sencha.sencha.core.domain.ModelEntry
import com.sencha.sencha.core.domain.ModelKey

class InMemoryModelCatalog(
    initialModels: List<ModelEntry> = emptyList(),
) : ModelCatalog {
    private val modelsByKey = initialModels.associateBy { it.key }.toMutableMap()

    override fun listModels(): List<ModelEntry> =
        modelsByKey.values.sortedBy { it.descriptor.displayName }

    override fun findByKey(key: ModelKey): ModelEntry? = modelsByKey[key]

    fun upsert(model: ModelEntry) {
        modelsByKey[model.key] = model
    }
}
