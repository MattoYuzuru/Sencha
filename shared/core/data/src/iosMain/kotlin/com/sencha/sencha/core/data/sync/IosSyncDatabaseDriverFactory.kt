package com.sencha.sencha.core.data.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.sencha.sencha.core.data.sync.db.SyncDatabase

class IosSyncDatabaseDriverFactory(
    private val databaseName: String = "sencha_sync.db",
) : SyncDatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        return NativeSqliteDriver(SyncDatabase.Schema, databaseName)
    }
}
