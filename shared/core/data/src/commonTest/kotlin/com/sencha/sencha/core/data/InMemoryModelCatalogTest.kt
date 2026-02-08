package com.sencha.sencha.core.data

import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryModelCatalogTest {

    @Test
    fun upsertReplacesModel() {
        val catalog = InMemoryModelCatalog()
        val model = ModelDescriptor(
            id = ModelId("local-llm"),
            displayName = "Local LLM",
            capabilities = setOf(ModelCapability.LLM),
        )

        catalog.upsert(model)

        assertEquals(model, catalog.findById(model.id))
    }
}
