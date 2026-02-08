@file:Suppress("MagicNumber")

package com.sencha.sencha

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sencha.sencha.core.data.*
import com.sencha.sencha.core.domain.*
import com.sencha.sencha.core.jobs.InMemoryJobEngine
import com.sencha.sencha.core.model.ModelCategory
import com.sencha.sencha.core.model.categories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val SenchaGreen = Color(0xFF7DBE9F)
private val SenchaLeaf = Color(0xFF5C8F79)
private val SenchaMist = Color(0xFFE8F2ED)
private val SenchaClay = Color(0xFFF6F3EE)

@Composable
@Preview
fun App() {
    SenchaTheme {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope) }
        val modelState by state.modelCatalogState.collectAsState()
        val chats by state.chatRepository.chats().collectAsState()
        var currentScreen by remember { mutableStateOf<Screen>(Screen.ChatList) }

        LaunchedEffect(Unit) {
            state.refreshModels()
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
                            ChatScreen(
                                chat = state.chatRepository.findChat(chatId),
                                messages = messages,
                                onBack = { currentScreen = Screen.ChatList },
                                onSend = { text -> state.sendMessage(chatId, text) },
                            )
                        }
                        Screen.Models -> {
                            ModelsScreen(
                                state = modelState,
                                selectedModel = state.selectedModel.value,
                                onSelect = { entry -> state.selectedModel.value = entry },
                                onRefresh = { state.refreshModels() },
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
    data class ChatDetail(val chatId: ChatId) : Screen("Чат")
}

private class AppState(private val scope: CoroutineScope) {
    private val jobEngine = InMemoryJobEngine(scope = scope)
    private val localProvider = LocalTextLLMProvider()
    private val remoteProvider = RemoteOllamaProvider("http://localhost:11434")
    private val registry: ModelProviderRegistry = DefaultModelProviderRegistry(
        listOf(localProvider, remoteProvider)
    )

    val modelCatalog = ModelCatalogService(registry)
    val modelCatalogState = modelCatalog.state()
    val chatRepository = InMemoryChatRepository()
    val selectedModel: MutableState<ModelEntry?> = mutableStateOf(null)
    private val chatUseCase = ChatUseCase(
        repository = chatRepository,
        providers = registry,
        jobEngine = jobEngine,
        scope = scope,
    )

    fun createChat(title: String): ChatThread {
        val selected = selectedModel.value ?: modelCatalogState.value.entries.firstOrNull()
            ?: error("No models available")
        selectedModel.value = selected
        return chatUseCase.createChat(title.ifBlank { selected.descriptor.displayName }, selected.key)
    }

    fun sendMessage(chatId: ChatId, text: String) {
        if (text.isBlank()) return
        chatUseCase.sendUserMessage(chatId, text)
    }

    fun refreshModels() {
        scope.launch { modelCatalog.refresh() }
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
    onBack: () -> Unit,
    onSend: (String) -> Unit,
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
                Button(
                    onClick = {
                        onSend(input)
                        input = ""
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Отправить")
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
    onSelect: (ModelEntry) -> Unit,
    onRefresh: () -> Unit,
) {
    val availabilityPolicy = remember { ModelAvailabilityPolicy() }
    val deviceProfile = DeviceProfile(availableRamMb = 4096, availableDiskMb = 10_240, isNetworkAvailable = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Каталог моделей", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = onRefresh) { Text("Обновить") }
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
            if (!support.isSupported) {
                Text(
                    "Недоступна: ${support.violations.joinToString()}",
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
