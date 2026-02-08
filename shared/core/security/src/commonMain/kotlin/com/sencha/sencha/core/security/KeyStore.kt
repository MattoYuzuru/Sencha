package com.sencha.sencha.core.security

data class KeyAlias(val value: String)

interface KeyStore {
    fun store(alias: KeyAlias, key: ByteArray)

    fun load(alias: KeyAlias): ByteArray?

    fun delete(alias: KeyAlias): Boolean
}

class InMemoryKeyStore : KeyStore {
    private val entries = mutableMapOf<KeyAlias, ByteArray>()

    override fun store(alias: KeyAlias, key: ByteArray) {
        entries[alias] = key.copyOf()
    }

    override fun load(alias: KeyAlias): ByteArray? = entries[alias]?.copyOf()

    override fun delete(alias: KeyAlias): Boolean = entries.remove(alias) != null
}
