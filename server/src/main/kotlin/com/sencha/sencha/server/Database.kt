package com.sencha.sencha.server

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class Database(private val dbPath: String) {
    init {
        initSchema()
    }

    private fun initSchema() {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        user_id TEXT PRIMARY KEY,
                        created_at INTEGER NOT NULL
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS devices (
                        device_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        public_key TEXT NOT NULL,
                        name TEXT,
                        created_at INTEGER NOT NULL,
                        last_seen INTEGER,
                        FOREIGN KEY(user_id) REFERENCES users(user_id)
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS registration_codes (
                        code TEXT PRIMARY KEY,
                        used_at INTEGER,
                        user_id TEXT
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS sessions (
                        token TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        FOREIGN KEY(user_id) REFERENCES users(user_id),
                        FOREIGN KEY(device_id) REFERENCES devices(device_id)
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        event_id TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        chat_id TEXT NOT NULL,
                        type TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        UNIQUE(user_id, event_id)
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS nodes (
                        node_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL,
                        last_seen INTEGER,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(user_id) REFERENCES users(user_id)
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS blobs (
                        blob_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        chat_id TEXT NOT NULL,
                        sha256 TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        mime TEXT NOT NULL,
                        object_key TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(user_id) REFERENCES users(user_id)
                    );
                    """.trimIndent()
                )
            }
        }
    }

    fun ensureRegistrationCode(code: String) {
        withConnection { connection ->
            connection.prepareStatement(
                "INSERT OR IGNORE INTO registration_codes(code) VALUES (?)"
            ).use { statement ->
                statement.setString(1, code)
                statement.executeUpdate()
            }
        }
    }

    fun consumeRegistrationCode(code: String): String? {
        return withConnection { connection ->
            connection.prepareStatement(
                "SELECT code, used_at, user_id FROM registration_codes WHERE code = ?"
            ).use { statement ->
                statement.setString(1, code)
                val result = statement.executeQuery()
                if (!result.next()) return@withConnection null
                val usedAt = result.getLongOrNull("used_at")
                if (usedAt != null && usedAt > 0) return@withConnection null
                val existingUser = result.getString("user_id")
                val userId = existingUser ?: createUser(connection)
                val now = Instant.now().toEpochMilli()
                connection.prepareStatement(
                    "UPDATE registration_codes SET used_at = ?, user_id = ? WHERE code = ?"
                ).use { update ->
                    update.setLong(1, now)
                    update.setString(2, userId)
                    update.setString(3, code)
                    update.executeUpdate()
                }
                userId
            }
        }
    }

    fun upsertDevice(deviceId: String, userId: String, publicKeyHex: String, name: String?) {
        withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO devices(device_id, user_id, public_key, name, created_at, last_seen)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    user_id = excluded.user_id,
                    public_key = excluded.public_key,
                    name = excluded.name,
                    last_seen = excluded.last_seen
                """.trimIndent()
            ).use { statement ->
                val now = Instant.now().toEpochMilli()
                statement.setString(1, deviceId)
                statement.setString(2, userId)
                statement.setString(3, publicKeyHex)
                statement.setString(4, name)
                statement.setLong(5, now)
                statement.setLong(6, now)
                statement.executeUpdate()
            }
        }
    }

    fun createSession(userId: String, deviceId: String, expiresAt: Long): String {
        val token = UUID.randomUUID().toString()
        withConnection { connection ->
            connection.prepareStatement(
                "INSERT INTO sessions(token, user_id, device_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, token)
                statement.setString(2, userId)
                statement.setString(3, deviceId)
                statement.setLong(4, Instant.now().toEpochMilli())
                statement.setLong(5, expiresAt)
                statement.executeUpdate()
            }
        }
        return token
    }

    fun findSession(token: String): SessionRecord? {
        return withConnection { connection ->
            connection.prepareStatement(
                "SELECT token, user_id, device_id, expires_at FROM sessions WHERE token = ?"
            ).use { statement ->
                statement.setString(1, token)
                val result = statement.executeQuery()
                if (!result.next()) return@withConnection null
                val expiresAt = result.getLong("expires_at")
                if (expiresAt < Instant.now().toEpochMilli()) return@withConnection null
                SessionRecord(
                    token = result.getString("token"),
                    userId = result.getString("user_id"),
                    deviceId = result.getString("device_id"),
                )
            }
        }
    }

    fun insertEvents(userId: String, events: List<SyncEvent>): Int {
        var accepted = 0
        withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT OR IGNORE INTO events(user_id, event_id, device_id, chat_id, type, payload_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                for (event in events) {
                    statement.setString(1, userId)
                    statement.setString(2, event.eventId)
                    statement.setString(3, event.deviceId)
                    statement.setString(4, event.chatId)
                    statement.setString(5, event.type)
                    statement.setString(6, SyncJson.encode(event.payload))
                    statement.setLong(7, event.createdAtEpochMillis)
                    if (statement.executeUpdate() > 0) {
                        accepted += 1
                    }
                }
            }
        }
        return accepted
    }

    fun fetchEventsSince(userId: String, cursor: Long, limit: Int): List<EventRecord> {
        return withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, event_id, device_id, chat_id, type, payload_json, created_at
                FROM events
                WHERE user_id = ? AND id > ?
                ORDER BY id ASC
                LIMIT ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, userId)
                statement.setLong(2, cursor)
                statement.setInt(3, limit)
                val result = statement.executeQuery()
                val events = mutableListOf<EventRecord>()
                while (result.next()) {
                    events.add(
                        EventRecord(
                            cursor = result.getLong("id"),
                            event = SyncEvent(
                                eventId = result.getString("event_id"),
                                deviceId = result.getString("device_id"),
                                chatId = result.getString("chat_id"),
                                type = result.getString("type"),
                                payload = SyncJson.decode(result.getString("payload_json")),
                                createdAtEpochMillis = result.getLong("created_at"),
                            )
                        )
                    )
                }
                events
            }
        }
    }

    fun listNodes(userId: String): List<NodeInfo> {
        return withConnection { connection ->
            connection.prepareStatement(
                "SELECT node_id, name, address, last_seen FROM nodes WHERE user_id = ? ORDER BY updated_at DESC"
            ).use { statement ->
                statement.setString(1, userId)
                val result = statement.executeQuery()
                val nodes = mutableListOf<NodeInfo>()
                while (result.next()) {
                    nodes.add(
                        NodeInfo(
                            nodeId = result.getString("node_id"),
                            name = result.getString("name"),
                            address = result.getString("address"),
                            lastSeenEpochMillis = result.getLongOrNull("last_seen"),
                        )
                    )
                }
                nodes
            }
        }
    }

    fun upsertNode(userId: String, request: NodeUpsertRequest): NodeInfo {
        val nodeId = request.nodeId ?: UUID.randomUUID().toString()
        withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO nodes(node_id, user_id, name, address, last_seen, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(node_id) DO UPDATE SET
                    name = excluded.name,
                    address = excluded.address,
                    last_seen = excluded.last_seen,
                    updated_at = excluded.updated_at
                """.trimIndent()
            ).use { statement ->
                val now = Instant.now().toEpochMilli()
                statement.setString(1, nodeId)
                statement.setString(2, userId)
                statement.setString(3, request.name)
                statement.setString(4, request.address)
                statement.setLong(5, request.lastSeenEpochMillis ?: now)
                statement.setLong(6, now)
                statement.executeUpdate()
            }
        }
        return NodeInfo(
            nodeId = nodeId,
            name = request.name,
            address = request.address,
            lastSeenEpochMillis = request.lastSeenEpochMillis,
        )
    }

    fun upsertBlob(userId: String, request: PresignRequest, objectKey: String) {
        withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO blobs(blob_id, user_id, chat_id, sha256, size, mime, object_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(blob_id) DO UPDATE SET
                    object_key = excluded.object_key,
                    sha256 = excluded.sha256,
                    size = excluded.size,
                    mime = excluded.mime
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, request.blobId)
                statement.setString(2, userId)
                statement.setString(3, request.chatId)
                statement.setString(4, request.sha256)
                statement.setLong(5, request.size)
                statement.setString(6, request.mime)
                statement.setString(7, objectKey)
                statement.setLong(8, Instant.now().toEpochMilli())
                statement.executeUpdate()
            }
        }
    }

    fun findBlobKey(userId: String, blobId: String): String? {
        return withConnection { connection ->
            connection.prepareStatement(
                "SELECT object_key FROM blobs WHERE user_id = ? AND blob_id = ?"
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, blobId)
                val result = statement.executeQuery()
                if (result.next()) result.getString("object_key") else null
            }
        }
    }

    private fun createUser(connection: Connection): String {
        val userId = UUID.randomUUID().toString()
        connection.prepareStatement(
            "INSERT INTO users(user_id, created_at) VALUES (?, ?)"
        ).use { statement ->
            statement.setString(1, userId)
            statement.setLong(2, Instant.now().toEpochMilli())
            statement.executeUpdate()
        }
        return userId
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON;")
            }
            return block(connection)
        }
    }

    private fun ResultSet.getLongOrNull(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }
}

data class SessionRecord(
    val token: String,
    val userId: String,
    val deviceId: String,
)

data class EventRecord(
    val cursor: Long,
    val event: SyncEvent,
)

private object SyncJson {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(payload: EventPayloadEnvelope): String = json.encodeToString(EventPayloadEnvelope.serializer(), payload)

    fun decode(payload: String): EventPayloadEnvelope = json.decodeFromString(EventPayloadEnvelope.serializer(), payload)
}
