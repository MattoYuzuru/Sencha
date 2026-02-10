package com.sencha.sencha.core.domain

interface MediaStore {
    fun read(path: String): ByteArray

    fun write(path: String, data: ByteArray)

    fun createBlobPath(blobId: String, extension: String): String
}
