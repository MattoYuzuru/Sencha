package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveModelUseCaseTest {

    @Test
    fun returnsModelWhenPresent() {
        val model = ModelDescriptor(
            id = ModelId("local-llm"),
            displayName = "Local LLM",
            capabilities = setOf(ModelCapability.LLM),
        )
        val catalog = StubCatalog(listOf(model))
        val useCase = ResolveModelUseCase(catalog)

        val result = useCase.execute(model.id)

        assertEquals(model, result)
    }

    private class StubCatalog(
        private val models: List<ModelDescriptor>
    ) : ModelCatalog {
        override fun listModels(): List<ModelDescriptor> = models

        override fun findById(id: ModelId): ModelDescriptor? = models.firstOrNull { it.id == id }
    }
}
