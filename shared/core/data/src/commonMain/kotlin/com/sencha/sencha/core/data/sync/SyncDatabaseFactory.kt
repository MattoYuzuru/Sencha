package com.sencha.sencha.core.data.sync

import app.cash.sqldelight.db.SqlDriver
import com.sencha.sencha.core.data.sync.db.SyncDatabase

interface SyncDatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

class SyncDatabaseFactory(private val driverFactory: SyncDatabaseDriverFactory) {
    fun create(): SyncDatabase = SyncDatabase(driverFactory.createDriver())
}
