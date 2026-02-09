package com.sencha.sencha.server

import java.security.MessageDigest

fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

fun hexToBytes(hex: String): ByteArray {
    val cleaned = hex.trim().lowercase()
    require(cleaned.length % 2 == 0) { "Invalid hex length" }
    val result = ByteArray(cleaned.length / 2)
    for (index in result.indices) {
        val byte = cleaned.substring(index * 2, index * 2 + 2).toInt(16)
        result[index] = byte.toByte()
    }
    return result
}
