package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.data.sync.db.SyncDatabase
import com.sencha.sencha.core.domain.sync.BlobRecord
import com.sencha.sencha.core.domain.sync.BlobStatus
import com.sencha.sencha.core.domain.sync.EventPayloadEnvelope
import com.sencha.sencha.core.domain.sync.SyncEvent
import com.sencha.sencha.core.domain.sync.SyncStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class SqlSyncStore(
    private val database: SyncDatabase,
) : EventStore, BlobStore, SyncStatusStore {
    private val queries = database.syncDatabaseQueries

    override fun appendLocal(event: SyncEvent) {
        queries.insertLocalEvent(
            event_id = event.eventId,
            device_id = event.deviceId,
            chat_id = event.chatId,
            type = event.type,
            payload_json = SyncJson.instance.encodeToString(EventPayloadEnvelope.serializer(), event.payload),
            created_at = event.createdAtEpochMillis,
        )
    }

    override fun mergeRemote(events: List<SyncEvent>): MergeResult {
        var inserted = 0
        var ignored = 0
        for (event in events) {
            val existing = this.queries.selectEventById(event.eventId).executeAsOneOrNull()
            if (existing == null) {
                this.queries.insertRemoteEvent(
                    event_id = event.eventId,
                    device_id = event.deviceId,
                    chat_id = event.chatId,
                    type = event.type,
                    payload_json = SyncJson.instance.encodeToString(EventPayloadEnvelope.serializer(), event.payload),
                    created_at = event.createdAtEpochMillis,
                )
                inserted += 1
            } else {
                ignored += 1
            }
        }
        return MergeResult(inserted = inserted, ignored = ignored)
    }

    override fun pendingEvents(limit: Int): List<SyncEvent> {
        return queries.selectPendingEvents(limit.toLong()).executeAsList().map { record ->
            SyncEvent(
                eventId = record.event_id,
                deviceId = record.device_id,
                chatId = record.chat_id,
                type = record.type,
                payload = SyncJson.instance.decodeFromString(EventPayloadEnvelope.serializer(), record.payload_json),
                createdAtEpochMillis = record.created_at,
            )
        }
    }

    override fun markUploaded(eventIds: List<String>) {
        if (eventIds.isEmpty()) return
        queries.markUploaded(eventIds)
    }

    override fun allEvents(): List<SyncEvent> {
        return queries.selectAllEvents().executeAsList().map { record ->
            SyncEvent(
                eventId = record.event_id,
                deviceId = record.device_id,
                chatId = record.chat_id,
                type = record.type,
                payload = SyncJson.instance.decodeFromString(EventPayloadEnvelope.serializer(), record.payload_json),
                createdAtEpochMillis = record.created_at,
            )
        }
    }

    override fun pendingEventCount(): Long = queries.pendingEventCount().executeAsOne()

    override fun lastAckCursor(): Long? = queries.selectSyncState(KEY_LAST_ACK_CURSOR).executeAsOneOrNull()
        ?.toLongOrNull()

    override fun updateLastAckCursor(cursor: Long) {
        queries.upsertSyncState(KEY_LAST_ACK_CURSOR, cursor.toString())
    }

    override fun lastSyncAt(): Long? = queries.selectSyncState(KEY_LAST_SYNC_AT).executeAsOneOrNull()
        ?.toLongOrNull()

    override fun updateLastSyncAt(epochMillis: Long) {
        queries.upsertSyncState(KEY_LAST_SYNC_AT, epochMillis.toString())
    }

    override fun lastErrorMessage(): String? = queries.selectSyncState(KEY_LAST_ERROR_MESSAGE).executeAsOneOrNull()

    override fun updateLastErrorMessage(message: String?) {
        if (message == null) {
            queries.upsertSyncState(KEY_LAST_ERROR_MESSAGE, "")
        } else {
            queries.upsertSyncState(KEY_LAST_ERROR_MESSAGE, message)
        }
    }

    override fun upsert(blob: BlobRecord) {
        queries.insertBlob(
            blob_id = blob.blobId,
            chat_id = blob.chatId,
            sha256 = blob.sha256,
            size = blob.size,
            mime = blob.mime,
            local_path = blob.localPath,
            remote_key = blob.remoteKey,
            status = blob.status.name,
            created_at = blob.createdAtEpochMillis,
        )
    }

    override fun find(blobId: String): BlobRecord? {
        val record = queries.selectBlobById(blobId).executeAsOneOrNull() ?: return null
        return record.toBlobRecord()
    }

    override fun pendingBlobs(limit: Int): List<BlobRecord> {
        return queries.selectPendingBlobs(limit.toLong()).executeAsList().map { it.toBlobRecord() }
    }

    override fun updateStatus(blobId: String, status: BlobStatus, remoteKey: String?, localPath: String?): BlobRecord? {
        queries.updateBlobStatus(status.name, remoteKey, localPath, blobId)
        return find(blobId)
    }

    override fun pendingBlobCount(): Long = queries.pendingBlobCount().executeAsOne()

    override fun current(): SyncStatus {
        val pendingEvents = pendingEventCount()
        val pendingBlobs = pendingBlobCount()
        val lastSync = lastSyncAt()
        val lastError = lastErrorMessage()?.ifBlank { null }
        return SyncStatus(
            lastSyncAtEpochMillis = lastSync,
            pendingEvents = pendingEvents,
            pendingBlobs = pendingBlobs,
            lastErrorMessage = lastError,
        )
    }

    private fun com.sencha.sencha.core.data.sync.db.Blobs.toBlobRecord(): BlobRecord {
        return BlobRecord(
            blobId = blob_id,
            chatId = chat_id,
            sha256 = sha256,
            size = size,
            mime = mime,
            localPath = local_path,
            remoteKey = remote_key,
            status = BlobStatus.valueOf(status),
            createdAtEpochMillis = created_at,
        )
    }

    private fun com.sencha.sencha.core.data.sync.db.SelectPendingBlobs.toBlobRecord(): BlobRecord {
        return BlobRecord(
            blobId = blob_id,
            chatId = chat_id,
            sha256 = sha256,
            size = size,
            mime = mime,
            localPath = local_path,
            remoteKey = remote_key,
            status = BlobStatus.valueOf(status),
            createdAtEpochMillis = created_at,
        )
    }

    companion object {
        private const val KEY_LAST_ACK_CURSOR = "last_ack_cursor"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_ERROR_MESSAGE = "last_error_message"
    }
}
