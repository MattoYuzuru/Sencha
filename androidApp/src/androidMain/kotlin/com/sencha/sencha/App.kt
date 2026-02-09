@file:Suppress("MagicNumber")

package com.sencha.sencha

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sencha.sencha.core.data.*
import com.sencha.sencha.core.data.sync.*
import com.sencha.sencha.core.domain.*
import com.sencha.sencha.core.domain.sync.NodeInfo
import com.sencha.sencha.core.domain.sync.SyncStatus
import com.sencha.sencha.core.jobs.*
import com.sencha.sencha.core.model.*
import com.sencha.sencha.core.security.AndroidDeviceIdentityProvider
import com.sencha.sencha.core.security.AndroidKeyStore
import com.sencha.sencha.core.security.toHexString
import com.sencha.sencha.local.AndroidDeviceProfileProvider
import com.sencha.sencha.local.AndroidLlamaInferenceEngine
import com.sencha.sencha.local.AndroidLocalModelStore
import com.sencha.sencha.local.AndroidModelInstaller
import com.sencha.sencha.local.toDisplayMessage
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlin.time.Clock

private val SenchaGreen = Color(0xFF7DBE9F)
private val SenchaLeaf = Color(0xFF5C8F79)
private val SenchaMist = Color(0xFFE8F2ED)
private val SenchaClay = Color(0xFFF6F3EE)

@Composable
@Preview
fun App() {
    SenchaTheme {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val state = remember { AppState(scope, context) }
        val modelState by state.modelCatalogState.collectAsState()
        val chats by state.chatRepository.chats().collectAsState()
        val activeGeneration by state.activeGenerationState.collectAsState()
        val sessionInfo by state.sessionInfo.collectAsState()
        var currentScreen by remember { mutableStateOf<Screen>(Screen.ChatList) }

        LaunchedEffect(Unit) {
            state.refreshModels()
        }

        LaunchedEffect(sessionInfo) {
            if (sessionInfo != null) {
                state.syncNow()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = SenchaBackground),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GlassTopBar(title = currentScreen.title)

                Box(modifier = Modifier.weight(1f)) {
                    when (val screen = currentScreen) {
                        Screen.ChatList -> {
                            ChatListScreen(
                                chats = chats,
                                selectedModel = state.selectedModel.value,
                                onNewChat = { title ->
                                    val chat = state.createChat(title)
                                    currentScreen = Screen.ChatDetail(chat.id)
                                },
                                onChatSelected = { chat ->
                                    currentScreen = Screen.ChatDetail(chat.id)
                                },
                            )
                        }
                        is Screen.ChatDetail -> {
                            val chatId = screen.chatId
                            val messages by state.chatRepository.messages(chatId).collectAsState()
                            val isGeneratingHere = activeGeneration?.chatId == chatId
                            ChatScreen(
                                chat = state.chatRepository.findChat(chatId),
                                messages = messages,
                                isBusy = activeGeneration != null,
                                isGeneratingHere = isGeneratingHere,
                                onBack = { currentScreen = Screen.ChatList },
                                onSend = { text -> state.sendMessage(chatId, text) },
                                onStop = { state.stopGeneration() },
                            )
                        }
                        Screen.Models -> {
                            ModelsScreen(
                                state = modelState,
                                selectedModel = state.selectedModel.value,
                                onImport = { uri -> state.startImportJob(uri) },
                                onDownload = { spec -> state.startDownloadJob(spec) },
                                onSelect = { entry -> state.selectedModel.value = entry },
                                onRefresh = { state.refreshModels() },
                            )
                        }
                        Screen.Connections -> {
                            val syncStatus by state.syncStatus.collectAsState()
                            val nodes by state.nodes.collectAsState()
                            val nodesLoading by state.nodesLoading.collectAsState()
                            val nodesError by state.nodesError.collectAsState()
                            val nodeTests by state.nodeTests.collectAsState()
                            val sessionInfo by state.sessionInfo.collectAsState()
                            val syncError by state.syncError.collectAsState()
                            ConnectionsScreen(
                                baseUrl = state.baseUrl.value,
                                onBaseUrlChange = { state.baseUrl.value = it },
                                isConnected = sessionInfo != null,
                                syncStatus = syncStatus,
                                syncError = syncError,
                                onRegister = { code, deviceName -> state.registerDevice(code, deviceName) },
                                onSyncNow = { state.syncNow() },
                                nodes = nodes,
                                nodesLoading = nodesLoading,
                                nodesError = nodesError,
                                nodeTests = nodeTests,
                                onRefreshNodes = { state.refreshNodes() },
                                onAddNode = { name, address -> state.addNode(name, address) },
                                onTestNode = { address -> state.testNode(address) },
                            )
                        }
                    }
                }

                GlassBottomBar(
                    current = currentScreen,
                    onNavigate = { screen -> currentScreen = screen },
                )
            }
        }
    }
}

