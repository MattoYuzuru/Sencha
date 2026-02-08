package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ModelProvider
import com.sencha.sencha.core.domain.ModelProviderRegistry
import com.sencha.sencha.core.domain.ModelKey

class DefaultModelProviderRegistry(
    private val providers: List<ModelProvider>,
) : ModelProviderRegistry {
    private val providersById = providers.associateBy { it.info.id }

    override fun providers(): List<ModelProvider> = providers

    override fun providerFor(key: ModelKey): ModelProvider? = providersById[key.providerId]
}
