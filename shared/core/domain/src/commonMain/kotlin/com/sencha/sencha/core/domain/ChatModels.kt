package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.ModelId
import kotlin.time.Instant

@JvmInline
value class ChatId(val value: String)

@JvmInline
value class ChatMessageId(val value: String)

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
}

data class ChatMessage(
    val id: ChatMessageId,
    val role: ChatRole,
    val content: String,
    val createdAt: Instant,
)

data class ChatThread(
    val id: ChatId,
    val title: String,
    val modelKey: ModelKey,
    val createdAt: Instant,
)

data class ChatRequest(
    val modelKey: ModelKey,
    val messages: List<ChatMessage>,
)

data class ChatDelta(
    val text: String,
    val isFinal: Boolean = false,
)