private sealed class Screen(val title: String) {
    data object ChatList : Screen("Чаты")
    data object Models : Screen("Модели")
    data object Connections : Screen("Связи")
    data class ChatDetail(val chatId: ChatId) : Screen("Чат")
}

private data class ActiveGeneration(
    val chatId: ChatId,
    val handle: JobHandle<ChatDelta>,
)

private class AppState(private val scope: CoroutineScope, context: android.content.Context) {
    private val jobEngine = InMemoryJobEngine(scope = scope)
    private val httpClient = HttpClientFactory.create()
    private val syncStore = SqlSyncStore(
        SyncDatabaseFactory(AndroidSyncDatabaseDriverFactory(context)).create()
    )
    private val keyStore = AndroidKeyStore(context)
    private val sessionStore = KeyStoreSessionStore(keyStore)
    val sessionInfo = MutableStateFlow(sessionStore.load())
    val baseUrl = MutableStateFlow("")
    val syncStatus = MutableStateFlow(syncStore.current())
    val syncError = MutableStateFlow<String?>(null)
    val nodes = MutableStateFlow<List<NodeInfo>>(emptyList())
    val nodesLoading = MutableStateFlow(false)
    val nodesError = MutableStateFlow<String?>(null)
    val nodeTests = MutableStateFlow<Map<String, String>>(emptyMap())
    private val deviceIdentity = AndroidDeviceIdentityProvider().loadOrCreate()
    private val syncApi = DynamicSyncApi({ baseUrl.value }, httpClient, sessionStore)
    private val blobTransfer = KtorBlobTransfer(httpClient, AndroidBlobDataSource())
    private val syncEngine = SyncEngine(
        eventStore = syncStore,
        blobStore = syncStore,
        api = syncApi,
        blobTransfer = blobTransfer,
        jobEngine = jobEngine,
    )
    private val flushPolicy = SyncFlushPolicy()
    private val syncScheduler = SyncScheduler(syncEngine, syncStore, policy = flushPolicy)
    private val localStore = AndroidLocalModelStore(context)
    val modelInstaller = AndroidModelInstaller(context, localStore)
    private val localProvider = LocalTextLLMProvider(
        store = localStore,
        engine = AndroidLlamaInferenceEngine(context),
    )
    private val registry: ModelProviderRegistry = DefaultModelProviderRegistry(
        listOf(localProvider)
    )

    val modelCatalog = ModelCatalogService(registry)
    val modelCatalogState = modelCatalog.state()
    val chatRepository = EventBackedChatRepository(
        eventStore = syncStore,
        deviceId = deviceIdentity.deviceId.value,
        onLocalEventAppended = { syncScheduler.onLocalEventAppended() },
    )
    val selectedModel: MutableState<ModelEntry?> = mutableStateOf(null)
    private val activeGeneration = MutableStateFlow<ActiveGeneration?>(null)
    val activeGenerationState: StateFlow<ActiveGeneration?> = activeGeneration.asStateFlow()
    private val chatUseCase = ChatUseCase(
        repository = chatRepository,
        providers = registry,
        jobEngine = jobEngine,
        scope = scope,
    )

