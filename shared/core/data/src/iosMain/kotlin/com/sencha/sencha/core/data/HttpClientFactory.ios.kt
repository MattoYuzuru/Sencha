package com.sencha.sencha.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual object HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(Darwin) {
        applyDefaultConfig()
    }
}
