package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ModelCatalogError
import com.sencha.sencha.core.domain.ModelCatalogState
import com.sencha.sencha.core.domain.ModelEntry
import com.sencha.sencha.core.domain.ModelKey
import com.sencha.sencha.core.domain.ModelProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelCatalogService(
    private val registry: ModelProviderRegistry,
) {
    private val stateFlow = MutableStateFlow(ModelCatalogState(entries = emptyList(), errors = emptyList()))

    fun state(): StateFlow<ModelCatalogState> = stateFlow.asStateFlow()

    suspend fun refresh() {
        val entries = mutableListOf<ModelEntry>()
        val errors = mutableListOf<ModelCatalogError>()

        registry.providers().forEach { provider ->
            val result = provider.listModels()
            result
                .onSuccess { models ->
                    models.forEach { model ->
                        entries.add(ModelEntry(ModelKey(provider.info.id, model.id), model))
                    }
                }
                .onFailure { failure ->
                    errors.add(ModelCatalogError(provider.info.id, failure.message ?: "Unknown error"))
                }
        }

        stateFlow.value = ModelCatalogState(
            entries = entries.sortedBy { it.descriptor.displayName },
            errors = errors,
        )
    }
}
