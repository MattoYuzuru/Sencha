package com.sencha.sencha.core.model

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

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

    @Test
    fun serializationRoundTrip() {
        val descriptor = ModelDescriptor(
            id = ModelId("local-llm"),
            displayName = "Local LLM",
            capabilities = setOf(ModelCapability.LLM),
            parameters = ModelParameters(temperature = 0.6, maxTokens = 512),
            resources = ModelResourceProfile(
                minRamMb = 4096,
                minDiskMb = 2048,
                maxContextTokens = 4096,
                requiresNetwork = false,
            ),
            runtime = ModelRuntime.LLAMA_CPP,
            source = ModelSource.local(),
            artifact = ModelArtifact(
                format = ModelFormat.GGUF,
                sizeBytes = 1024,
                sha256 = "deadbeef",
                license = "Apache-2.0",
                quantization = "Q4_K_M",
            ),
        )

        val json = Json.encodeToString(ModelDescriptor.serializer(), descriptor)
        val decoded = Json.decodeFromString(ModelDescriptor.serializer(), json)

        assertEquals(descriptor, decoded)
    }
}
