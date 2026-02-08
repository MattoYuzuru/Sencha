package com.sencha.sencha.core.model

import kotlin.test.Test
import kotlin.test.assertTrue

class ModelDescriptorTest {

    @Test
    fun descriptorPreservesCapabilities() {
        val descriptor = ModelDescriptor(
            id = ModelId("local-llm"),
            displayName = "Local LLM",
            capabilities = setOf(ModelCapability.LLM, ModelCapability.VISION),
        )

        assertTrue(ModelCapability.LLM in descriptor.capabilities)
        assertTrue(ModelCapability.VISION in descriptor.capabilities)
    }
}
