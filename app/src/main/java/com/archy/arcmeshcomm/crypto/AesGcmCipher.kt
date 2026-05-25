package com.archy.arcmeshcomm.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val nonce: String,
    val cipherText: String,
    val checksum: String
)

class AesGcmCipher(passphrase: String) {
    private val random = SecureRandom()
    private val key = SecretKeySpec(sha256(passphrase.toByteArray()), "AES")

    fun encrypt(plainText: String): EncryptedPayload {
        val nonce = ByteArray(NONCE_BYTES)
        random.nextBytes(nonce)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val cipherText = Base64.getEncoder().encodeToString(encrypted)
        return EncryptedPayload(
            nonce = Base64.getEncoder().encodeToString(nonce),
            cipherText = cipherText,
            checksum = checksum(cipherText)
        )
    }

    fun decrypt(payload: EncryptedPayload): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(payload.nonce))
        )
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(payload.cipherText))
        return decrypted.toString(Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128

        fun checksum(value: String): String {
            return sha256(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(12)
        }

        private fun sha256(bytes: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(bytes)
        }
    }
}
