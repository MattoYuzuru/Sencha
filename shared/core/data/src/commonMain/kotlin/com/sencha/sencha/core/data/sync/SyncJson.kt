package com.sencha.sencha.core.data.sync

import kotlinx.serialization.json.Json

internal object SyncJson {
    val instance = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}
