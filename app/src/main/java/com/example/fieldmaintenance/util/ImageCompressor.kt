package com.example.fieldmaintenance.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import kotlin.math.max

object ImageCompressor {
    private const val DEFAULT_MAX_BYTES = 1024 * 1024 // 1MB

    /**
     * Compresses the source image into [destFile] aiming for <= [maxBytes] while trying to keep a
     * WhatsApp-like resolution (long side around [maxDim]) and not shrinking below [minShortSide]
     * unless required.
     */
    @WorkerThread
    fun compressForExport(
        sourceFile: File,
        destFile: File,
        maxBytes: Int = DEFAULT_MAX_BYTES,
        maxDim: Int = 1600,
        minShortSide: Int = 900
    ) {
        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath)
            ?: throw IllegalArgumentException("No se pudo leer imagen: ${sourceFile.absolutePath}")

        val exifRot = readExifRotationDegrees(sourceFile)
        val oriented = if (exifRot != 0) rotateBitmap(decoded, exifRot) else decoded
        if (oriented !== decoded) decoded.recycle()

        // Force landscape: ensure the longer side is horizontal
        val original = if (oriented.height > oriented.width) rotateBitmap(oriented, 90) else oriented
        if (original !== oriented) oriented.recycle()

        // Prefer long-side = maxDim while trying to keep short side >= minShortSide if possible.
        var bitmap = downscalePreferMinShortSide(original, maxDim, minShortSide)
        if (bitmap !== original) original.recycle()

        // First: quality loop
        var quality = 90
        var bytes = compressJpegToBytes(bitmap, quality)

        // If still too big at low-ish quality, downscale progressively
        while (bytes.size > maxBytes) {
            if (quality > 45) {
                quality -= 10
                bytes = compressJpegToBytes(bitmap, quality)
            } else {
                // reduce resolution by 15%
                val w = (bitmap.width * 0.85).toInt().coerceAtLeast(800)
                val h = (bitmap.height * 0.85).toInt().coerceAtLeast(800)
                val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
                quality = 85
                bytes = compressJpegToBytes(bitmap, quality)

                // hard stop: accept best-effort once we're already quite small
                if (bitmap.width <= 900 || bitmap.height <= 900) break
            }
        }

        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { it.write(bytes) }
        bitmap.recycle()
    }

    private fun readExifRotationDegrees(file: File): Int {
        return runCatching {
            FileInputStream(file).use { fis ->
                val exif = ExifInterface(fis)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        }.getOrDefault(0)
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun downscalePreferMinShortSide(src: Bitmap, maxDim: Int, minShortSide: Int): Bitmap {
        val w0 = src.width
        val h0 = src.height
        val long0 = max(w0, h0)
        val short0 = minOf(w0, h0)

        if (long0 <= maxDim && short0 >= minShortSide) return src

        // scale so long side becomes maxDim (if larger), but do not scale up
        val scaleDown = if (long0 > maxDim) maxDim.toFloat() / long0.toFloat() else 1f
        var w = (w0 * scaleDown).toInt().coerceAtLeast(1)
        var h = (h0 * scaleDown).toInt().coerceAtLeast(1)

        // If short side falls below minShortSide and we didn't scale down much, keep original.
        val shortAfter = minOf(w, h)
        if (shortAfter < minShortSide && scaleDown == 1f) {
            // image is already small; keep as-is
            return src
        }
        // Otherwise accept this size (we may need further downscale for bytes later)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun compressJpegToBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 95), bos)
        return bos.toByteArray()
    }
}


