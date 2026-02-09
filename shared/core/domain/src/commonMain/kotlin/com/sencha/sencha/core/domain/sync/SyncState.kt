package com.sencha.sencha.core.domain.sync

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SyncCursor(val value: Long)

@Serializable
data class SyncStatus(
    val lastSyncAtEpochMillis: Long?,
    val pendingEvents: Long,
    val pendingBlobs: Long,
    val lastErrorMessage: String?,
)
