package com.sencha.sencha.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioInputInfo(
    val mimeType: String,
    val sizeBytes: Long,
    val durationMillis: Long? = null,
)

@Serializable
data class SttParams(
    val language: String? = null,
    val diarization: Boolean = false,
    val timestamps: Boolean = false,
)

@Serializable
data class SttCapabilities(
    val inputFormats: Set<String>,
    val maxDurationMillis: Long? = null,
    val maxSizeBytes: Long? = null,
    val languages: Set<String>? = null,
    val supportsDiarization: Boolean = false,
    val supportsTimestamps: Boolean = false,
) {
    fun validate(input: AudioInputInfo, params: SttParams): List<SttValidationError> {
        val errors = mutableListOf<SttValidationError>()
        if (!inputFormats.contains(input.mimeType)) {
            errors.add(
                SttValidationError(
                    code = SttValidationErrorCode.UNSUPPORTED_FORMAT,
                    detail = input.mimeType,
                )
            )
        }
        val maxSize = maxSizeBytes
        if (maxSize != null && input.sizeBytes > maxSize) {
            errors.add(
                SttValidationError(
                    code = SttValidationErrorCode.TOO_LARGE,
                    detail = "${input.sizeBytes}>$maxSize",
                )
            )
        }
        val duration = input.durationMillis
        val maxDuration = maxDurationMillis
        if (duration != null && maxDuration != null && duration > maxDuration) {
            errors.add(
                SttValidationError(
                    code = SttValidationErrorCode.TOO_LONG,
                    detail = "${duration}>$maxDuration",
                )
            )
        }
        val language = params.language
        if (language != null && languages != null && language !in languages) {
            errors.add(
                SttValidationError(
                    code = SttValidationErrorCode.LANGUAGE_UNSUPPORTED,
                    detail = language,
                )
            )
        }
        if (params.diarization && !supportsDiarization) {
            errors.add(SttValidationError(SttValidationErrorCode.DIARIZATION_UNSUPPORTED))
        }
        if (params.timestamps && !supportsTimestamps) {
            errors.add(SttValidationError(SttValidationErrorCode.TIMESTAMPS_UNSUPPORTED))
        }
        return errors
    }
}

@Serializable
enum class SttValidationErrorCode {
    UNSUPPORTED_FORMAT,
    TOO_LARGE,
    TOO_LONG,
    LANGUAGE_UNSUPPORTED,
    DIARIZATION_UNSUPPORTED,
    TIMESTAMPS_UNSUPPORTED,
}

@Serializable
data class SttValidationError(
    val code: SttValidationErrorCode,
    val detail: String? = null,
)

@Serializable
data class TtsVoice(
    val id: String,
    val displayName: String,
)

@Serializable
data class TtsParams(
    val voiceId: String,
    val format: String,
    val sampleRateHz: Int? = null,
)

@Serializable
data class TtsCapabilities(
    val voices: List<TtsVoice>,
    val outputFormats: Set<String>,
    val sampleRatesHz: Set<Int>? = null,
    val maxChars: Int? = null,
) {
    fun validate(text: String, params: TtsParams): List<TtsValidationError> {
        val errors = mutableListOf<TtsValidationError>()
        if (text.isBlank()) {
            errors.add(TtsValidationError(TtsValidationErrorCode.TEXT_EMPTY))
        }
        val max = maxChars
        if (max != null && text.length > max) {
            errors.add(
                TtsValidationError(
                    code = TtsValidationErrorCode.TEXT_TOO_LONG,
                    detail = "${text.length}>$max",
                )
            )
        }
        if (voices.none { it.id == params.voiceId }) {
            errors.add(
                TtsValidationError(
                    code = TtsValidationErrorCode.VOICE_UNSUPPORTED,
                    detail = params.voiceId,
                )
            )
        }
        if (!outputFormats.contains(params.format)) {
            errors.add(
                TtsValidationError(
                    code = TtsValidationErrorCode.UNSUPPORTED_FORMAT,
                    detail = params.format,
                )
            )
        }
        val sampleRate = params.sampleRateHz
        if (sampleRate != null && sampleRatesHz != null && sampleRate !in sampleRatesHz) {
            errors.add(
                TtsValidationError(
                    code = TtsValidationErrorCode.SAMPLE_RATE_UNSUPPORTED,
                    detail = sampleRate.toString(),
                )
            )
        }
        return errors
    }
}

@Serializable
enum class TtsValidationErrorCode {
    TEXT_EMPTY,
    TEXT_TOO_LONG,
    VOICE_UNSUPPORTED,
    UNSUPPORTED_FORMAT,
    SAMPLE_RATE_UNSUPPORTED,
}

@Serializable
data class TtsValidationError(
    val code: TtsValidationErrorCode,
    val detail: String? = null,
)