    init {
        scope.launch {
            while (true) {
                delay(flushPolicy.flushIntervalMillis)
                syncScheduler.maybeFlush()
                syncStatus.value = syncStore.current()
            }
        }
    }

    fun createChat(title: String): ChatThread {
        val selected = selectedModel.value ?: modelCatalogState.value.entries.firstOrNull()
            ?: error("No models available")
        selectedModel.value = selected
        return chatUseCase.createChat(title.ifBlank { selected.descriptor.displayName }, selected.key)
    }

    fun sendMessage(chatId: ChatId, text: String) {
        if (text.isBlank()) return
        val current = activeGeneration.value
        if (current?.handle?.snapshot?.value?.state in setOf(JobState.QUEUED, JobState.RUNNING)) {
            return
        }
        val handle = chatUseCase.sendUserMessage(chatId, text)
        activeGeneration.value = ActiveGeneration(chatId, handle)
        scope.launch {
            handle.snapshot.first { it.state in setOf(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELED) }
            if (activeGeneration.value?.handle == handle) {
                activeGeneration.value = null
            }
        }
    }

    fun refreshModels() {
        scope.launch { modelCatalog.refresh() }
    }

    fun stopGeneration() {
        activeGeneration.value?.handle?.cancel()
    }

    fun registerDevice(code: String, deviceName: String?) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            syncError.value = "Введите одноразовый код"
            return
        }
        val validation = SyncEndpointPolicy.validate(
            baseUrl = baseUrl.value,
            isDebug = BuildConfig.DEBUG,
            allowlistedHosts = setOf("10.0.2.2", "localhost"),
        )
        val normalized = validation.getOrElse { error ->
            syncError.value = error.message ?: "Неверный адрес сервера"
            return
        }
        baseUrl.value = normalized
        scope.launch {
            try {
                syncError.value = null
                val response = syncApi.registerDevice(
                    RegisterDeviceRequest(
                        code = trimmed,
                        deviceId = deviceIdentity.deviceId.value,
                        devicePublicKeyHex = deviceIdentity.publicKey.toHexString(),
                        deviceName = deviceName?.ifBlank { null },
                    )
                )
                val session = SessionInfo(
                    userId = response.userId,
                    deviceId = response.deviceId,
                    sessionToken = response.sessionToken,
                )
                sessionStore.save(session)
                sessionInfo.value = session
                syncStatus.value = syncStore.current()
                syncNow()
            } catch (throwable: Throwable) {
                syncError.value = throwable.message ?: "Не удалось подключиться"
            }
        }
    }

    fun syncNow() {
        val uploadHandle = syncEngine.enqueueUploadEvents()
        val downloadHandle = syncEngine.enqueueDownloadEvents()
        val blobHandle = syncEngine.enqueueBlobUploads()
        listOf(uploadHandle, downloadHandle, blobHandle).forEach { handle ->
            scope.launch {
                handle.snapshot.first { it.state in setOf(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELED) }
                if (handle == downloadHandle) {
                    chatRepository.refreshFromStore()
                }
                syncStatus.value = syncStore.current()
            }
        }
    }

    fun refreshNodes() {
        if (baseUrl.value.isBlank()) {
            nodesError.value = "Укажите адрес сервера"
            return
        }
        scope.launch {
            nodesLoading.value = true
            nodesError.value = null
            try {
                nodes.value = syncApi.fetchNodes()
            } catch (throwable: Throwable) {
                nodesError.value = throwable.message ?: "Не удалось загрузить узлы"
            } finally {
                nodesLoading.value = false
            }
        }
    }

    fun addNode(name: String, address: String) {
        if (name.isBlank() || address.isBlank()) {
            nodesError.value = "Заполните имя и адрес узла"
            return
        }
        scope.launch {
            nodesLoading.value = true
            nodesError.value = null
            try {
                syncApi.upsertNode(
                    NodeUpsertRequest(
                        nodeId = null,
                        name = name.trim(),
                        address = address.trim(),
                        lastSeenEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    )
                )
                nodes.value = syncApi.fetchNodes()
            } catch (throwable: Throwable) {
                nodesError.value = throwable.message ?: "Не удалось сохранить узел"
            } finally {
                nodesLoading.value = false
            }
        }
    }

    fun testNode(address: String) {
        val validation = SyncEndpointPolicy.validate(
            baseUrl = address.trim(),
            isDebug = BuildConfig.DEBUG,
            allowlistedHosts = setOf("10.0.2.2", "localhost"),
        )
        val normalized = validation.getOrElse { error ->
            nodeTests.value = nodeTests.value + (address to (error.message ?: "Неверный адрес"))
            return
        }
        scope.launch {
            try {
                val response = httpClient.get("$normalized/v1/health")
                val status = if (response.status.isSuccess()) "OK" else "HTTP ${response.status.value}"
                nodeTests.value = nodeTests.value + (address to status)
            } catch (throwable: Throwable) {
                nodeTests.value = nodeTests.value + (address to (throwable.message ?: "Ошибка"))
            }
        }
    }

    fun startImportJob(uri: String): JobHandle<LocalModelRecord> {
        val job = object : JobDefinition<LocalModelRecord> {
            override val id = JobId("install-import-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}")
            override val description = "Import local model"

            override suspend fun run(context: JobExecutionContext<LocalModelRecord>) {
                val result = modelInstaller.importModel(ModelImportRequest(uri)) { current, total ->
                    context.updateProgress(JobProgress(current = current, total = total, message = "Импорт"))
                }
                val record = result.getOrElse { throw JobFailureException(toInstallError(it)) }
                context.emitOutput(record)
            }
        }
        return jobEngine.submit(job)
    }

    fun startDownloadJob(spec: ModelDownloadSpec): JobHandle<LocalModelRecord> {
        val job = object : JobDefinition<LocalModelRecord> {
            override val id = JobId("install-download-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}")
            override val description = "Download local model"

            override suspend fun run(context: JobExecutionContext<LocalModelRecord>) {
                val result = modelInstaller.downloadModel(ModelDownloadRequest(spec)) { current, total ->
                    context.updateProgress(JobProgress(current = current, total = total, message = "Загрузка"))
                }
                val record = result.getOrElse { throw JobFailureException(toInstallError(it)) }
                context.emitOutput(record)
            }
        }
        return jobEngine.submit(job)
    }

    private fun toInstallError(throwable: Throwable): JobError {
        val message = throwable.message ?: "Не удалось установить модель"
        val code = when {
            message.contains("sha", ignoreCase = true) -> JobErrorCode.VALIDATION
            message.contains("network", ignoreCase = true) -> JobErrorCode.NETWORK
            message.contains("tls", ignoreCase = true) -> JobErrorCode.NETWORK
            message.contains("http", ignoreCase = true) -> JobErrorCode.NETWORK
            throwable is java.io.IOException -> JobErrorCode.NETWORK
            else -> JobErrorCode.UNKNOWN
        }
        return JobError(code = code, message = message, cause = throwable::class.simpleName)
    }
}

