package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.sync.BlobRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class KtorBlobTransfer(
    private val client: HttpClient,
    private val dataSource: BlobDataSource,
) : BlobTransfer {
    override suspend fun upload(presign: PresignResponse, blob: BlobRecord) {
        val path = blob.localPath ?: error("Missing local path for blob ${blob.blobId}")
        val bytes = dataSource.read(path)
        client.request(presign.url) {
            method = HttpMethod.parse(presign.method)
            headers {
                presign.headers.forEach { (key, value) -> append(key, value) }
                append(HttpHeaders.ContentLength, bytes.size.toString())
            }
            contentType(ContentType.parse(blob.mime))
            setBody(bytes)
        }
    }
}
