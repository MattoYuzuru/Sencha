package com.sencha.sencha.core.data.sync

import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create

class IosBlobDataSource : BlobDataSource {
    override fun read(path: String): ByteArray {
        val data = NSData.create(contentsOfFile = path) ?: error("Missing file at $path")
        return data.toByteArray()
    }

    override fun write(path: String, data: ByteArray) {
        val nsData = NSData.create(bytes = data, length = data.size.toULong())
        val directory = path.substringBeforeLast('/', "")
        if (directory.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        }
        nsData.writeToFile(path, true)
    }
}
