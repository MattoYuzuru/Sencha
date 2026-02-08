package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId

interface ModelCatalog {
    fun listModels(): List<ModelDescriptor>

    fun findById(id: ModelId): ModelDescriptor?
}

class ResolveModelUseCase(private val catalog: ModelCatalog) {
    fun execute(id: ModelId): ModelDescriptor? = catalog.findById(id)
}
