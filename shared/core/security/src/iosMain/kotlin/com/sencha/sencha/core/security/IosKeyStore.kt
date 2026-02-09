package com.sencha.sencha.core.security

import kotlinx.cinterop.CFTypeRefVar
import kotlinx.cinterop.memScoped
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

class IosKeyStore(
    private val service: String = "com.sencha.sencha",
) : KeyStore {
    override fun store(alias: KeyAlias, key: ByteArray) {
        val baseQuery = baseQuery(alias)
        SecItemDelete(baseQuery)
        val addQuery = baseQuery + mapOf(kSecValueData to key.toNSData())
        SecItemAdd(addQuery, null)
    }

    override fun load(alias: KeyAlias): ByteArray? {
        val query = baseQuery(alias) + mapOf(
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return null
            val data = result.value as? NSData ?: return null
            data.toByteArray()
        }
    }

    override fun delete(alias: KeyAlias): Boolean {
        val status = SecItemDelete(baseQuery(alias))
        return status == errSecSuccess || status == errSecItemNotFound
    }

    private fun baseQuery(alias: KeyAlias): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to service,
        kSecAttrAccount to alias.value,
    )
}

internal fun ByteArray.toNSData(): NSData = NSData.create(bytes = this, length = size.toULong())

internal fun NSData.toByteArray(): ByteArray {
    val length = CFDataGetLength(this)
    val buffer = ByteArray(length.toInt())
    val bytes = CFDataGetBytePtr(this)
    if (bytes != null) {
        for (index in 0 until buffer.size) {
            buffer[index] = bytes[index]
        }
    }
    return buffer
}
