package com.sencha.sencha.core.domain.sync

import com.sencha.sencha.core.domain.ChatRole
import kotlinx.serialization.Serializable

@Serializable
data class ChatCreatedPayload(
    val title: String,
    val modelProviderId: String,
    val modelId: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class MessageCreatedPayload(
    val messageId: String,
    val role: ChatRole,
    val content: String,
    val createdAtEpochMillis: Long,
)
