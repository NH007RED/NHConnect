package com.nhdeveloper.nhconnect.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** A compressed chat image ready for Realtime Database (base64 of [bytes] is written to chatMedia/...). */
class CompressedImage(val bytes: ByteArray, val thumb: ByteArray, val width: Int, val height: Int)

object ImageCompressor {
    const val CHAT_MAX_BYTES = 230_000     // ~307 KB as base64, below the 420 000 char rule limit
    const val PROFILE_MAX_BYTES = 45_000   // ~60 KB as base64, below the 90 000 char rule limit

    suspend fun chatImage(ctx: Context, uri: Uri): CompressedImage = withContext(Dispatchers.Default) {
        val src = decode(ctx, uri, 2048)
        var maxDim = 1024
        var quality = 75
        var bmp = scaleMax(src, maxDim)
        var bytes = jpeg(bmp, quality)
        while (bytes.size > CHAT_MAX_BYTES && (quality > 45 || maxDim > 480)) {
            if (quality > 45) quality -= 10 else { maxDim = (maxDim * 0.8f).toInt(); bmp = scaleMax(src, maxDim) }
            bytes = jpeg(bmp, quality)
        }
        val thumb = jpeg(scaleMax(bmp, 40), 50)
        CompressedImage(bytes, thumb, bmp.width, bmp.height)
    }

    /** Square, centre-cropped 256 px JPEG. */
    suspend fun profilePhoto(ctx: Context, uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val src = decode(ctx, uri, 1024)
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        var px = 256
        var q = 80
        var bytes = jpeg(Bitmap.createScaledBitmap(square, px, px, true), q)
        while (bytes.size > PROFILE_MAX_BYTES && (q > 45 || px > 128)) {
            if (q > 45) q -= 10 else px = (px * 0.8f).toInt()
            bytes = jpeg(Bitmap.createScaledBitmap(square, px, px, true), q)
        }
        bytes
    }

    private fun decode(ctx: Context, uri: Uri, maxDim: Int): Bitmap {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Cannot read this image")
        val orientation = try {
            cr.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) { android.media.ExifInterface.ORIENTATION_NORMAL }
        val m = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return raw
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    private fun scaleMax(b: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(b.width, b.height)
        if (longest <= maxDim) return b
        val f = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(b, maxOf(1, (b.width * f).toInt()), maxOf(1, (b.height * f).toInt()), true)
    }

    /** JPEG has no alpha: draw on white so transparent PNGs do not turn black. */
    private fun jpeg(b: Bitmap, quality: Int): ByteArray {
        val flat = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}

object B64 {
    fun enc(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun dec(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}

/** Decoded-bitmap memory cache limited to ~24 MB. */
object ImageCache {
    private val lru = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = lru.get(key)

    suspend fun decode(key: String, bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        lru.get(key) ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { lru.put(key, it) }
    }
}
