package com.sencha.sencha.core.jobs

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryJobEngineTest {

    @Test
    fun jobCompletesSuccessfully() = runTest {
        val engine = InMemoryJobEngine(scope = this)
        val job = object : JobDefinition<String> {
            override val id = JobId("job-success")
            override val description = "test job"

            override suspend fun run(context: JobExecutionContext<String>) {
                context.updateProgress(JobProgress(current = 1, total = 2, message = "half"))
                context.emitOutput("hello")
            }
        }

        val handle = engine.submit(job)

        advanceUntilIdle()

        assertEquals(JobState.SUCCEEDED, handle.snapshot.value.state)
    }

    @Test
    fun jobFailsWithMappedError() = runTest {
        val engine = InMemoryJobEngine(scope = this)
        val job = object : JobDefinition<Unit> {
            override val id = JobId("job-fail")
            override val description = "fail job"

            override suspend fun run(context: JobExecutionContext<Unit>) {
                throw JobFailureException(JobError(JobErrorCode.NETWORK, "offline"))
            }
        }

        val handle = engine.submit(job)

        advanceUntilIdle()

        assertEquals(JobState.FAILED, handle.snapshot.value.state)
        assertEquals(JobErrorCode.NETWORK, handle.snapshot.value.error?.code)
    }

    @Test
    fun jobCanBeCanceled() = runTest {
        val engine = InMemoryJobEngine(scope = this)
        val job = object : JobDefinition<Unit> {
            override val id = JobId("job-cancel")
            override val description = "cancel job"

            override suspend fun run(context: JobExecutionContext<Unit>) {
                awaitCancellation()
            }
        }

        val handle = engine.submit(job)
        handle.cancel()

        advanceUntilIdle()

        assertEquals(JobState.CANCELED, handle.snapshot.value.state)
    }

    @Test
    fun retryRestartsFailedJob() = runTest {
        val engine = InMemoryJobEngine(scope = this)
        var attempt = 0
        val job = object : JobDefinition<Unit> {
            override val id = JobId("job-retry")
            override val description = "retry job"

            override suspend fun run(context: JobExecutionContext<Unit>) {
                attempt += 1
                if (attempt == 1) {
                    throw JobFailureException(JobError(JobErrorCode.UNKNOWN, "boom"))
                }
            }
        }

        val handle = engine.submit(job)
        advanceUntilIdle()

        assertEquals(JobState.FAILED, handle.snapshot.value.state)

        handle.retry()
        advanceUntilIdle()

        assertEquals(JobState.SUCCEEDED, handle.snapshot.value.state)
        assertEquals(2, handle.snapshot.value.attempt)
    }
}
