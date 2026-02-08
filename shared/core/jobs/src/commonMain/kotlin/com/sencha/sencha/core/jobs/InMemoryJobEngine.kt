package com.sencha.sencha.core.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class InMemoryJobEngine(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: Clock = Clock.System,
) : JobEngine {
    private val jobs = mutableMapOf<JobId, InMemoryJobHandle<*>>()

    override fun <T> submit(definition: JobDefinition<T>): JobHandle<T> {
        val existing = jobs[definition.id]
        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            return existing as JobHandle<T>
        }

        val handle = InMemoryJobHandle(definition, scope, clock)
        jobs[definition.id] = handle
        handle.start()
        return handle
    }

    private inner class InMemoryJobHandle<T>(
        private val definition: JobDefinition<T>,
        private val scope: CoroutineScope,
        private val clock: Clock,
    ) : JobHandle<T> {
        override val id: JobId = definition.id
        private val snapshotFlow = MutableStateFlow(initialSnapshot(definition.id))
        private val outputFlow = MutableSharedFlow<T>(extraBufferCapacity = 64)
        private val jobMutex = Mutex()
        private var runningJob = null as kotlinx.coroutines.Job?

        override val snapshot = snapshotFlow.asStateFlow()
        override val output = outputFlow.asSharedFlow()

        fun start() {
            scope.launch {
                jobMutex.withLock {
                    if (runningJob?.isActive == true) return@withLock
                    val attempt = snapshotFlow.value.attempt + 1
                    updateSnapshot(
                        state = JobState.QUEUED,
                        attempt = attempt,
                        progress = null,
                        error = null,
                    )
                    runningJob = scope.launch {
                        val job = currentCoroutineContext().job
                        updateSnapshot(state = JobState.RUNNING)
                        try {
                            val context = InMemoryJobContext(outputFlow, snapshotFlow, clock, job)
                            definition.run(context)
                            updateSnapshot(state = JobState.SUCCEEDED)
                        } catch (cancel: CancellationException) {
                            updateSnapshot(
                                state = JobState.CANCELED,
                                error = JobError(
                                    code = JobErrorCode.CANCELED,
                                    message = "Job was canceled",
                                    cause = cancel.message,
                                ),
                            )
                        } catch (failure: JobFailureException) {
                            updateSnapshot(state = JobState.FAILED, error = failure.error)
                        } catch (throwable: Throwable) {
                            updateSnapshot(
                                state = JobState.FAILED,
                                error = JobError(
                                    code = JobErrorCode.UNKNOWN,
                                    message = throwable.message ?: "Unknown failure",
                                    cause = throwable::class.simpleName,
                                ),
                            )
                        }
                    }
                }
            }
        }

        override fun cancel() {
            runningJob?.cancel()
        }

        override fun retry() {
            val current = snapshotFlow.value
            if (current.state == JobState.RUNNING || current.state == JobState.QUEUED) return
            start()
        }

        private fun initialSnapshot(jobId: JobId): JobSnapshot {
            val now = clock.now()
            return JobSnapshot(
                id = jobId,
                state = JobState.QUEUED,
                createdAt = now,
                updatedAt = now,
            )
        }

        private fun updateSnapshot(
            state: JobState,
            attempt: Int = snapshotFlow.value.attempt,
            progress: JobProgress? = snapshotFlow.value.progress,
            error: JobError? = snapshotFlow.value.error,
        ) {
            val now = clock.now()
            snapshotFlow.value = snapshotFlow.value.copy(
                state = state,
                attempt = attempt,
                progress = progress,
                error = error,
                updatedAt = now,
            )
        }
    }

    private class InMemoryJobContext<T>(
        private val outputFlow: MutableSharedFlow<T>,
        private val snapshotFlow: MutableStateFlow<JobSnapshot>,
        private val clock: Clock,
        private val job: Job,
    ) : JobExecutionContext<T> {
        override val isActive: Boolean
            get() = job.isActive

        override suspend fun emitOutput(value: T) {
            outputFlow.emit(value)
        }

        override suspend fun updateProgress(progress: JobProgress) {
            val now = clock.now()
            snapshotFlow.value = snapshotFlow.value.copy(
                progress = progress,
                updatedAt = now,
            )
        }
    }
}