@Composable
private fun ChatListScreen(
    chats: List<ChatThread>,
    selectedModel: ModelEntry?,
    onNewChat: (String) -> Unit,
    onChatSelected: (ChatThread) -> Unit,
) {
    var newTitle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Новый чат", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Модель: ${selectedModel?.descriptor?.displayName ?: "Не выбрана"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (selectedModel == null) {
                    Text(
                        "Импортируйте модель, чтобы создать чат.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text("Название чата") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(
                    onClick = {
                        onNewChat(newTitle)
                        newTitle = ""
                    },
                    enabled = selectedModel != null,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Создать")
                }
            }
        }

        AnimatedVisibility(chats.isEmpty()) {
            GlassCard {
                Text(
                    "Пока нет чатов. Создайте первый диалог, чтобы начать.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(chats) { chat ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChatSelected(chat) }
                ) {
                    Column {
                        Text(
                            chat.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Модель: ${chat.modelKey.modelId.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    chat: ChatThread?,
    messages: List<ChatMessage>,
    isBusy: Boolean,
    isGeneratingHere: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Назад") }
            Text(
                chat?.title ?: "Чат",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        if (messages.isEmpty()) {
            GlassCard {
                Text(
                    "Сообщений пока нет. Напишите сообщение, чтобы получить ответ.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isBusy && !isGeneratingHere) {
                    Text(
                        "Идет генерация в другом чате. Дождитесь завершения или остановите её.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Введите сообщение") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                )
                if (isGeneratingHere) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isBusy && !isGeneratingHere) {
                        TextButton(onClick = onStop) {
                            Text("Остановить")
                        }
                    }
                    Button(
                        onClick = {
                            if (isGeneratingHere) {
                                onStop()
                            } else {
                                onSend(input)
                                input = ""
                            }
                        },
                        enabled = isGeneratingHere || !isBusy,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                        Text(if (isGeneratingHere) "Стоп" else "Отправить")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    val bubbleColor = if (isUser) SenchaGreen.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = textColor,
            )
        }
    }
}

@Composable
private fun ModelsScreen(
    state: ModelCatalogState,
    selectedModel: ModelEntry?,
    onImport: (String) -> JobHandle<LocalModelRecord>,
    onDownload: (ModelDownloadSpec) -> JobHandle<LocalModelRecord>,
    onSelect: (ModelEntry) -> Unit,
    onRefresh: () -> Unit,
) {
    val availabilityPolicy = remember { ModelAvailabilityPolicy() }
    val context = LocalContext.current
    val deviceProfile = remember(context) { AndroidDeviceProfileProvider.current(context) }
    var downloadUrl by remember { mutableStateOf("") }
    var downloadSha by remember { mutableStateOf("") }
    var installJob by remember { mutableStateOf<JobHandle<LocalModelRecord>?>(null) }
    var installSnapshot by remember { mutableStateOf<JobSnapshot?>(null) }
    var installMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(installJob) {
        installSnapshot = null
        installMessage = null
        val job = installJob ?: return@LaunchedEffect
        job.snapshot.collect { snapshot ->
            installSnapshot = snapshot
            if (snapshot.state == JobState.FAILED) {
                installMessage = snapshot.error?.message
            }
        }
    }

    LaunchedEffect(installJob) {
        val job = installJob ?: return@LaunchedEffect
        job.output.collect {
            installMessage = null
            onRefresh()
        }
    }

    val isInstalling = installSnapshot?.state == JobState.RUNNING || installSnapshot?.state == JobState.QUEUED
    val progress = installSnapshot?.progress?.fraction?.toFloat()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        installJob = onImport(uri.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Каталог моделей", style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(onClick = onRefresh) { Text("Обновить") }
                }

                Text("Импорт и загрузка", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text("Импортировать GGUF")
                    }
                }
                TextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    placeholder = { Text("URL на GGUF") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = downloadSha,
                    onValueChange = { downloadSha = it },
                    placeholder = { Text("SHA256") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(
                        onClick = {
                            val descriptor = ModelDescriptor(
                                id = ModelId(downloadUrl.substringAfterLast('/').substringBeforeLast('.')),
                                displayName = "Downloaded model",
                                capabilities = setOf(ModelCapability.LLM),
                                runtime = ModelRuntime.LLAMA_CPP,
                                source = ModelSource.download(downloadUrl),
                                artifact = ModelArtifact(
                                    format = ModelFormat.GGUF,
                                    sizeBytes = null,
                                    sha256 = downloadSha,
                                    license = null,
                                    quantization = null,
                                ),
                            )
                            installJob = onDownload(
                                ModelDownloadSpec(
                                    descriptor = descriptor,
                                    url = downloadUrl,
                                    sha256 = downloadSha,
                                    sizeBytes = 0,
                                )
                            )
                        },
                        enabled = downloadUrl.isNotBlank() && downloadSha.length >= 8 && !isInstalling,
                    ) {
                        Text("Скачать")
                    }
                }
                if (isInstalling) {
                    if (progress != null) {
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                installMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (state.errors.isNotEmpty()) {
            ErrorBanner(state.errors)
        }

        if (state.entries.isEmpty()) {
            GlassCard {
                Text("Моделей нет. Проверьте подключение или добавьте локальные модели.")
            }
        } else {
            val grouped = state.entries.groupBy { entry ->
                entry.descriptor.categories().ifEmpty { setOf(ModelCategory.CHAT) }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                grouped.forEach { (categories, entries) ->
                    item {
                        Text(
                            categories.joinToString(" • ") { it.displayName() },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(entries) { entry ->
                        val support = availabilityPolicy.evaluate(entry.descriptor, deviceProfile)
                        ModelRow(
                            entry = entry,
                            selected = selectedModel?.key == entry.key,
                            support = support,
                            onSelect = onSelect,
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    entry: ModelEntry,
    selected: Boolean,
    support: ModelSupport,
    onSelect: (ModelEntry) -> Unit,
) {
    val borderColor = if (selected) SenchaGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = support.isSupported) { onSelect(entry) },
        borderColor = borderColor,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.descriptor.displayName, style = MaterialTheme.typography.titleSmall)
                if (selected) {
                    Text("Выбрана", color = SenchaLeaf, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                "Провайдер: ${entry.key.providerId.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val artifact = entry.descriptor.artifact
            val sizeMb = artifact?.sizeBytes?.let { it / (1024 * 1024) }
            val minRam = entry.descriptor.resources.minRamMb
            val quant = artifact?.quantization
            val license = artifact?.license
            val sha = artifact?.sha256
            if (sizeMb != null || minRam != null || quant != null) {
                Text(
                    listOfNotNull(
                        sizeMb?.let { "Размер: ${it}MB" },
                        minRam?.let { "RAM: ${it}MB+" },
                        quant?.let { "Квант: $it" },
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!license.isNullOrBlank()) {
                Text("Лицензия: $license", style = MaterialTheme.typography.bodySmall)
            }
            if (!sha.isNullOrBlank()) {
                Text("SHA256: ${sha.take(12)}…", style = MaterialTheme.typography.bodySmall)
            }
            if (!support.isSupported) {
                Text(
                    "Недоступна: ${support.toDisplayMessage()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(errors: List<ModelCatalogError>) {
    GlassCard(borderColor = MaterialTheme.colorScheme.error) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Проблемы с источниками", color = MaterialTheme.colorScheme.error)
            errors.forEach { error ->
                Text("${error.providerId.value}: ${error.message}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun ModelCategory.displayName(): String = when (this) {
    ModelCategory.CHAT -> "Чат"
    ModelCategory.SPEECH_TO_TEXT -> "Речь → текст"
    ModelCategory.TEXT_TO_SPEECH -> "Текст → речь"
    ModelCategory.VISION -> "Визуальные"
    ModelCategory.VIDEO -> "Видео"
}

@Composable
private fun ConnectionsScreen(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    isConnected: Boolean,
    syncStatus: SyncStatus,
    syncError: String?,
    onRegister: (String, String?) -> Unit,
    onSyncNow: () -> Unit,
    nodes: List<NodeInfo>,
    nodesLoading: Boolean,
    nodesError: String?,
    nodeTests: Map<String, String>,
    onRefreshNodes: () -> Unit,
    onAddNode: (String, String) -> Unit,
    onTestNode: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var nodeName by remember { mutableStateOf("") }
    var nodeAddress by remember { mutableStateOf("") }
    val context = LocalContext.current
    val online = remember { isOnline(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sync storage", style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    placeholder = { Text("https://sync.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                )
                TextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("Одноразовый код") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                )
                TextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    placeholder = { Text("Имя устройства (опционально)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onRegister(code, deviceName) }) {
                        Text("Подключить")
                    }
                    OutlinedButton(onClick = onSyncNow, enabled = isConnected) {
                        Text("Синхронизировать")
                    }
                }
                Text(
                    text = if (isConnected) "Статус: подключено" else "Статус: не подключено",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Последняя синхронизация: ${formatEpochMillis(syncStatus.lastSyncAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "В очереди: события ${syncStatus.pendingEvents}, медиа ${syncStatus.pendingBlobs}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!syncError.isNullOrBlank()) {
                    Text(
                        text = syncError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!syncStatus.lastErrorMessage.isNullOrBlank()) {
                    Text(
                        text = "Ошибка: ${syncStatus.lastErrorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!online) {
                    Text(
                        text = "Оффлайн: синхронизация приостановлена",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Compute nodes", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onRefreshNodes) { Text("Обновить") }
                }
                Text(
                    text = "Рекомендуем: используйте Tailscale/ZeroTier для подключения домашнего сервера без проброса портов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nodesLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (nodesError != null) {
                    Text(
                        text = nodesError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (nodes.isEmpty()) {
                    Text(
                        text = "Узлы не добавлены.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        nodes.forEach { node ->
                            val testResult = nodeTests[node.address]
                            GlassCard(borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) {
                                Column {
                                    Text(node.name, style = MaterialTheme.typography.titleSmall)
                                    Text(node.address, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "Последний пинг: ${formatEpochMillis(node.lastSeenEpochMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!testResult.isNullOrBlank()) {
                                        Text(
                                            text = "Test connection: $testResult",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    TextButton(onClick = { onTestNode(node.address) }) {
                                        Text("Test connection")
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Добавить узел", style = MaterialTheme.typography.labelMedium)
                TextField(
                    value = nodeName,
                    onValueChange = { nodeName = it },
                    placeholder = { Text("Имя узла") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                )
                TextField(
                    value = nodeAddress,
                    onValueChange = { nodeAddress = it },
                    placeholder = { Text("Адрес (например, https://node.local)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                )
                FilledTonalButton(onClick = { onAddNode(nodeName, nodeAddress) }) {
                    Text("Сохранить узел")
                }
            }
        }
    }
}

@Composable
private fun GlassTopBar(title: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GlassBottomBar(
    current: Screen,
    onNavigate: (Screen) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
        NavigationBarItem(
            selected = current is Screen.ChatList || current is Screen.ChatDetail,
            onClick = { onNavigate(Screen.ChatList) },
            icon = { Icon(Icons.Default.ChatBubbleOutline, null) },
            label = { Text("Чаты") },
        )
        NavigationBarItem(
            selected = current is Screen.Models,
            onClick = { onNavigate(Screen.Models) },
            icon = { Icon(Icons.Default.Tune, null) },
            label = { Text("Модели") },
        )
        NavigationBarItem(
            selected = current is Screen.Connections,
            onClick = { onNavigate(Screen.Connections) },
            icon = { Icon(Icons.Default.Cloud, null) },
            label = { Text("Связи") },
        )
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

private val SenchaBackground = Brush.verticalGradient(
    listOf(SenchaClay, SenchaMist),
)

private fun formatEpochMillis(epochMillis: Long?): String {
    if (epochMillis == null) return "—"
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(java.time.Instant.ofEpochMilli(epochMillis))
}

private fun isOnline(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.net.ConnectivityManager::class.java)
        ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun SenchaTheme(content: @Composable () -> Unit) {
    val lightScheme = lightColorScheme(
        primary = SenchaGreen,
        secondary = SenchaLeaf,
        surface = Color(0xFFFDFCFB),
        background = SenchaClay,
    )
    val darkScheme = darkColorScheme(
        primary = SenchaGreen,
        secondary = SenchaLeaf,
        surface = Color(0xFF1F2522),
        background = Color(0xFF121614),
    )

    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkScheme else lightScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
