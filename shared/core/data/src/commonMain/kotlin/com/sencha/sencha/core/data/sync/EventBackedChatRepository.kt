package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.domain.ChatId
import com.sencha.sencha.core.domain.ChatMessage
import com.sencha.sencha.core.domain.ChatMessageId
import com.sencha.sencha.core.domain.ChatRepository
import com.sencha.sencha.core.domain.ChatThread
import com.sencha.sencha.core.domain.ChatRole
import com.sencha.sencha.core.domain.ModelProviderId
import com.sencha.sencha.core.domain.ModelKey
import com.sencha.sencha.core.model.ModelId
import com.sencha.sencha.core.domain.sync.ChatCreatedPayload
import com.sencha.sencha.core.domain.sync.EventPayloadEnvelope
import com.sencha.sencha.core.domain.sync.MessageCreatedPayload
import com.sencha.sencha.core.domain.sync.SyncEvent
import com.sencha.sencha.core.domain.sync.SyncEventTypes
import com.sencha.sencha.core.domain.sync.UlidGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Clock
import kotlin.time.Instant

class EventBackedChatRepository(
    private val eventStore: EventStore,
    private val deviceId: String,
    private val clock: Clock = Clock.System,
    private val onLocalEventAppended: (() -> Unit)? = null,
) : ChatRepository {
    private val chatsFlow = MutableStateFlow<List<ChatThread>>(emptyList())
    private val messagesFlow = mutableMapOf<ChatId, MutableStateFlow<List<ChatMessage>>>()
    private val pendingAssistantMessages = mutableSetOf<ChatMessageId>()

    init {
        rebuildFrom(eventStore.allEvents())
    }

    fun refreshFromStore() {
        rebuildFrom(eventStore.allEvents())
    }

    override fun chats(): StateFlow<List<ChatThread>> = chatsFlow.asStateFlow()

    override fun messages(chatId: ChatId): StateFlow<List<ChatMessage>> {
        return messagesFlow.getOrPut(chatId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    override fun findChat(chatId: ChatId): ChatThread? = chatsFlow.value.firstOrNull { it.id == chatId }

    override fun createChat(title: String, modelKey: ModelKey): ChatThread {
        val chatId = ChatId(UlidGenerator.newUlid())
        val createdAt = clock.now().toEpochMilliseconds()
        val event = SyncEvent(
            eventId = UlidGenerator.newUlid(),
            deviceId = deviceId,
            chatId = chatId.value,
            type = SyncEventTypes.CHAT_CREATED,
            payload = EventPayloadEnvelope(
                schemaVersion = 1,
                data = SyncJson.instance.encodeToJsonElement(
                    ChatCreatedPayload(
                        title = title,
                        modelProviderId = modelKey.providerId.value,
                        modelId = modelKey.modelId.value,
                        createdAtEpochMillis = createdAt,
                    )
                ),
            ),
            createdAtEpochMillis = createdAt,
        )
        eventStore.appendLocal(event)
        onLocalEventAppended?.invoke()
        val thread = ChatThread(
            id = chatId,
            title = title,
            modelKey = modelKey,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
        )
        chatsFlow.value = listOf(thread) + chatsFlow.value
        messagesFlow.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        return thread
    }

    override fun appendMessage(chatId: ChatId, message: ChatMessage) {
        val flow = messagesFlow.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + message
        val shouldPersist = message.role == ChatRole.USER || message.content.isNotBlank()
        if (!shouldPersist) {
            pendingAssistantMessages.add(message.id)
            return
        }
        val event = createMessageEvent(chatId, message)
        eventStore.appendLocal(event)
        onLocalEventAppended?.invoke()
    }

    override fun updateMessage(chatId: ChatId, messageId: ChatMessageId, newContent: String, isFinal: Boolean) {
        val flow = messagesFlow.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        val updated = flow.value.map { message ->
            if (message.id == messageId) message.copy(content = newContent) else message
        }
        flow.value = updated
        if (isFinal && pendingAssistantMessages.remove(messageId)) {
            val message = updated.firstOrNull { it.id == messageId } ?: return
            val event = createMessageEvent(chatId, message)
            eventStore.appendLocal(event)
            onLocalEventAppended?.invoke()
        }
    }

    private fun createMessageEvent(chatId: ChatId, message: ChatMessage): SyncEvent {
        val createdAt = message.createdAt.toEpochMilliseconds()
        return SyncEvent(
            eventId = UlidGenerator.newUlid(),
            deviceId = deviceId,
            chatId = chatId.value,
            type = SyncEventTypes.MESSAGE_CREATED,
            payload = EventPayloadEnvelope(
                schemaVersion = 1,
                data = SyncJson.instance.encodeToJsonElement(
                    MessageCreatedPayload(
                        messageId = message.id.value,
                        role = message.role,
                        content = message.content,
                        createdAtEpochMillis = createdAt,
                    )
                ),
            ),
            createdAtEpochMillis = createdAt,
        )
    }

    private fun rebuildFrom(events: List<SyncEvent>) {
        val threads = mutableMapOf<String, ChatThread>()
        val messages = mutableMapOf<String, MutableList<ChatMessage>>()
        for (event in events) {
            when (event.type) {
                SyncEventTypes.CHAT_CREATED -> {
                    val payload = SyncJson.instance.decodeFromJsonElement(
                        ChatCreatedPayload.serializer(),
                        event.payload.data,
                    )
                    val thread = ChatThread(
                        id = ChatId(event.chatId),
                        title = payload.title,
                        modelKey = ModelKey(
                            providerId = ModelProviderId(payload.modelProviderId),
                            modelId = ModelId(payload.modelId),
                        ),
                        createdAt = Instant.fromEpochMilliseconds(payload.createdAtEpochMillis),
                    )
                    threads[event.chatId] = thread
                }
                SyncEventTypes.MESSAGE_CREATED -> {
                    val payload = SyncJson.instance.decodeFromJsonElement(
                        MessageCreatedPayload.serializer(),
                        event.payload.data,
                    )
                    val list = messages.getOrPut(event.chatId) { mutableListOf() }
                    list.add(
                        ChatMessage(
                            id = ChatMessageId(payload.messageId),
                            role = payload.role,
                            content = payload.content,
                            createdAt = Instant.fromEpochMilliseconds(payload.createdAtEpochMillis),
                        )
                    )
                }
            }
        }
        chatsFlow.value = threads.values.sortedByDescending { it.createdAt.toEpochMilliseconds() }
        threads.keys.forEach { chatId ->
            messagesFlow[ChatId(chatId)] = MutableStateFlow(messages[chatId] ?: emptyList())
        }
    }
}
