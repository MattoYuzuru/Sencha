package com.sencha.sencha.core.data

import com.sencha.sencha.core.data.sync.IosBlobDataSource
import com.sencha.sencha.core.domain.MediaStore
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

class IosMediaStore : MediaStore {
    private val dataSource = IosBlobDataSource()

    override fun read(path: String): ByteArray = dataSource.read(path)

    override fun write(path: String, data: ByteArray) {
        dataSource.write(path, data)
    }

    override fun createBlobPath(blobId: String, extension: String): String {
        val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
        val baseDir = "${NSHomeDirectory()}/Documents/sencha/artifacts/blobs"
        NSFileManager.defaultManager.createDirectoryAtPath(baseDir, true, null, null)
        return "$baseDir/$blobId.$safeExt"
    }
}
