package com.sencha.sencha.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ArtifactSerializationTest {
    @Test
    fun artifactRoundTrip() {
        val artifact = Artifact(
            id = ArtifactId("artifact-1"),
            type = ArtifactType.TEXT,
            origin = ArtifactOrigin(
                jobId = "job-1",
                sourceMessageId = null,
                sourceFileName = "audio.wav",
                modelId = "remote-stt",
            ),
            meta = ArtifactMeta(
                durationMillis = 1200,
                language = "en",
                format = "text/plain",
                sizeBytes = 10,
                sha256 = null,
                createdAtEpochMillis = 123456789,
            ),
            text = "Hello",
            blobId = null,
            sourceBlobId = "blob-1",
            ref = ArtifactRef(localPath = "/tmp/audio.wav", remoteKey = null),
        )

        val json = Json.encodeToString(Artifact.serializer(), artifact)
        val decoded = Json.decodeFromString(Artifact.serializer(), json)

        assertEquals(artifact, decoded)
    }
}
