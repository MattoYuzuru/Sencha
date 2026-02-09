package com.sencha.sencha.core.data

import com.sencha.sencha.core.domain.LocalModelRecord
import com.sencha.sencha.core.domain.LocalModelStore
import com.sencha.sencha.core.model.ModelId

class InMemoryLocalModelStore(
    initial: List<LocalModelRecord> = emptyList(),
) : LocalModelStore {
    private val records = LinkedHashMap<ModelId, LocalModelRecord>().apply {
        initial.forEach { put(it.descriptor.id, it) }
    }

    override suspend fun list(): List<LocalModelRecord> = records.values.toList()

    override suspend fun find(modelId: ModelId): LocalModelRecord? = records[modelId]

    override suspend fun upsert(record: LocalModelRecord) {
        records[record.descriptor.id] = record
    }

    override suspend fun remove(modelId: ModelId) {
        records.remove(modelId)
    }
}
