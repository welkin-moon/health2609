package uk.lunarlab.health2609.core.sync

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.roundToInt

data class EncryptedPayload(
    val ciphertextBase64: String,
    val nonceBase64: String
)

object E2eeCrypto {
    private const val AES_KEY_BIT_LENGTH = 256
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_BIT_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 210_000

    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(secureRandom::nextBytes)

    fun generateSalt(): ByteArray = randomBytes(16)

    fun generateAccountKey(): SecretKey =
        SecretKeySpec(randomBytes(32), "AES")

    fun generateRecoveryPhrase(): String {
        val hex = randomBytes(20).joinToString("") { "%02X".format(it) }
        return hex.chunked(5).joinToString("-")
    }

    fun normalizeRecoveryPhrase(value: String): String =
        value.uppercase().filter { it.isLetterOrDigit() }

    private fun purposeSalt(salt: ByteArray, purpose: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(0.toByte())
        digest.update("health2609:$purpose:v1".toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    fun deriveKey(secret: String, salt: ByteArray, purpose: String): SecretKey {
        val spec = PBEKeySpec(
            secret.toCharArray(),
            purposeSalt(salt, purpose),
            PBKDF2_ITERATIONS,
            AES_KEY_BIT_LENGTH
        )
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun deriveAuthVerifier(secret: String, salt: ByteArray, purpose: String): String {
        val key = deriveKey(secret, salt, "$purpose-auth")
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun deriveWrappingKey(secret: String, salt: ByteArray, purpose: String): SecretKey =
        deriveKey(secret, salt, "$purpose-wrap")

    fun encrypt(
        plaintext: ByteArray,
        key: SecretKey,
        aad: String? = null
    ): EncryptedPayload {
        val iv = randomBytes(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv))
        if (aad != null) cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            nonceBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(
        ciphertextBase64: String,
        nonceBase64: String,
        key: SecretKey,
        aad: String? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(
                GCM_TAG_BIT_LENGTH,
                Base64.decode(nonceBase64, Base64.NO_WRAP)
            )
        )
        if (aad != null) cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(Base64.decode(ciphertextBase64, Base64.NO_WRAP))
    }

    fun keyFromBase64(value: String): SecretKey =
        SecretKeySpec(Base64.decode(value, Base64.NO_WRAP), "AES")

    fun keyToBase64(key: SecretKey): String =
        Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun base64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun unbase64(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    fun buildKeyAad(username: String, epoch: Int, kind: String): String =
        "health2609|key|v1|$username|$epoch|$kind"

    fun buildRecordAad(
        entityType: String,
        entityId: String,
        envelopeVersion: Int,
        keyEpoch: Int,
        revision: Long,
        clientUpdatedAt: String
    ): String = listOf(
        "health2609",
        "record",
        "v$envelopeVersion",
        "epoch=$keyEpoch",
        "type=$entityType",
        "id=$entityId",
        "revision=$revision",
        "updated=$clientUpdatedAt"
    ).joinToString("|")

    /**
     * Optional cross-device preview only. The full-resolution original is never
     * part of the sync payload.
     */
    fun generateMicroThumbnail(original: Bitmap, maxDimension: Int = 192): ByteArray {
        val scale = if (max(original.width, original.height) > maxDimension) {
            maxDimension.toFloat() / max(original.width, original.height).toFloat()
        } else {
            1f
        }
        val scaled = Bitmap.createScaledBitmap(
            original,
            (original.width * scale).roundToInt().coerceAtLeast(1),
            (original.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        return ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 38, stream)
            if (scaled !== original) scaled.recycle()
            stream.toByteArray()
        }
    }
}
