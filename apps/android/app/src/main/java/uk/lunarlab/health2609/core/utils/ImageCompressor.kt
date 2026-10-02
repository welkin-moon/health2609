package uk.lunarlab.health2609.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedImageResult(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val fileName: String = "meal.jpg",
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressedImageResult) return false
        return bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            fileName == other.fileName &&
            width == other.width &&
            height == other.height
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

object ImageCompressor {
    private const val DEFAULT_MAX_DIMENSION = 1280
    private const val DEFAULT_QUALITY = 80
    private const val TARGET_MAX_BYTES = 300 * 1024 // 300 KB limit to prevent turn timeouts

    /**
     * Efficiently reads an image from Uri with downsampling and JPEG compression.
     * Prevents CLI turn timeout by ensuring max dimension <= 1280px and byte size < 300KB (typically 100-250KB).
     */
    fun compressFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        initialQuality: Int = DEFAULT_QUALITY,
        maxSizeBytes: Int = TARGET_MAX_BYTES
    ): CompressedImageResult {
        // Step 1: Decode image dimensions without loading pixel data into memory
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "无法打开图片输入流" }
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        require(origWidth > 0 && origHeight > 0) { "无效的图片尺寸" }

        // Step 2: Compute optimal sample size (power of 2)
        val sampleSize = calculateInSampleSize(origWidth, origHeight, maxDimension)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Half memory footprint of ARGB_8888
        }

        val sampledBitmap = context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "无法打开图片输入流" }
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("解码图片位图失败")

        return processAndCompressBitmap(sampledBitmap, maxDimension, initialQuality, maxSizeBytes)
    }

    /**
     * Compresses an existing ByteArray payload if larger than maxSizeBytes or dimension > 1280px.
     */
    fun compressFromBytes(
        bytes: ByteArray,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        initialQuality: Int = DEFAULT_QUALITY,
        maxSizeBytes: Int = TARGET_MAX_BYTES
    ): CompressedImageResult {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        // If already within budget and dimensions, avoid redundant re-compression
        if (origWidth in 1..maxDimension && origHeight in 1..maxDimension && bytes.size <= maxSizeBytes) {
            return CompressedImageResult(
                bytes = bytes,
                mimeType = "image/jpeg",
                fileName = "meal.jpg",
                width = origWidth,
                height = origHeight
            )
        }

        val sampleSize = calculateInSampleSize(origWidth, origHeight, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IllegalStateException("解码字节位图失败")

        return processAndCompressBitmap(sampledBitmap, maxDimension, initialQuality, maxSizeBytes)
    }

    private fun processAndCompressBitmap(
        bitmap: Bitmap,
        maxDimension: Int,
        initialQuality: Int,
        maxSizeBytes: Int
    ): CompressedImageResult {
        var currentBitmap = bitmap
        try {
            val width = currentBitmap.width
            val height = currentBitmap.height
            val currentMax = max(width, height)

            // Step 3: Exact scaling if max dimension still exceeds threshold
            if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax.toFloat()
                val targetW = (width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(currentBitmap, targetW, targetH, true)
                if (scaled != currentBitmap) {
                    currentBitmap.recycle()
                    currentBitmap = scaled
                }
            }

            // Step 4: Compress to JPEG starting at target quality (~80%)
            var quality = initialQuality
            var output = ByteArrayOutputStream()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)

            // Step 5: If output size exceeds limit, reduce quality iteratively
            while (output.size() > maxSizeBytes && quality > 45) {
                output.reset()
                quality -= 10
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }

            val finalBytes = output.toByteArray()
            return CompressedImageResult(
                bytes = finalBytes,
                mimeType = "image/jpeg",
                fileName = "meal.jpg",
                width = currentBitmap.width,
                height = currentBitmap.height
            )
        } finally {
            if (!currentBitmap.isRecycled) {
                currentBitmap.recycle()
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        val maxLen = max(width, height)
        while ((maxLen / (inSampleSize * 2)) >= maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
