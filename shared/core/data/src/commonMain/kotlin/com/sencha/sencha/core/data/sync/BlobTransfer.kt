package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.BlobRecord

interface BlobDataSource {
    fun read(path: String): ByteArray
    fun write(path: String, data: ByteArray)
}

interface BlobTransfer {
    suspend fun upload(presign: PresignResponse, blob: BlobRecord)

    suspend fun download(presign: PresignResponse): ByteArray
}
