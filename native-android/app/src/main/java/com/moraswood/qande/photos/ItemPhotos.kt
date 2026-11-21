package com.moraswood.qande.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.moraswood.qande.data.ItemPhotoEntity
import com.moraswood.qande.data.QeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

/** One photo per calculator/estimation item, stored privately in filesDir/item_photos/. */
object ItemPhotos {
    const val DIR = "item_photos"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }
    fun file(context: Context, name: String): File = File(dir(context), name)

    /** New empty file + FileProvider URI for ActivityResultContracts.TakePicture. */
    fun newCaptureTarget(context: Context): Pair<String, Uri> {
        val name = "item-" + UUID.randomUUID().toString() + ".jpg"
        val f = file(context, name)
        f.createNewFile()
        return name to FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    /** Copies a picked image (PickVisualMedia, no storage permission) into the private folder. */
    suspend fun importFrom(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = "item-" + UUID.randomUUID().toString() + ".jpg"
            val target = file(context, name)
            context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
            if (target.length() == 0L) { target.delete(); null } else name
        }.getOrNull()
    }

    /** Feature N: copies an existing photo to a new file; null when the source is missing. */
    suspend fun copyOf(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (name.contains('/') || name.contains("..")) return@runCatching null
            val src = file(context, name)
            if (!src.exists()) return@runCatching null
            val copy = "item-" + UUID.randomUUID().toString() + "." + src.extension.ifEmpty { "jpg" }
            src.copyTo(file(context, copy))
            copy
        }.getOrNull()
    }

    suspend fun register(context: Context, db: QeDatabase, name: String) = withContext(Dispatchers.IO) {
        val f = file(context, name)
        if (f.exists()) runCatching { db.itemPhotos().put(ItemPhotoEntity(name, Instant.now().toString(), f.length())) }
    }

    suspend fun delete(context: Context, db: QeDatabase, name: String) = withContext(Dispatchers.IO) {
        if (name.contains('/') || name.contains("..")) return@withContext
        file(context, name).delete()
        runCatching { db.itemPhotos().delete(name) }
    }

    /** Downsampled, EXIF-rotated thumbnail (no image library needed). */
    suspend fun thumbnail(context: Context, name: String, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(context, name)
            if (!f.exists() || f.length() == 0L) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            val degrees = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        }.getOrNull()
    }
}
