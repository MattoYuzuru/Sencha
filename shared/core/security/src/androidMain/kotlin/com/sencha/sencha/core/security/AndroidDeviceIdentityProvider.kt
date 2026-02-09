package com.sencha.sencha.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

class AndroidDeviceIdentityProvider(
    private val alias: String = "sencha.device.key",
) : DeviceIdentityProvider {
    override fun loadOrCreate(): DeviceIdentity {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(alias)) {
            generateKeyPair()
        }
        val certificate = keyStore.getCertificate(alias)
            ?: error("Missing device key certificate")
        val publicKeyBytes = certificate.publicKey.encoded
        return DeviceIdentity(deviceIdFromPublicKey(publicKeyBytes), publicKeyBytes)
    }

    private fun generateKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore",
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }
}
