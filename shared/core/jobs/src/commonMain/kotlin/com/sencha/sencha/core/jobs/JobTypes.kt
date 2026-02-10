package com.sencha.sencha.core.jobs

import kotlin.time.Instant

@JvmInline
value class JobId(val value: String)

enum class JobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

data class JobProgress(
    val current: Long? = null,
    val total: Long? = null,
    val message: String? = null,
) {
    val fraction: Double?
        get() = if (current != null && total != null && total > 0) current.toDouble() / total.toDouble() else null
}

enum class JobErrorCode {
    NETWORK,
    OFFLINE,
    OUT_OF_MEMORY,
    TIMEOUT,
    VALIDATION,
    UNSUPPORTED_FORMAT,
    TOO_LARGE,
    REMOTE_ERROR,
    CANCELED,
    UNKNOWN,
}

data class JobError(
    val code: JobErrorCode,
    val message: String,
    val cause: String? = null,
)

data class JobSnapshot(
    val id: JobId,
    val state: JobState,
    val progress: JobProgress? = null,
    val error: JobError? = null,
    val attempt: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

sealed interface JobEvent<out T> {
    data class Progress(val progress: JobProgress) : JobEvent<Nothing>
    data class Output<T>(val value: T) : JobEvent<T>
}

class JobFailureException(val error: JobError) : Exception(error.message)

interface JobExecutionContext<T> {
    val isActive: Boolean

    suspend fun emitOutput(value: T)

    suspend fun updateProgress(progress: JobProgress)
}

interface JobDefinition<T> {
    val id: JobId
    val description: String?

    suspend fun run(context: JobExecutionContext<T>)
}
