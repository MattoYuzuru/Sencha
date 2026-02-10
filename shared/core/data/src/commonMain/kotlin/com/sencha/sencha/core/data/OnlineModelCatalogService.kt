package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.OnlineCatalogState
import com.sencha.sencha.core.model.OnlineModelManifest
import com.sencha.sencha.core.data.sync.SyncEndpointPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

class OnlineModelCatalogService(
    private val manifestUrlProvider: () -> String,
    private val client: HttpClient = HttpClientFactory.create(),
    private val isDebug: Boolean,
    private val allowlistedHosts: Set<String>,
    private val clock: Clock = Clock.System,
) {
    private val stateFlow = MutableStateFlow(OnlineCatalogState(entries = emptyList()))

    fun state(): StateFlow<OnlineCatalogState> = stateFlow.asStateFlow()

    suspend fun refresh() {
        val url = manifestUrlProvider().trim()
        if (url.isBlank()) {
            stateFlow.value = OnlineCatalogState(
                entries = emptyList(),
                errorMessage = "Catalog URL is not configured",
                lastUpdatedEpochMillis = clock.now().toEpochMilliseconds(),
            )
            return
        }
        val validated = SyncEndpointPolicy.validate(url, isDebug, allowlistedHosts).getOrElse { error ->
            stateFlow.value = OnlineCatalogState(
                entries = emptyList(),
                errorMessage = error.message ?: "Invalid catalog URL",
                lastUpdatedEpochMillis = clock.now().toEpochMilliseconds(),
            )
            return
        }
        try {
            val manifest = client.get(validated) {
                accept(ContentType.Application.Json)
            }.body<OnlineModelManifest>()
            stateFlow.value = OnlineCatalogState(
                entries = manifest.models,
                errorMessage = null,
                lastUpdatedEpochMillis = clock.now().toEpochMilliseconds(),
            )
        } catch (throwable: Throwable) {
            stateFlow.value = OnlineCatalogState(
                entries = emptyList(),
                errorMessage = throwable.message ?: "Failed to load catalog",
                lastUpdatedEpochMillis = clock.now().toEpochMilliseconds(),
            )
        }
    }
}
