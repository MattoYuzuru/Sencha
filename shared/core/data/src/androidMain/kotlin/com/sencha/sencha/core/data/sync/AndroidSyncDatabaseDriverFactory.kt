package com.sencha.sencha.core.data.sync

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sencha.sencha.core.data.sync.db.SyncDatabase

class AndroidSyncDatabaseDriverFactory(
    private val context: Context,
    private val databaseName: String = "sencha_sync.db",
) : SyncDatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(SyncDatabase.Schema, context, databaseName)
    }
}
