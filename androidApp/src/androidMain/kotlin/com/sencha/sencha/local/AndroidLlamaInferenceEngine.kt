package com.sencha.sencha.local

import android.content.Context
import android.net.Uri
import com.sencha.sencha.android.llama.LlamaBridge
import com.sencha.sencha.core.domain.ChatRequest
import com.sencha.sencha.core.domain.ChatRole
import com.sencha.sencha.core.domain.InferenceConfig
import com.sencha.sencha.core.domain.LocalModelRecord
import com.sencha.sencha.core.domain.LocalTextInferenceEngine
import com.sencha.sencha.core.domain.ModelAvailabilityPolicy
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidLlamaInferenceEngine(
    private val context: Context,
) : LocalTextInferenceEngine {
    private val bridge = LlamaBridge()
    private val lock = Mutex()
    private val availabilityPolicy = ModelAvailabilityPolicy()
    private var handle: Long = 0
    private var loadedModelId: String? = null
    private var currentConfig: InferenceConfig? = null

    override suspend fun load(model: LocalModelRecord, config: InferenceConfig): Result<Unit> = lock.withLock {
        val file = modelFile(model)
            ?: return Result.failure(IllegalArgumentException("Файл модели не найден"))

        val support = availabilityPolicy.evaluate(model.descriptor, AndroidDeviceProfileProvider.current(context))
        if (!support.isSupported) {
            return Result.failure(IllegalStateException("Модель недоступна: ${support.toDisplayMessage()}"))
        }

        if (handle == 0L) {
            handle = bridge.createSession()
        }
        if (handle == 0L) {
            return Result.failure(IllegalStateException("Не удалось создать native-сессию"))
        }

        if (loadedModelId == model.descriptor.id.value && currentConfig == config) {
            return Result.success(Unit)
        }

        val ok = bridge.loadModel(handle, file.absolutePath, config.maxContextTokens)
        if (!ok) {
            val error = bridge.lastError(handle) ?: "Не удалось загрузить модель"
            return Result.failure(IllegalStateException(error))
        }
        loadedModelId = model.descriptor.id.value
        currentConfig = config
        return Result.success(Unit)
    }

    override suspend fun generate(
        request: ChatRequest,
        onToken: suspend (String, Boolean) -> Unit,
    ): Result<Unit> = lock.withLock {
        val config = currentConfig ?: return Result.failure(IllegalStateException("Модель не загружена"))
        val prompt = buildPrompt(request)

        return withContext(Dispatchers.Default) {
            val job = currentCoroutineContext()[Job]
            job?.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    bridge.cancel(handle)
                }
            }

            coroutineScope {
                val channel = Channel<TokenChunk>(Channel.BUFFERED)
                val collector = launch {
                    for (chunk in channel) {
                        onToken(chunk.text, chunk.isFinal)
                    }
                }

                val callback = object : LlamaBridge.TokenCallback {
                    override fun onToken(text: String, isFinal: Boolean): Boolean {
                        if (job?.isActive == false) return false
                        return channel.trySend(TokenChunk(text, isFinal)).isSuccess
                    }
                }

                val ok = bridge.generate(
                    handle = handle,
                    prompt = prompt,
                    maxTokens = config.maxOutputTokens,
                    temperature = config.temperature.toFloat(),
                    callback = callback,
                )

                channel.close()
                collector.join()

                if (job?.isActive == false) {
                    throw CancellationException()
                }

                if (!ok) {
                    val error = bridge.lastError(handle)
                    if (error?.contains("CANCELED", ignoreCase = true) == true) {
                        throw CancellationException()
                    }
                    val message = mapNativeError(error)
                    return@coroutineScope Result.failure(IllegalStateException(message))
                }
                return@coroutineScope Result.success(Unit)
            }
        }
    }

    override fun cancel() {
        if (handle != 0L) {
            bridge.cancel(handle)
        }
    }

    override fun unload() {
        if (handle != 0L) {
            bridge.destroySession(handle)
        }
        handle = 0
        loadedModelId = null
        currentConfig = null
    }

    private fun modelFile(model: LocalModelRecord): File? {
        val uri = Uri.parse(model.fileUri)
        val path = uri.path ?: return null
        val file = File(path)
        return file.takeIf { it.exists() }
    }

    private fun buildPrompt(request: ChatRequest): String {
        val builder = StringBuilder()
        request.messages.forEach { message ->
            when (message.role) {
                ChatRole.SYSTEM -> builder.append("System: ")
                ChatRole.USER -> builder.append("User: ")
                ChatRole.ASSISTANT -> builder.append("Assistant: ")
            }
            builder.append(message.content.trim()).append('\n')
        }
        if (request.messages.lastOrNull()?.role != ChatRole.ASSISTANT) {
            builder.append("Assistant: ")
        }
        return builder.toString()
    }

    private fun mapNativeError(error: String?): String {
        if (error.isNullOrBlank()) return "Ошибка локального inference"
        if (error.contains("context", ignoreCase = true)) {
            return "Контекст слишком длинный. Уменьшите историю или max tokens."
        }
        return error
    }

    private data class TokenChunk(
        val text: String,
        val isFinal: Boolean,
    )
}
