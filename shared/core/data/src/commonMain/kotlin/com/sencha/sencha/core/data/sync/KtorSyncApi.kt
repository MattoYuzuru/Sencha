package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.NodeInfo
import com.sencha.sencha.core.domain.sync.SyncEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class KtorSyncApi(
    private val baseUrl: String,
    private val client: HttpClient,
    private val sessionStore: SessionStore,
) : SyncApi {
    override suspend fun registerDevice(request: RegisterDeviceRequest): RegisterDeviceResponse {
        return client.post("$baseUrl/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun whoAmI(): WhoAmIResponse {
        return client.get("$baseUrl/v1/auth/whoami") {
            authorize()
        }.body()
    }

    override suspend fun uploadEvents(events: List<SyncEvent>): EventBatchResponse {
        return client.post("$baseUrl/v1/events/batch") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(EventBatchRequest(events))
        }.body()
    }

    override suspend fun fetchEventsSince(cursor: Long?, limit: Int): EventsSinceResponse {
        val query = buildString {
            append("$baseUrl/v1/events/since?limit=$limit")
            if (cursor != null) {
                append("&cursor=$cursor")
            }
        }
        return client.get(query) {
            authorize()
        }.body()
    }

    override suspend fun presign(request: PresignRequest): PresignResponse {
        return client.post("$baseUrl/v1/blobs/presign") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun fetchNodes(): List<NodeInfo> {
        val response: NodesResponse = client.get("$baseUrl/v1/nodes") {
            authorize()
        }.body()
        return response.nodes
    }

    override suspend fun upsertNode(request: NodeUpsertRequest): NodeInfo {
        return client.post("$baseUrl/v1/nodes") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun health(): HealthResponse {
        return client.get("$baseUrl/v1/health").body()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        val token = sessionStore.load()?.sessionToken
            ?: return
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    @Serializable
    private data class NodesResponse(
        val nodes: List<NodeInfo>,
    )
}
