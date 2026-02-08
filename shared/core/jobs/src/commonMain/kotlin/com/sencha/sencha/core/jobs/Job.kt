package com.sencha.sencha.core.jobs

data class JobId(val value: String)

enum class JobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

data class JobStatus(
    val id: JobId,
    val state: JobState,
)

interface Job {
    val id: JobId
    val state: JobState
}

interface JobEngine {
    fun submit(job: Job): JobStatus
}
