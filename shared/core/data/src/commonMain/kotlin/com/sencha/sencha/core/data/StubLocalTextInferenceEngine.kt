package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.ChatRequest
import com.sencha.sencha.core.domain.ChatRole
import com.sencha.sencha.core.domain.InferenceConfig
import com.sencha.sencha.core.domain.LocalModelRecord
import com.sencha.sencha.core.domain.LocalTextInferenceEngine

class StubLocalTextInferenceEngine : LocalTextInferenceEngine {
    override suspend fun load(model: LocalModelRecord, config: InferenceConfig): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun generate(request: ChatRequest, onToken: suspend (String, Boolean) -> Unit): Result<Unit> {
        val lastUserMessage = request.messages.lastOrNull { it.role == ChatRole.USER }?.content
        val response = if (lastUserMessage.isNullOrBlank()) {
            "Локальная модель готова. Спроси что-нибудь."
        } else {
            "Локальный ответ: $lastUserMessage"
        }
        val parts = response.split(" ")
        parts.forEachIndexed { index, part ->
            val token = if (index == parts.lastIndex) part else "$part "
            onToken(token, index == parts.lastIndex)
        }
        return Result.success(Unit)
    }

    override fun cancel() = Unit

    override fun unload() = Unit
}
