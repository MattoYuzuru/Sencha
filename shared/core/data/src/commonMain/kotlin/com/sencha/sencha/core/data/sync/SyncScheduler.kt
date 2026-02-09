package com.sencha.sencha.core.data.sync

import kotlin.time.Clock


data class SyncFlushPolicy(
    val eventThreshold: Int = 20,
    val flushIntervalMillis: Long = 5_000,
)

class SyncScheduler(
    private val syncEngine: SyncEngine,
    private val eventStore: EventStore,
    private val clock: Clock = Clock.System,
    private val policy: SyncFlushPolicy = SyncFlushPolicy(),
) {
    private var lastFlushAt: Long = 0

    fun onLocalEventAppended() {
        maybeFlush()
    }

    fun maybeFlush() {
        val pending = eventStore.pendingEventCount()
        val now = clock.now().toEpochMilliseconds()
        if (pending >= policy.eventThreshold || (now - lastFlushAt) >= policy.flushIntervalMillis) {
            syncEngine.enqueueUploadEvents()
            lastFlushAt = now
        }
    }
}
