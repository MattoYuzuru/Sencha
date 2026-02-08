package com.sencha.sencha.core.domain

import com.sencha.sencha.core.jobs.JobEngine
import com.sencha.sencha.core.jobs.JobHandle
import com.sencha.sencha.core.jobs.JobState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.random.Random

class ChatUseCase(
    private val repository: ChatRepository,
    private val providers: ModelProviderRegistry,
    private val jobEngine: JobEngine,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    fun createChat(title: String, modelKey: ModelKey): ChatThread {
        return repository.createChat(title, modelKey)
    }

    fun sendUserMessage(chatId: ChatId, content: String): JobHandle<ChatDelta> {
        val chat = repository.findChat(chatId)
            ?: error("Chat not found: ${chatId.value}")
        val provider = providers.providerFor(chat.modelKey)
            ?: error("Provider not found: ${chat.modelKey.providerId.value}")

        val existingMessages = repository.messages(chatId).value
        val userMessage = ChatMessage(
            id = ChatMessageId("msg-${clock.now().toEpochMilliseconds()}-${Random.nextInt()}"),
            role = ChatRole.USER,
            content = content,
            createdAt = clock.now(),
        )
        val history = existingMessages + userMessage
        repository.appendMessage(chatId, userMessage)

        val assistantMessage = ChatMessage(
            id = ChatMessageId("msg-${clock.now().toEpochMilliseconds()}-${Random.nextInt()}-assistant"),
            role = ChatRole.ASSISTANT,
            content = "",
            createdAt = clock.now(),
        )
        repository.appendMessage(chatId, assistantMessage)

        val request = ChatRequest(
            modelKey = chat.modelKey,
            messages = history,
        )
        val handle = jobEngine.submit(provider.createChatJob(request))

        scope.launch {
            var current = ""
            handle.output.collect { delta ->
                current += delta.text
                repository.updateMessage(chatId, assistantMessage.id, current, delta.isFinal)
            }
        }

        scope.launch {
            handle.snapshot.collect { snapshot ->
                if (snapshot.state == JobState.FAILED) {
                    val message = snapshot.error?.message ?: "Unknown error"
                    repository.updateMessage(chatId, assistantMessage.id, "Ошибка: $message", true)
                }
            }
        }

        return handle
    }
}
