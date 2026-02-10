package com.sencha.sencha.shared

import com.sencha.sencha.core.data.DefaultModelProviderRegistry
import com.sencha.sencha.core.data.IosMediaStore
import com.sencha.sencha.core.data.RemoteNodeSpeechProvider
import com.sencha.sencha.core.data.SqlArtifactRepository
import com.sencha.sencha.core.data.SqlJobRepository
import com.sencha.sencha.core.data.sync.IosSyncDatabaseDriverFactory
import com.sencha.sencha.core.data.sync.SqlSyncStore
import com.sencha.sencha.core.data.sync.SyncDatabaseFactory
import com.sencha.sencha.core.domain.Artifact
import com.sencha.sencha.core.domain.ArtifactRepository
import com.sencha.sencha.core.domain.AudioInput
import com.sencha.sencha.core.domain.JobRepository
import com.sencha.sencha.core.domain.MediaStore
import com.sencha.sencha.core.domain.ModelEntry
import com.sencha.sencha.core.domain.ModelKey
import com.sencha.sencha.core.domain.ModelProviderRegistry
import com.sencha.sencha.core.domain.SttParams
import com.sencha.sencha.core.domain.SttResult
import com.sencha.sencha.core.domain.SttUseCase
import com.sencha.sencha.core.domain.TextInput
import com.sencha.sencha.core.domain.TtsParams
import com.sencha.sencha.core.domain.TtsResult
import com.sencha.sencha.core.domain.TtsUseCase
import com.sencha.sencha.core.jobs.InMemoryJobEngine
import com.sencha.sencha.core.jobs.JobHandle
import com.sencha.sencha.core.jobs.awaitResult
import com.sencha.sencha.core.jobs.awaitTerminal
import com.sencha.sencha.core.security.IosDeviceIdentityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class IosSpeechSession(
    var nodeAddress: String = "",
    private val isDebug: Boolean = false,
) {
    private val scope = MainScope()
    private val jobEngine = InMemoryJobEngine(scope = scope)
    private val mediaStore: MediaStore = IosMediaStore()
    private val deviceIdentity = IosDeviceIdentityProvider().loadOrCreate()
    private val syncDatabase = SyncDatabaseFactory(IosSyncDatabaseDriverFactory()).create()
    private val syncStore = SqlSyncStore(syncDatabase)
    private val jobs: JobRepository = SqlJobRepository(syncDatabase)
    private val artifacts: ArtifactRepository = SqlArtifactRepository(
        database = syncDatabase,
        eventStore = syncStore,
        blobStore = syncStore,
        deviceId = deviceIdentity.deviceId.value,
        clock = Clock.System,
        onLocalEventAppended = null,
    )
    private val speechProvider = RemoteNodeSpeechProvider(
        baseUrlProvider = { nodeAddress },
        mediaStore = mediaStore,
        isDebug = isDebug,
        allowlistedHosts = setOf("localhost", "127.0.0.1"),
    )
    private val registry: ModelProviderRegistry = DefaultModelProviderRegistry(
        listOf(speechProvider)
    )
    private val sttEntry: ModelEntry
    private val ttsEntry: ModelEntry
    private val sttUseCase = SttUseCase(
        artifacts = artifacts,
        jobs = jobs,
        providers = registry,
        jobEngine = jobEngine,
        mediaStore = mediaStore,
        scope = scope,
    )
    private val ttsUseCase = TtsUseCase(
        artifacts = artifacts,
        jobs = jobs,
        providers = registry,
        jobEngine = jobEngine,
        mediaStore = mediaStore,
        scope = scope,
    )

    init {
        val sttDescriptor = speechProvider.sttModelDescriptor()
        val ttsDescriptor = speechProvider.ttsModelDescriptor()
        sttEntry = ModelEntry(ModelKey(speechProvider.info.id, sttDescriptor.id), sttDescriptor)
        ttsEntry = ModelEntry(ModelKey(speechProvider.info.id, ttsDescriptor.id), ttsDescriptor)
    }

    fun sttModel(): ModelEntry = sttEntry

    fun ttsModel(): ModelEntry = ttsEntry

    fun artifactsSnapshot(): List<Artifact> = artifacts.snapshot()

    fun audioPathForJob(jobId: String): String? {
        return artifacts.snapshot()
            .firstOrNull { it.origin.jobId == jobId && it.ref?.localPath != null }
            ?.ref
            ?.localPath
    }

    fun startStt(input: AudioInput, params: SttParams): JobHandle<SttResult> {
        return sttUseCase.transcribe(sttEntry, input, params)
    }

    fun startTts(input: TextInput, params: TtsParams): JobHandle<TtsResult> {
        return ttsUseCase.synthesize(ttsEntry, input, params)
    }

    fun refreshArtifacts() {
        artifacts.refreshFromStore()
    }

    fun observeSttResult(
        handle: JobHandle<SttResult>,
        onComplete: (SttResult?, String?) -> Unit,
    ) {
        scope.launch {
            val snapshot = handle.awaitTerminal()
            val result = if (snapshot.state == com.sencha.sencha.core.jobs.JobState.SUCCEEDED) {
                handle.awaitResult()
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                onComplete(result, snapshot.error?.message)
            }
        }
    }

    fun observeTtsResult(
        handle: JobHandle<TtsResult>,
        onComplete: (TtsResult?, String?) -> Unit,
    ) {
        scope.launch {
            val snapshot = handle.awaitTerminal()
            val result = if (snapshot.state == com.sencha.sencha.core.jobs.JobState.SUCCEEDED) {
                handle.awaitResult()
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                onComplete(result, snapshot.error?.message)
            }
        }
    }
}
