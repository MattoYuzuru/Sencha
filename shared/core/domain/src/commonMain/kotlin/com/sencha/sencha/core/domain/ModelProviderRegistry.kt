package com.sencha.sencha.core.domain

interface ModelProviderRegistry {
    fun providers(): List<ModelProvider>

    fun providerFor(key: ModelKey): ModelProvider?
}
