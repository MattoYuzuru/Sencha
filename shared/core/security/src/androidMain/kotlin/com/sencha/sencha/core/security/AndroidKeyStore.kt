package com.sencha.sencha.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore as AndroidKeyStoreApi
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeyStore(context: Context) : KeyStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sencha_secure_store", Context.MODE_PRIVATE)
    private val masterKeyAlias = "sencha.master.key"

    override fun store(alias: KeyAlias, key: ByteArray) {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(key)
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        prefs.edit().putString(alias.value, encoded).apply()
    }

    override fun load(alias: KeyAlias): ByteArray? {
        val encoded = prefs.getString(alias.value, null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        if (payload.isEmpty()) return null
        val ivSize = payload[0].toInt() and 0xFF
        if (payload.size < 1 + ivSize) return null
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val encrypted = payload.copyOfRange(1 + ivSize, payload.size)
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    override fun delete(alias: KeyAlias): Boolean {
        val existed = prefs.contains(alias.value)
        prefs.edit().remove(alias.value).apply()
        return existed
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = AndroidKeyStoreApi.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existing = keyStore.getKey(masterKeyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            masterKeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
