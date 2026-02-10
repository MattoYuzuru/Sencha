package com.sencha.sencha.core.domain

interface JobRepository {
    fun upsert(record: JobRecord)

    fun find(jobId: String): JobRecord?

    fun list(): List<JobRecord>
}
