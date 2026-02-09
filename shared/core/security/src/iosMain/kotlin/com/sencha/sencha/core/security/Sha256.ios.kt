package com.sencha.sencha.core.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CC_SHA256
import platform.CommonCrypto.CC_SHA256_DIGEST_LENGTH

internal actual object Sha256 {
    actual fun digest(data: ByteArray): ByteArray {
        val output = ByteArray(CC_SHA256_DIGEST_LENGTH)
        data.usePinned { inputPinned ->
            output.usePinned { outputPinned ->
                CC_SHA256(
                    inputPinned.addressOf(0),
                    data.size.convert(),
                    outputPinned.addressOf(0),
                )
            }
        }
        return output
    }
}
