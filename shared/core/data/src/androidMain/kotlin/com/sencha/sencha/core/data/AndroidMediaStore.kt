package com.sencha.sencha.core.data

import android.content.Context
import com.sencha.sencha.core.domain.MediaStore
import java.io.File

class AndroidMediaStore(
    private val context: Context,
) : MediaStore {
    override fun read(path: String): ByteArray = File(path).readBytes()

    override fun write(path: String, data: ByteArray) {
        File(path).writeBytes(data)
    }

    override fun createBlobPath(blobId: String, extension: String): String {
        val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
        val dir = File(context.filesDir, "artifacts/blobs")
        dir.mkdirs()
        return File(dir, "$blobId.$safeExt").absolutePath
    }
}
