package com.sencha.sencha.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sencha.sencha.core.data.HttpClientFactory
import com.sencha.sencha.core.domain.*
import com.sencha.sencha.core.model.*
import io.ktor.client.request.get
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.core.net.toUri

class AndroidLocalModelStore(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) : LocalModelStore {
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    private val indexFile = File(modelsDir, "models.json")

    override suspend fun list(): List<LocalModelRecord> = withContext(Dispatchers.IO) {
        readIndex().toList()
    }

    override suspend fun find(modelId: ModelId): LocalModelRecord? = withContext(Dispatchers.IO) {
        readIndex().firstOrNull { it.descriptor.id == modelId }
    }

    override suspend fun upsert(record: LocalModelRecord) = withContext(Dispatchers.IO) {
        val current = readIndex().toMutableList()
        val existing = current.indexOfFirst { it.descriptor.id == record.descriptor.id }
        if (existing >= 0) {
            current[existing] = record
        } else {
            current.add(record)
        }
        writeIndex(current)
    }

    override suspend fun remove(modelId: ModelId) = withContext(Dispatchers.IO) {
        val current = readIndex().filterNot { it.descriptor.id == modelId }
        writeIndex(current)
    }

    private fun readIndex(): List<LocalModelRecord> {
        if (!indexFile.exists()) return emptyList()
        val text = indexFile.readText()
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(text)
    }

    private fun writeIndex(records: List<LocalModelRecord>) {
        if (!modelsDir.exists()) modelsDir.mkdirs()
        indexFile.writeText(json.encodeToString(records))
    }

    fun modelFile(name: String): File = File(modelsDir, name)
}

class AndroidModelInstaller(
    private val context: Context,
    private val store: AndroidLocalModelStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ModelInstaller {
    private val client = HttpClientFactory.create()

    override suspend fun importModel(request: ModelImportRequest): Result<LocalModelRecord> =
        importModel(request, onProgress = { _, _ -> })

    override suspend fun downloadModel(request: ModelDownloadRequest): Result<LocalModelRecord> =
        downloadModel(request, onProgress = { _, _ -> })

    override suspend fun validate(record: LocalModelRecord): Result<Unit> = runCatching {
        val file = File(record.fileUri.toUri().path ?: error("Invalid file path"))
        GgufMetadataReader.read(file)
    }

    suspend fun importModel(
        request: ModelImportRequest,
        onProgress: suspend (Long, Long?) -> Unit,
    ): Result<LocalModelRecord> = runCatching {
        val uri = Uri.parse(request.uri)
        val displayName = context.contentResolver.queryDisplayName(uri) ?: "imported.gguf"
        val target = store.modelFile(displayName)
        val totalBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length?.takeIf { it > 0 }
        val copy = context.contentResolver.openInputStream(uri)?.use { input ->
            copyToFile(input, target, totalBytes, onProgress)
        } ?: error("Cannot read file")

        val metadata = GgufMetadataReader.read(target)
        val descriptor = ModelDescriptorFactory.fromMetadata(
            file = target,
            sha256 = copy.sha256,
            metadata = metadata,
        )
        val record = LocalModelRecord(
            descriptor = descriptor,
            fileUri = target.toURI().toString(),
        )
        validate(record).getOrThrow()
        store.upsert(record)
        record
    }

    suspend fun downloadModel(
        request: ModelDownloadRequest,
        onProgress: suspend (Long, Long?) -> Unit,
    ): Result<LocalModelRecord> = runCatching {
        val spec = request.spec
        val fileName = "${spec.descriptor.id.value}.gguf"
        val target = store.modelFile(fileName)
        val copy = downloadToFile(spec.url, target, spec.sizeBytes.takeIf { it > 0 }, onProgress)
        if (!copy.sha256.equals(spec.sha256, ignoreCase = true)) {
            target.delete()
            error("SHA256 mismatch")
        }

        val metadata = GgufMetadataReader.read(target)
        val descriptor = ModelDescriptorFactory.fromMetadata(
            file = target,
            sha256 = copy.sha256,
            metadata = metadata,
            overrideId = spec.descriptor.id,
            overrideName = spec.descriptor.displayName.ifBlank { null },
        )
        val record = LocalModelRecord(descriptor = descriptor, fileUri = target.toURI().toString())
        validate(record).getOrThrow()
        store.upsert(record)
        record
    }

    private suspend fun downloadToFile(
        url: String,
        target: File,
        expectedSize: Long?,
        onProgress: suspend (Long, Long?) -> Unit,
    ): FileCopyResult = withContext(Dispatchers.IO) {
        val response = client.get(url)
        val headerSize = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val totalBytes = expectedSize ?: headerSize
        val channel = response.bodyAsChannel()
        val digest = MessageDigest.getInstance("SHA-256")
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                total += read
                onProgress(total, totalBytes)
            }
            FileCopyResult(
                sha256 = digest.digest().toHex(),
                sizeBytes = total,
            )
        }
    }

    private suspend fun copyToFile(
        input: InputStream,
        target: File,
        totalBytes: Long?,
        onProgress: suspend (Long, Long?) -> Unit,
    ): FileCopyResult = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                total += read
                onProgress(total, totalBytes)
            }
            FileCopyResult(
                sha256 = digest.digest().toHex(),
                sizeBytes = total,
            )
        }
    }

    private fun ContentResolver.queryDisplayName(uri: Uri): String? {
        return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }
}

private data class FileCopyResult(
    val sha256: String,
    val sizeBytes: Long,
)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private object ModelDescriptorFactory {
    private const val MB = 1024 * 1024.0

    fun fromMetadata(
        file: File,
        sha256: String,
        metadata: GgufMetadata,
        overrideId: ModelId? = null,
        overrideName: String? = null,
    ): ModelDescriptor {
        val name = overrideName ?: metadata.name ?: file.nameWithoutExtension
        val quant = metadata.quantization ?: inferQuantFromFileName(file.name)
        val sizeBytes = file.length()
        val minRam = ceil((sizeBytes / MB) * 1.3).roundToInt().coerceAtLeast(256)
        val minDisk = ceil(sizeBytes / MB).roundToInt().coerceAtLeast(64)

        return ModelDescriptor(
            id = overrideId ?: ModelId(name),
            displayName = name,
            capabilities = setOf(ModelCapability.LLM),
            resources = ModelResourceProfile(
                minRamMb = minRam,
                minDiskMb = minDisk,
                maxContextTokens = metadata.contextLength,
                requiresNetwork = false,
            ),
            runtime = ModelRuntime.LLAMA_CPP,
            source = ModelSource.local(),
            artifact = ModelArtifact(
                format = ModelFormat.GGUF,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                license = metadata.license,
                quantization = quant,
            ),
        )
    }

    private fun inferQuantFromFileName(fileName: String): String? {
        val match = Regex("Q\\d_[A-Z]+").find(fileName.uppercase())
        return match?.value
    }
}
