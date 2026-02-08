package com.sencha.sencha.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual object HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(OkHttp) {
        applyDefaultConfig()
    }
}
