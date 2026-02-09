package com.sencha.sencha.core.security

import kotlinx.cinterop.CFTypeRefVar
import kotlinx.cinterop.memScoped
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Security.SecItemCopyMatching
import platform.Security.SecKey
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.errSecSuccess
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef

class IosDeviceIdentityProvider(
    private val tag: String = "com.sencha.device.key",
) : DeviceIdentityProvider {
    override fun loadOrCreate(): DeviceIdentity {
        val tagData = tag.encodeToByteArray().toNSData()
        val existing = loadKey(tagData)
        val key = existing ?: createKey(tagData)
        val publicKey = SecKeyCopyPublicKey(key)
            ?: error("Unable to load public key")
        val publicData = SecKeyCopyExternalRepresentation(publicKey, null) as? NSData
            ?: error("Unable to export public key")
        val publicKeyBytes = publicData.toByteArray()
        return DeviceIdentity(deviceIdFromPublicKey(publicKeyBytes), publicKeyBytes)
    }

    private fun loadKey(tagData: NSData): SecKey? = memScoped {
        val query = mapOf(
            kSecClass to kSecClassKey,
            kSecAttrKeyType to kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrApplicationTag to tagData,
            kSecReturnRef to kCFBooleanTrue,
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status == errSecSuccess) result.value as? SecKey else null
    }

    private fun createKey(tagData: NSData): SecKey {
        val attributes = mapOf(
            kSecAttrKeyType to kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits to 256,
            kSecPrivateKeyAttrs to mapOf(
                kSecAttrIsPermanent to kCFBooleanTrue,
                kSecAttrApplicationTag to tagData,
            ),
        )
        return SecKeyCreateRandomKey(attributes, null)
            ?: error("Unable to generate device key")
    }
}
