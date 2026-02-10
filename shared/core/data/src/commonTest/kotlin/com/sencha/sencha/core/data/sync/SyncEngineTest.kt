package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.BlobKeyBuilder
import com.sencha.sencha.core.domain.sync.BlobRecord
import com.sencha.sencha.core.domain.sync.BlobStatus
import com.sencha.sencha.core.domain.sync.EventPayloadEnvelope
import com.sencha.sencha.core.domain.sync.SyncEvent
import com.sencha.sencha.core.domain.sync.SyncEventTypes
import com.sencha.sencha.core.domain.sync.UlidGenerator
import com.sencha.sencha.core.jobs.InMemoryJobEngine
import com.sencha.sencha.core.jobs.JobState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.serialization.json.encodeToJsonElement

class SyncEngineTest {
    @Test
    fun mergeRemoteIsIdempotent() {
        val store = FakeEventStore()
        val event = sampleEvent()
        val first = store.mergeRemote(listOf(event))
        val second = store.mergeRemote(listOf(event))

        assertEquals(1, first.inserted)
        assertEquals(0, first.ignored)
        assertEquals(0, second.inserted)
        assertEquals(1, second.ignored)
        assertEquals(1, store.allEvents().size)
    }

    @Test
    fun cursorUpdatesAfterDownload() = runTest {
        val store = FakeEventStore()
        val api = FakeSyncApi(
            events = listOf(sampleEvent()),
            nextCursor = 42,
        )
        val engine = SyncEngine(
            eventStore = store,
            blobStore = FakeBlobStore(),
            api = api,
            blobTransfer = FakeBlobTransfer(),
            jobEngine = InMemoryJobEngine(scope = this),
            deviceId = "device-1",
        )

        val handle = engine.enqueueDownloadEvents()
        handle.snapshot.first { it.state == JobState.SUCCEEDED }

        assertEquals(42, store.lastAckCursor())
    }

    @Test
    fun uploadJobMarksEventsUploaded() = runTest {
        val store = FakeEventStore()
        store.appendLocal(sampleEvent())
        val api = FakeSyncApi()
        val engine = SyncEngine(
            eventStore = store,
            blobStore = FakeBlobStore(),
            api = api,
            blobTransfer = FakeBlobTransfer(),
            jobEngine = InMemoryJobEngine(scope = this),
            deviceId = "device-1",
        )

        val handle = engine.enqueueUploadEvents()
        handle.snapshot.first { it.state == JobState.SUCCEEDED }

        assertTrue(store.pendingEvents(10).isEmpty())
        assertEquals(1, api.uploaded.size)
    }

    @Test
    fun blobKeyIsUserScoped() {
        val key = BlobKeyBuilder.build(
            userId = "user-1",
            chatId = "chat-9",
            blobId = "blob-7",
            sha256 = "deadbeef",
            extension = ".jpg",
        )
        assertEquals("u/user-1/c/chat-9/b/blob-7-deadbeef.jpg", key)
    }

    @Test
    fun blobStatusTransitionsToUploaded() = runTest {
        val blobStore = FakeBlobStore()
        val blob = BlobRecord(
            blobId = "blob-1",
            chatId = "chat-1",
            sha256 = "abcd",
            size = 10,
            mime = "image/png",
            localPath = "/tmp/blob.png",
            remoteKey = null,
            status = BlobStatus.PENDING,
            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        blobStore.upsert(blob)

        val api = FakeSyncApi()
        val engine = SyncEngine(
            eventStore = FakeEventStore(),
            blobStore = blobStore,
            api = api,
            blobTransfer = FakeBlobTransfer(),
            jobEngine = InMemoryJobEngine(scope = this),
            deviceId = "device-1",
        )

        val handle = engine.enqueueBlobUploads()
        handle.snapshot.first { it.state == JobState.SUCCEEDED }

        val updated = blobStore.find("blob-1")
        assertEquals(BlobStatus.UPLOADED, updated?.status)
    }

    private fun sampleEvent(): SyncEvent {
        return SyncEvent(
            eventId = UlidGenerator.newUlid(),
            deviceId = "device-1",
            chatId = "chat-1",
            type = SyncEventTypes.MESSAGE_CREATED,
            payload = EventPayloadEnvelope(schemaVersion = 1, data = SyncJson.instance.encodeToJsonElement("test")),
            createdAtEpochMillis = 123,
        )
    }
}

private class FakeEventStore : EventStore {
    private val events = mutableListOf<SyncEvent>()
    private val pending = mutableListOf<SyncEvent>()
    private var lastCursor: Long? = null
    private var lastSync: Long? = null
    private var lastError: String? = null

