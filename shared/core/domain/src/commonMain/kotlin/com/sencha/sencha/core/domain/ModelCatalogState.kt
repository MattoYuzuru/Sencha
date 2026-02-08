package com.sencha.sencha.core.domain

data class ModelCatalogError(
    val providerId: ModelProviderId,
    val message: String,
)

data class ModelCatalogState(
    val entries: List<ModelEntry>,
    val errors: List<ModelCatalogError>,
)
