package com.sencha.sencha.core.domain

import com.sencha.sencha.core.jobs.JobErrorCode
import com.sencha.sencha.core.jobs.JobState

enum class JobType {
    STT_TRANSCRIBE,
    TTS_SYNTHESIZE,
}

data class JobRecord(
    val id: String,
    val type: JobType,
    val state: JobState,
    val modelKey: ModelKey,
    val payloadJson: String,
    val errorCode: JobErrorCode? = null,
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
