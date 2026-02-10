package com.sencha.sencha.core.domain

import com.sencha.sencha.core.jobs.InMemoryJobEngine
import com.sencha.sencha.core.jobs.JobDefinition
import com.sencha.sencha.core.jobs.JobExecutionContext
import com.sencha.sencha.core.jobs.JobHandle
import com.sencha.sencha.core.jobs.JobId
import com.sencha.sencha.core.jobs.JobState
import com.sencha.sencha.core.jobs.awaitTerminal
import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId
import com.sencha.sencha.core.model.ModelResourceProfile
import com.sencha.sencha.core.model.SttCapabilities
import com.sencha.sencha.core.model.SttParams
import com.sencha.sencha.core.model.TtsCapabilities
import com.sencha.sencha.core.model.TtsParams
import com.sencha.sencha.core.model.TtsVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpeechUseCaseTest {
    @Test
    fun sttCreatesArtifactAndUpdatesJob() = runTest {
        val artifacts = FakeArtifactRepository()
        val jobs = FakeJobRepository()
        val mediaStore = FakeMediaStore(mapOf("/tmp/audio.wav" to "hi".encodeToByteArray()))
        val provider = FakeSpeechProvider()
        val registry = DefaultModelProviderRegistry(listOf(provider))
        val jobEngine = InMemoryJobEngine(scope = this)
        val entry = ModelEntry(
            key = ModelKey(provider.info.id, ModelId("stt")),
            descriptor = ModelDescriptor(
                id = ModelId("stt"),
                displayName = "STT",
                capabilities = setOf(ModelCapability.STT),
                resources = ModelResourceProfile(requiresNetwork = false),
                sttCapabilities = SttCapabilities(inputFormats = setOf("audio/wav")),
            ),
        )
        val useCase = SttUseCase(
            artifacts = artifacts,
            jobs = jobs,
            providers = registry,
            jobEngine = jobEngine,
            mediaStore = mediaStore,
            scope = this,
        )

        val handle = useCase.transcribe(
            entry = entry,
            input = AudioInput(
                localPath = "/tmp/audio.wav",
                mimeType = "audio/wav",
                sizeBytes = 2,
            ),
            params = SttParams(),
        )

        handle.awaitTerminal()

        val artifact = artifacts.snapshot().firstOrNull()
        assertNotNull(artifact)
        assertEquals(ArtifactType.TEXT, artifact.type)
        assertEquals(JobState.SUCCEEDED, jobs.list().firstOrNull()?.state)
    }

    @Test
    fun ttsCreatesAudioArtifactAndWritesFile() = runTest {
        val artifacts = FakeArtifactRepository()
        val jobs = FakeJobRepository()
        val mediaStore = FakeMediaStore()
        val provider = FakeSpeechProvider()
        val registry = DefaultModelProviderRegistry(listOf(provider))
        val jobEngine = InMemoryJobEngine(scope = this)
        val entry = ModelEntry(
            key = ModelKey(provider.info.id, ModelId("tts")),
            descriptor = ModelDescriptor(
                id = ModelId("tts"),
                displayName = "TTS",
                capabilities = setOf(ModelCapability.TTS),
                resources = ModelResourceProfile(requiresNetwork = false),
                ttsCapabilities = TtsCapabilities(
                    voices = listOf(TtsVoice("default", "Default")),
                    outputFormats = setOf("audio/m4a"),
                ),
            ),
        )
        val useCase = TtsUseCase(
            artifacts = artifacts,
            jobs = jobs,
            providers = registry,
            jobEngine = jobEngine,
            mediaStore = mediaStore,
            scope = this,
        )

        val handle = useCase.synthesize(
            entry = entry,
            input = TextInput("Hello"),
            params = TtsParams(voiceId = "default", format = "audio/m4a"),
        )

        handle.awaitTerminal()

        val artifact = artifacts.snapshot().firstOrNull()
        assertNotNull(artifact)
        assertEquals(ArtifactType.AUDIO, artifact.type)
        assertTrue(mediaStore.writtenPaths.isNotEmpty())
        assertEquals(JobState.SUCCEEDED, jobs.list().firstOrNull()?.state)
    }
}

private class FakeArtifactRepository : ArtifactRepository {
    private val items = mutableListOf<Artifact>()
    private val flow = MutableStateFlow<List<Artifact>>(emptyList())
    private var counter = 0

    override fun artifacts(): StateFlow<List<Artifact>> = flow.asStateFlow()

    override fun find(id: ArtifactId): Artifact? = items.firstOrNull { it.id == id }

    override fun snapshot(): List<Artifact> = items.toList()

    override fun createTextArtifact(request: CreateTextArtifactRequest): Artifact {
        val artifact = Artifact(
            id = ArtifactId("artifact-${counter++}"),
            type = ArtifactType.TEXT,
            origin = request.origin,
            meta = request.meta,
            text = request.text,
            sourceBlobId = request.sourceAudio?.blobId,
        )
        items.add(artifact)
        flow.value = items.toList()
        return artifact
    }

    override fun createAudioArtifact(request: CreateAudioArtifactRequest): Artifact {
        val artifact = Artifact(
            id = ArtifactId("artifact-${counter++}"),
            type = ArtifactType.AUDIO,
            origin = request.origin,
            meta = request.meta,
            blobId = request.blob.blobId,
            ref = ArtifactRef(localPath = request.blob.localPath),
        )
        items.add(artifact)
        flow.value = items.toList()
        return artifact
    }

    override fun refreshFromStore() = Unit
}

private class FakeJobRepository : JobRepository {
    private val jobs = mutableListOf<JobRecord>()

    override fun upsert(record: JobRecord) {
        jobs.removeAll { it.id == record.id }
        jobs.add(record)
    }

    override fun find(jobId: String): JobRecord? = jobs.firstOrNull { it.id == jobId }

    override fun list(): List<JobRecord> = jobs.toList()
}

private class FakeMediaStore(
    initial: Map<String, ByteArray> = emptyMap(),
) : MediaStore {
    private val data = initial.toMutableMap()
    val writtenPaths = mutableListOf<String>()

    override fun read(path: String): ByteArray = data[path] ?: ByteArray(0)

    override fun write(path: String, data: ByteArray) {
        this.data[path] = data
        writtenPaths.add(path)
    }

    override fun createBlobPath(blobId: String, extension: String): String {
        return "/tmp/$blobId.$extension"
    }
}

private class FakeSpeechProvider : ModelProvider {
    override val info = ModelProviderInfo(
        id = ModelProviderId("fake"),
        displayName = "Fake",
        kind = ModelProviderKind.LOCAL,
    )

    override suspend fun listModels(): Result<List<ModelDescriptor>> = Result.success(emptyList())

    override fun createChatJob(request: ChatRequest): JobDefinition<ChatDelta> {
        error("Not used")
    }

    override fun createSttJob(request: SttRequest): JobDefinition<SttResult> {
        return object : JobDefinition<SttResult> {
            override val id = request.jobId
            override val description = "stt"

            override suspend fun run(context: JobExecutionContext<SttResult>) {
                context.emitOutput(SttResult(text = "hello", language = "en"))
            }
        }
    }

    override fun createTtsJob(request: TtsRequest): JobDefinition<TtsResult> {
        return object : JobDefinition<TtsResult> {
            override val id = request.jobId
            override val description = "tts"

            override suspend fun run(context: JobExecutionContext<TtsResult>) {
                context.emitOutput(
                    TtsResult(
                        audioBytes = "audio".encodeToByteArray(),
                        mimeType = "audio/m4a",
                    )
                )
            }
        }
    }
}
