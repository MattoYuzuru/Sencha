package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.NodeInfo
import com.sencha.sencha.core.domain.sync.SyncEvent
import io.ktor.client.HttpClient

class DynamicSyncApi(
    private val baseUrlProvider: () -> String,
    private val client: HttpClient,
    private val sessionStore: SessionStore,
) : SyncApi {
    private fun delegate(): SyncApi = KtorSyncApi(baseUrlProvider(), client, sessionStore)

    override suspend fun registerDevice(request: RegisterDeviceRequest): RegisterDeviceResponse {
        return delegate().registerDevice(request)
    }

    override suspend fun whoAmI(): WhoAmIResponse {
        return delegate().whoAmI()
    }

    override suspend fun uploadEvents(events: List<SyncEvent>): EventBatchResponse {
        return delegate().uploadEvents(events)
    }

    override suspend fun fetchEventsSince(cursor: Long?, limit: Int): EventsSinceResponse {
        return delegate().fetchEventsSince(cursor, limit)
    }

    override suspend fun presign(request: PresignRequest): PresignResponse {
        return delegate().presign(request)
    }

    override suspend fun fetchNodes(): List<NodeInfo> {
        return delegate().fetchNodes()
    }

    override suspend fun upsertNode(request: NodeUpsertRequest): NodeInfo {
        return delegate().upsertNode(request)
    }

    override suspend fun health(): HealthResponse {
        return delegate().health()
    }
}
