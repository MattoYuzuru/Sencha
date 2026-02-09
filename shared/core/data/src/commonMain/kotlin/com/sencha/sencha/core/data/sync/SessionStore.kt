package com.sencha.sencha.core.data.sync

import com.sencha.sencha.core.security.KeyAlias
import com.sencha.sencha.core.security.KeyStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SessionInfo(
    val userId: String,
    val deviceId: String,
    val sessionToken: String,
)

interface SessionStore {
    fun load(): SessionInfo?
    fun save(info: SessionInfo)
    fun clear()
}

class KeyStoreSessionStore(
    private val keyStore: KeyStore,
    private val alias: KeyAlias = KeyAlias("sync.session"),
) : SessionStore {
    override fun load(): SessionInfo? {
        val data = keyStore.load(alias) ?: return null
        val json = data.decodeToString()
        return SyncJson.instance.decodeFromString(SessionInfo.serializer(), json)
    }

    override fun save(info: SessionInfo) {
        val json = SyncJson.instance.encodeToString(SessionInfo.serializer(), info)
        keyStore.store(alias, json.encodeToByteArray())
    }

    override fun clear() {
        keyStore.delete(alias)
    }
}
