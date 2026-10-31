package com.frostlock.remote.data

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import java.security.spec.MGF1ParameterSpec

internal object PairingCodeCrypto {
    private const val PREFIX = "FL1."
    private const val PRIVATE_KEY_BASE64 =
        "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"

    fun decrypt(code: String): String {
        require(code.startsWith(PREFIX)) { "Pairing code is not in the encrypted FrostLock format." }
        val encoded = code.substring(PREFIX.length)
        require(encoded.matches(Regex("[A-Za-z0-9_-]{512}"))) { "Pairing code is malformed or incomplete." }
        try {
            val ciphertext = Base64.getUrlDecoder().decode(encoded)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_KEY_BASE64)),
            )
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                privateKey,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT),
            )
            return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalArgumentException("Could not decrypt this pairing code. Create a new one in FrostLock for Windows.")
        }
    }
}
