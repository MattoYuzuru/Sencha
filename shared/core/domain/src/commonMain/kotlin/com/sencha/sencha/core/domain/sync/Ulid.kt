package com.sencha.sencha.core.domain.sync

import kotlin.random.Random
import kotlin.time.Clock

object UlidGenerator {
    private const val ENCODED_LENGTH = 26
    private const val TIME_LENGTH = 10
    private const val RANDOM_LENGTH = 16
    private val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()

    fun newUlid(clock: Clock = Clock.System): String {
        val time = clock.now().toEpochMilliseconds()
        val random = ByteArray(10)
        Random.Default.nextBytes(random)
        val chars = CharArray(ENCODED_LENGTH)
        encodeTime(time, chars)
        encodeRandom(random, chars)
        return String(chars)
    }

    private fun encodeTime(time: Long, chars: CharArray) {
        var value = time
        for (index in TIME_LENGTH - 1 downTo 0) {
            chars[index] = ENCODING[(value and 31).toInt()]
            value = value ushr 5
        }
    }

    private fun encodeRandom(random: ByteArray, chars: CharArray) {
        var buffer = 0
        var bitsLeft = 0
        var outputIndex = TIME_LENGTH
        for (byte in random) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5 && outputIndex < TIME_LENGTH + RANDOM_LENGTH) {
                val index = (buffer shr (bitsLeft - 5)) and 31
                chars[outputIndex++] = ENCODING[index]
                bitsLeft -= 5
            }
        }
        while (outputIndex < TIME_LENGTH + RANDOM_LENGTH) {
            val index = (buffer shl (5 - bitsLeft)) and 31
            chars[outputIndex++] = ENCODING[index]
            bitsLeft = 0
        }
    }
}
