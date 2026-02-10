package com.sencha.sencha.core.data

import com.sencha.sencha.core.data.sync.db.SyncDatabase
import com.sencha.sencha.core.domain.JobRecord
import com.sencha.sencha.core.domain.JobRepository
import com.sencha.sencha.core.domain.JobType
import com.sencha.sencha.core.domain.ModelKey
import com.sencha.sencha.core.domain.ModelProviderId
import com.sencha.sencha.core.jobs.JobErrorCode
import com.sencha.sencha.core.jobs.JobState
import com.sencha.sencha.core.model.ModelId

class SqlJobRepository(
    private val database: SyncDatabase,
) : JobRepository {
    private val queries = database.syncDatabaseQueries

    override fun upsert(record: JobRecord) {
        queries.upsertJob(
            job_id = record.id,
            type = record.type.name,
            state = record.state.name,
            model_provider_id = record.modelKey.providerId.value,
            model_id = record.modelKey.modelId.value,
            payload_json = record.payloadJson,
            error_code = record.errorCode?.name,
            error_message = record.errorMessage,
            created_at = record.createdAtEpochMillis,
            updated_at = record.updatedAtEpochMillis,
        )
    }

    override fun find(jobId: String): JobRecord? {
        val record = queries.selectJobById(jobId).executeAsOneOrNull() ?: return null
        return record.toJobRecord()
    }

    override fun list(): List<JobRecord> {
        return queries.selectAllJobs().executeAsList().map { it.toJobRecord() }
    }

    private fun com.sencha.sencha.core.data.sync.db.Jobs.toJobRecord(): JobRecord {
        return JobRecord(
            id = job_id,
            type = JobType.valueOf(type),
            state = JobState.valueOf(state),
            modelKey = ModelKey(
                providerId = ModelProviderId(model_provider_id),
                modelId = ModelId(model_id),
            ),
            payloadJson = payload_json,
            errorCode = error_code?.let { JobErrorCode.valueOf(it) },
            errorMessage = error_message,
            createdAtEpochMillis = created_at,
            updatedAtEpochMillis = updated_at,
        )
    }
}
