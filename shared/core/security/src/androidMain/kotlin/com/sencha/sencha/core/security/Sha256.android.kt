package com.sencha.sencha.core.security

import java.security.MessageDigest

internal actual object Sha256 {
    actual fun digest(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}
