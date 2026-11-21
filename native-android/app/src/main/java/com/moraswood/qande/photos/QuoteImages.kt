package com.moraswood.qande.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** V10.1 quotation item images: `resizeImage(file)` → max 720 px, `canvas.toDataURL('image/jpeg', .65)`. */
object QuoteImages {
    const val MAX_IMAGES = 6

    fun isImage(context: Context, uri: Uri): Boolean = context.contentResolver.getType(uri)?.startsWith("image/") == true

    /** Returns a `data:image/jpeg;base64,…` URL, or null when the file cannot be decoded. */
    suspend fun resize(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 720) sample *= 2
            val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching null
            // WebView draws <img> with EXIF orientation applied (image-orientation: from-image).
            val orientation = cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            }
            val oriented = if (m.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            // Scale is computed from the original (full-size, oriented) dimensions, like the JS canvas.
            val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
            val fullW = if (rotated) bounds.outHeight else bounds.outWidth
            val fullH = if (rotated) bounds.outWidth else bounds.outHeight
            val scale = minOf(1.0, 720.0 / maxOf(fullW, fullH))
            val w = maxOf(1, (fullW * scale).roundToInt()); val h = maxOf(1, (fullH * scale).roundToInt())
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(oriented, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            val bytes = ByteArrayOutputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 65, it); it.toByteArray() }
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Decodes a stored data URL for on-screen thumbnails. */
    fun decode(dataUrl: String, maxPx: Int = 0): Bitmap? = runCatching {
        val b64 = dataUrl.substringAfter("base64,", "")
        if (b64.isEmpty()) return null
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val opts = BitmapFactory.Options()
        if (maxPx > 0) {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxPx) s *= 2
            opts.inSampleSize = s
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}
