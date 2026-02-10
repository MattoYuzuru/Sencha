package com.sencha.sencha.core.domain

import com.sencha.sencha.core.jobs.JobId
import com.sencha.sencha.core.jobs.JobProgress
import com.sencha.sencha.core.model.SttParams
import com.sencha.sencha.core.model.TtsParams

data class AudioInput(
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMillis: Long? = null,
    val fileName: String? = null,
    val data: ByteArray? = null,
)

data class TextInput(
    val text: String,
)

data class SttSegment(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)

data class SttChunk(
    val text: String,
    val isFinal: Boolean = false,
    val segments: List<SttSegment> = emptyList(),
)

data class SttResult(
    val text: String,
    val segments: List<SttSegment> = emptyList(),
    val language: String? = null,
)

data class TtsResult(
    val audioBytes: ByteArray,
    val mimeType: String,
    val sampleRateHz: Int? = null,
    val durationMillis: Long? = null,
)

data class SttRequest(
    val jobId: JobId,
    val modelKey: ModelKey,
    val input: AudioInput,
    val params: SttParams,
)

data class TtsRequest(
    val jobId: JobId,
    val modelKey: ModelKey,
    val input: TextInput,
    val params: TtsParams,
)

interface SttEngine {
    suspend fun transcribe(
        input: AudioInput,
        params: SttParams,
        onProgress: (JobProgress) -> Unit,
        onChunk: (SttChunk) -> Unit = {},
    ): SttResult
}

interface TtsEngine {
    suspend fun synthesize(
        input: TextInput,
        params: TtsParams,
        onProgress: (JobProgress) -> Unit,
    ): TtsResult
}
