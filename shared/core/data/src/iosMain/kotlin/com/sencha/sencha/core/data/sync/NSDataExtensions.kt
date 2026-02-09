package com.sencha.sencha.core.data.sync

import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.Foundation.NSData

internal fun NSData.toByteArray(): ByteArray {
    val length = CFDataGetLength(this)
    val buffer = ByteArray(length.toInt())
    val bytes = CFDataGetBytePtr(this)
    if (bytes != null) {
        for (index in 0 until buffer.size) {
            buffer[index] = bytes[index]
        }
    }
    return buffer
}
