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
        val entry = ModelEntry(
            key = ModelKey(ModelProviderId("local"), model.id),
            descriptor = model,
        )
        val catalog = StubCatalog(listOf(entry))
        val useCase = ResolveModelUseCase(catalog)

        val result = useCase.execute(entry.key)

        assertEquals(entry, result)
    }

    private class StubCatalog(
        private val models: List<ModelEntry>
    ) : ModelCatalog {
        override fun listModels(): List<ModelEntry> = models

        override fun findByKey(key: ModelKey): ModelEntry? = models.firstOrNull { it.key == key }
    }
}
