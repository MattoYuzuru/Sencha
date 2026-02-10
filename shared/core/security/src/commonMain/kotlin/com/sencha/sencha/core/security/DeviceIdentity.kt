package com.sencha.sencha.core.security

import kotlin.jvm.JvmInline

@JvmInline
value class DeviceId(val value: String)

data class DeviceIdentity(
    val deviceId: DeviceId,
    val publicKey: ByteArray,
)

interface DeviceIdentityProvider {
    fun loadOrCreate(): DeviceIdentity
}

internal fun deviceIdFromPublicKey(publicKey: ByteArray): DeviceId {
    val digest = Sha256.digest(publicKey)
    return DeviceId(digest.toHexString())
}

object Hashing {
    fun sha256(data: ByteArray): ByteArray = Sha256.digest(data)

    fun sha256Hex(data: ByteArray): String = Sha256.digest(data).toHexString()
}

fun ByteArray.toHexString(): String {
    val result = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        val high = value ushr 4
        val low = value and 0x0F
        result.append("0123456789abcdef"[high])
        result.append("0123456789abcdef"[low])
    }
    return result.toString()
}

internal expect object Sha256 {
    fun digest(data: ByteArray): ByteArray
}