    override fun appendLocal(event: SyncEvent) {
        if (events.any { it.eventId == event.eventId }) return
        events.add(event)
        pending.add(event)
    }

    override fun mergeRemote(events: List<SyncEvent>): MergeResult {
        var inserted = 0
        var ignored = 0
        for (event in events) {
            if (this.events.any { it.eventId == event.eventId }) {
                ignored += 1
            } else {
                this.events.add(event)
                inserted += 1
            }
        }
        return MergeResult(inserted, ignored)
    }

    override fun pendingEvents(limit: Int): List<SyncEvent> = pending.take(limit)

    override fun markUploaded(eventIds: List<String>) {
        pending.removeAll { it.eventId in eventIds }
    }

    override fun allEvents(): List<SyncEvent> = events.toList()

    override fun pendingEventCount(): Long = pending.size.toLong()

    override fun lastAckCursor(): Long? = lastCursor

    override fun updateLastAckCursor(cursor: Long) {
        lastCursor = cursor
    }

    override fun lastSyncAt(): Long? = lastSync

    override fun updateLastSyncAt(epochMillis: Long) {
        lastSync = epochMillis
    }

    override fun lastErrorMessage(): String? = lastError

    override fun updateLastErrorMessage(message: String?) {
        lastError = message
    }
}

private class FakeBlobStore : BlobStore {
    private val blobs = mutableMapOf<String, BlobRecord>()

    override fun upsert(blob: BlobRecord) {
        blobs[blob.blobId] = blob
    }

    override fun find(blobId: String): BlobRecord? = blobs[blobId]

    override fun pendingBlobs(limit: Int): List<BlobRecord> {
        return blobs.values.filter { it.status == BlobStatus.PENDING || it.status == BlobStatus.FAILED }.take(limit)
    }

    override fun updateStatus(blobId: String, status: BlobStatus, remoteKey: String?, localPath: String?): BlobRecord? {
        val current = blobs[blobId] ?: return null
        val updated = current.copy(status = status, remoteKey = remoteKey ?: current.remoteKey, localPath = localPath)
        blobs[blobId] = updated
        return updated
    }

    override fun pendingBlobCount(): Long = blobs.values.count { it.status == BlobStatus.PENDING || it.status == BlobStatus.FAILED }.toLong()
}

private class FakeBlobTransfer : BlobTransfer {
    override suspend fun upload(presign: PresignResponse, blob: BlobRecord) = Unit

    override suspend fun download(presign: PresignResponse): ByteArray = ByteArray(0)
}

private class FakeSyncApi(
    val events: List<SyncEvent> = emptyList(),
    val nextCursor: Long = 0,
) : SyncApi {
    val uploaded = mutableListOf<SyncEvent>()

    override suspend fun registerDevice(request: RegisterDeviceRequest): RegisterDeviceResponse {
        error("Not used")
    }

    override suspend fun whoAmI(): WhoAmIResponse {
        error("Not used")
    }

    override suspend fun uploadEvents(events: List<SyncEvent>): EventBatchResponse {
        uploaded.addAll(events)
        return EventBatchResponse(accepted = events.size)
    }

    override suspend fun fetchEventsSince(cursor: Long?, limit: Int): EventsSinceResponse {
        return EventsSinceResponse(events, nextCursor)
    }

    override suspend fun presign(request: PresignRequest): PresignResponse {
        return PresignResponse(
            url = "http://example.com/upload",
            method = "PUT",
            headers = emptyMap(),
            key = "u/user/c/chat/b/blob",
            expiresAtEpochMillis = 0,
        )
    }

    override suspend fun fetchNodes() = emptyList()

    override suspend fun upsertNode(request: NodeUpsertRequest): com.sencha.sencha.core.domain.sync.NodeInfo {
        error("Not used")
    }

    override suspend fun health(): HealthResponse {
        return HealthResponse(status = "ok", version = "test")
    }
}
