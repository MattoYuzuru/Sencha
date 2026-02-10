package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.OnlineModelEntry

data class OnlineCatalogState(
    val entries: List<OnlineModelEntry>,
    val errorMessage: String? = null,
    val lastUpdatedEpochMillis: Long? = null,
)
