package com.sencha.sencha.core.model

import kotlin.test.Test
import kotlin.test.assertTrue

class SpeechCapabilitiesTest {
    @Test
    fun sttValidationFlagsUnsupportedInputs() {
        val caps = SttCapabilities(
            inputFormats = setOf("audio/wav"),
            maxDurationMillis = 1000,
            maxSizeBytes = 100,
            languages = setOf("en"),
            supportsDiarization = false,
            supportsTimestamps = false,
        )
        val input = AudioInputInfo(
            mimeType = "audio/mpeg",
            sizeBytes = 200,
            durationMillis = 2000,
        )
        val params = SttParams(
            language = "fr",
            diarization = true,
            timestamps = true,
        )
        val errors = caps.validate(input, params)
        assertTrue(errors.any { it.code == SttValidationErrorCode.UNSUPPORTED_FORMAT })
        assertTrue(errors.any { it.code == SttValidationErrorCode.TOO_LARGE })
        assertTrue(errors.any { it.code == SttValidationErrorCode.TOO_LONG })
        assertTrue(errors.any { it.code == SttValidationErrorCode.LANGUAGE_UNSUPPORTED })
        assertTrue(errors.any { it.code == SttValidationErrorCode.DIARIZATION_UNSUPPORTED })
        assertTrue(errors.any { it.code == SttValidationErrorCode.TIMESTAMPS_UNSUPPORTED })
    }

    @Test
    fun ttsValidationFlagsUnsupportedInputs() {
        val caps = TtsCapabilities(
            voices = listOf(TtsVoice("voice-1", "Voice 1")),
            outputFormats = setOf("audio/m4a"),
            sampleRatesHz = setOf(24000),
            maxChars = 10,
        )
        val params = TtsParams(
            voiceId = "voice-2",
            format = "audio/wav",
            sampleRateHz = 16000,
        )
        val errors = caps.validate("This text is too long", params)
        assertTrue(errors.any { it.code == TtsValidationErrorCode.TEXT_TOO_LONG })
        assertTrue(errors.any { it.code == TtsValidationErrorCode.VOICE_UNSUPPORTED })
        assertTrue(errors.any { it.code == TtsValidationErrorCode.UNSUPPORTED_FORMAT })
        assertTrue(errors.any { it.code == TtsValidationErrorCode.SAMPLE_RATE_UNSUPPORTED })
    }
}
