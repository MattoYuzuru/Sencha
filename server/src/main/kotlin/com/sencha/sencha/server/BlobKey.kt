package com.sencha.sencha.server

fun buildBlobKey(
    userId: String,
    chatId: String,
    blobId: String,
    sha256: String,
    extension: String,
): String {
    val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
    return "u/$userId/c/$chatId/b/$blobId-$sha256.$safeExt"
}
