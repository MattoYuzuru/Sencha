package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import com.sencha.sencha.core.model.ModelResourceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelAvailabilityPolicyTest {

    @Test
    fun allowsModelWhenConstraintsSatisfied() {
        val model = ModelDescriptor(
            id = ModelId("local-llm"),
            displayName = "Local LLM",
            capabilities = setOf(ModelCapability.LLM),
            resources = ModelResourceProfile(minRamMb = 2048, minDiskMb = 1024, requiresNetwork = false),
        )
        val device = DeviceProfile(availableRamMb = 4096, availableDiskMb = 4096, isNetworkAvailable = true)

        val support = ModelAvailabilityPolicy().evaluate(model, device)

        assertTrue(support.isSupported)
        assertEquals(emptyList(), support.violations)
    }

    @Test
    fun blocksModelWhenResourcesInsufficient() {
        val model = ModelDescriptor(
            id = ModelId("remote-llm"),
            displayName = "Remote LLM",
            capabilities = setOf(ModelCapability.LLM),
            resources = ModelResourceProfile(minRamMb = 8192, minDiskMb = 2048, requiresNetwork = true),
        )
        val device = DeviceProfile(availableRamMb = 4096, availableDiskMb = 1024, isNetworkAvailable = false)

        val support = ModelAvailabilityPolicy().evaluate(model, device)

        assertFalse(support.isSupported)
        assertEquals(
            listOf(
                ModelConstraintViolation.INSUFFICIENT_RAM,
                ModelConstraintViolation.INSUFFICIENT_DISK,
                ModelConstraintViolation.NETWORK_REQUIRED,
            ),
            support.violations,
        )
    }
}
