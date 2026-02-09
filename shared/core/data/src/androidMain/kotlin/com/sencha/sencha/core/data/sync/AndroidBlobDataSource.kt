package com.sencha.sencha.core.data.sync

import java.io.File

class AndroidBlobDataSource : BlobDataSource {
    override fun read(path: String): ByteArray = File(path).readBytes()

    override fun write(path: String, data: ByteArray) {
        File(path).writeBytes(data)
    }
}
