package com.sencha.sencha.local

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val name: String?,
    val license: String?,
    val quantization: String?,
    val architecture: String?,
    val contextLength: Int?,
)

class GgufMetadataReader {
    companion object {
        private const val GGUF_MAGIC = 0x46554747
        private const val GGUF_VERSION_MIN = 2
        private const val GGUF_VERSION_MAX = 3

        private const val TYPE_UINT8 = 0
        private const val TYPE_INT8 = 1
        private const val TYPE_UINT16 = 2
        private const val TYPE_INT16 = 3
        private const val TYPE_UINT32 = 4
        private const val TYPE_INT32 = 5
        private const val TYPE_FLOAT32 = 6
        private const val TYPE_BOOL = 7
        private const val TYPE_STRING = 8
        private const val TYPE_ARRAY = 9
        private const val TYPE_UINT64 = 10
        private const val TYPE_INT64 = 11
        private const val TYPE_FLOAT64 = 12

        fun read(file: File): GgufMetadata {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(0)
                val magic = raf.readU32LE()
                require(magic == GGUF_MAGIC.toLong()) { "GGUF magic invalid" }
                val version = raf.readU32LE()
                require(version in GGUF_VERSION_MIN..GGUF_VERSION_MAX) { "Unsupported GGUF version: $version" }

                val tensorCount = raf.readU64LE()
                val kvCount = raf.readU64LE()
                if (tensorCount < 0 || kvCount < 0) {
                    throw IllegalArgumentException("GGUF header invalid")
                }

                val metadata = mutableMapOf<String, String>()
                repeat(kvCount.toInt()) {
                    val key = raf.readString()
                    val valueType = raf.readU32LE().toInt()
                    when (valueType) {
                        TYPE_STRING -> metadata[key] = raf.readString()
                        TYPE_ARRAY -> raf.skipArray()
                        else -> raf.skipValue(valueType)
                    }
                }

                val architecture = metadata["general.architecture"]
                val contextLength = when {
                    architecture != null -> metadata["$architecture.context_length"]?.toIntOrNull()
                    else -> metadata["llama.context_length"]?.toIntOrNull()
                }

                return GgufMetadata(
                    name = metadata["general.name"],
                    license = metadata["general.license"] ?: metadata["general.license.name"],
                    quantization = metadata["general.file_type"] ?: metadata["general.quantized_by"],
                    architecture = architecture,
                    contextLength = contextLength,
                )
            }
        }

        private fun RandomAccessFile.readU32LE(): Long {
            val buffer = ByteArray(4)
            readFully(buffer)
            return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        }

        private fun RandomAccessFile.readU64LE(): Long {
            val buffer = ByteArray(8)
            readFully(buffer)
            return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).long
        }

        private fun RandomAccessFile.readString(): String {
            val length = readU64LE()
            require(length >= 0 && length <= Int.MAX_VALUE) { "Invalid string length" }
            val buffer = ByteArray(length.toInt())
            readFully(buffer)
            return buffer.toString(Charsets.UTF_8)
        }

        private fun RandomAccessFile.skipValue(type: Int) {
            val size = when (type) {
                TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1
                TYPE_UINT16, TYPE_INT16 -> 2
                TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4
                TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8
                else -> 0
            }
            if (size > 0) {
                seek(filePointer + size)
            }
        }

        private fun RandomAccessFile.skipArray() {
            val elementType = readU32LE().toInt()
            val count = readU64LE()
            if (elementType == TYPE_STRING) {
                repeat(count.toInt()) { readString() }
                return
            }
            val elementSize = when (elementType) {
                TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1
                TYPE_UINT16, TYPE_INT16 -> 2
                TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4
                TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8
                else -> 0
            }
            if (elementSize > 0) {
                seek(filePointer + elementSize * count)
            }
        }
    }
}
