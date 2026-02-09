package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.BlobRecord
import com.sencha.sencha.core.domain.sync.BlobStatus
import com.sencha.sencha.core.domain.sync.SyncEvent
import com.sencha.sencha.core.domain.sync.SyncStatus

interface EventStore {
    fun appendLocal(event: SyncEvent)
    fun mergeRemote(events: List<SyncEvent>): MergeResult
    fun pendingEvents(limit: Int): List<SyncEvent>
    fun markUploaded(eventIds: List<String>)
    fun allEvents(): List<SyncEvent>
    fun pendingEventCount(): Long
    fun lastAckCursor(): Long?
    fun updateLastAckCursor(cursor: Long)
    fun lastSyncAt(): Long?
    fun updateLastSyncAt(epochMillis: Long)
    fun lastErrorMessage(): String?
    fun updateLastErrorMessage(message: String?)
}

data class MergeResult(
    val inserted: Int,
    val ignored: Int,
)

interface BlobStore {
    fun upsert(blob: BlobRecord)
    fun find(blobId: String): BlobRecord?
    fun pendingBlobs(limit: Int): List<BlobRecord>
    fun updateStatus(blobId: String, status: BlobStatus, remoteKey: String?, localPath: String?): BlobRecord?
    fun pendingBlobCount(): Long
}

interface SyncStatusStore {
    fun current(): SyncStatus
}
