package com.sencha.sencha.core.jobs

import kotlinx.coroutines.flow.first

suspend fun JobHandle<*>.awaitTerminal(): JobSnapshot {
    return snapshot.first { it.state in setOf(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELED) }
}

suspend fun <T> JobHandle<T>.awaitResult(): T {
    return output.first()
}
