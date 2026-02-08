package com.sencha.sencha.core.jobs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface JobHandle<T> {
    val id: JobId
    val snapshot: StateFlow<JobSnapshot>
    val output: Flow<T>

    fun cancel()

    fun retry()
}

interface JobEngine {
    fun <T> submit(definition: JobDefinition<T>): JobHandle<T>
}
