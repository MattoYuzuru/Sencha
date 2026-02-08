package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ChatId
import com.sencha.sencha.core.domain.ChatMessage
import com.sencha.sencha.core.domain.ChatMessageId
import com.sencha.sencha.core.domain.ChatRepository
import com.sencha.sencha.core.domain.ChatThread
import com.sencha.sencha.core.domain.ModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

class InMemoryChatRepository(
    private val clock: Clock = Clock.System,
) : ChatRepository {
    private val chatsFlow = MutableStateFlow<List<ChatThread>>(emptyList())
    private val messageFlows = mutableMapOf<ChatId, MutableStateFlow<List<ChatMessage>>>()

    override fun chats(): StateFlow<List<ChatThread>> = chatsFlow

    override fun messages(chatId: ChatId): StateFlow<List<ChatMessage>> {
        return messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    }

    override fun findChat(chatId: ChatId): ChatThread? = chatsFlow.value.firstOrNull { it.id == chatId }

    override fun createChat(title: String, modelKey: ModelKey): ChatThread {
        val chat = ChatThread(
            id = ChatId("chat-${clock.now().toEpochMilliseconds()}"),
            title = title,
            modelKey = modelKey,
            createdAt = clock.now(),
        )
        chatsFlow.value = (chatsFlow.value + chat).sortedByDescending { it.createdAt }
        messageFlows.getOrPut(chat.id) { MutableStateFlow(emptyList()) }
        return chat
    }

    override fun appendMessage(chatId: ChatId, message: ChatMessage) {
        val flow = messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        flow.value += message
    }

    override fun updateMessage(chatId: ChatId, messageId: ChatMessageId, newContent: String, isFinal: Boolean) {
        val flow = messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.map { existing ->
            if (existing.id == messageId) {
                existing.copy(content = newContent)
            } else {
                existing
            }
        }
    }
}
