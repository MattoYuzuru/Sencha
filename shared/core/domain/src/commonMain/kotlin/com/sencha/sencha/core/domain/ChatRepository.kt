package com.sencha.sencha.core.domain

import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    fun chats(): StateFlow<List<ChatThread>>

    fun messages(chatId: ChatId): StateFlow<List<ChatMessage>>

    fun findChat(chatId: ChatId): ChatThread?

    fun createChat(title: String, modelKey: ModelKey): ChatThread

    fun appendMessage(chatId: ChatId, message: ChatMessage)

    fun updateMessage(chatId: ChatId, messageId: ChatMessageId, newContent: String, isFinal: Boolean)
}
