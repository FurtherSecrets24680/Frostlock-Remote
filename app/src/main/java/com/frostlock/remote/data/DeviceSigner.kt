package com.frostlock.remote.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.Base64

class DeviceSigner {
    fun create(alias: String): String {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        val pair = generator.generateKeyPair()
        return Base64.getEncoder().encodeToString(pair.public.encoded)
    }

    fun sign(alias: String, message: ByteArray): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey
            ?: error("This phone's security key is unavailable. Remove and pair the PC again.")
        return Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").run { initSign(privateKey); update(message); sign() },
        )
    }

    fun delete(alias: String) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
