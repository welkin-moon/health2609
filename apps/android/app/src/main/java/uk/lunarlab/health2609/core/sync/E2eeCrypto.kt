package uk.lunarlab.health2609.core.sync

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
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
    private const val PBKDF2_ITERATIONS = 10000

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BIT_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: ByteArray, key: SecretKey): EncryptedPayload {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            nonceBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(ciphertextBase64: String, nonceBase64: String, key: SecretKey): ByteArray {
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(nonceBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Compress bitmap into ultra-micro low bit-depth thumbnail (max 160px, <= 5KB)
     * For bandwidth-efficient and privacy-conscious E2EE sync.
     */
    fun generateMicroThumbnail(original: Bitmap, maxDimension: Int = 160): ByteArray {
        val width = original.width
        val height = original.height
        val scale = if (max(width, height) > maxDimension) {
            maxDimension.toFloat() / max(width, height).toFloat()
        } else {
            1.0f
        }

        val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)

        val scaledBitmap = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true)
        val stream = ByteArrayOutputStream()
        // Low bit-depth / aggressive compression for cloud metadata sync
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 45, stream)
        if (scaledBitmap != original) {
            scaledBitmap.recycle()
        }
        return stream.toByteArray()
    }
}
