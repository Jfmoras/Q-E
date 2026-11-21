package com.moraswood.qande.ops

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.moraswood.qande.data.OpsAttachmentEntity
import com.moraswood.qande.logic.QeOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Native replacement for the V10.1 IndexedDB `mwi-project-files-v1` store: each attachment is a private copy in
 * `filesDir/project-attachments/` plus an `ops_attachments` row. Not part of the JSON backup, like V10.1.
 */
object Attachments {
    private const val DIR = "project-attachments"

    /** `<input accept="image/(any),.pdf,.doc,.docx,.xls,.xlsx,.dxf,.stl,.3mf">` as SAF MIME types. */
    val ACCEPT = arrayOf(
        "image/*", "application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/dxf", "image/vnd.dxf", "model/stl", "application/sla", "model/3mf", "application/vnd.ms-package.3dmanufacturing-3dmodel+xml",
        "application/octet-stream",
    )

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }
    fun file(context: Context, a: OpsAttachmentEntity): File = File(dir(context), a.internalFileName)

    data class Picked(val uri: Uri, val name: String, val size: Long, val type: String)

    /** File name / size / MIME type as the browser `File` object reported them. */
    fun describe(context: Context, uri: Uri): Picked {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        return Picked(uri, name, size, context.contentResolver.getType(uri) ?: "")
    }

    class TooLarge : Exception()

    /** `store.put({id,projectId,name,type,size,at,blob})`. Throws [TooLarge] above 15 MB. */
    suspend fun store(context: Context, p: Picked, projectId: String, id: String, at: String): OpsAttachmentEntity = withContext(Dispatchers.IO) {
        if (p.size > QeOps.MAX_FILE) throw TooLarge()
        val internal = id
        val target = File(dir(context), internal)
        var copied = 0L
        try {
            context.contentResolver.openInputStream(p.uri).use { input ->
                requireNotNull(input) { "Cannot read ${p.name}" }
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        copied += n
                        if (copied > QeOps.MAX_FILE) throw TooLarge()
                        out.write(buf, 0, n)
                    }
                }
            }
        } catch (e: Exception) { target.delete(); throw e }
        OpsAttachmentEntity(id, projectId, p.name, p.type, if (p.size >= 0) p.size else copied, at, internal)
    }

    /** Feature A: copy an item photo (filesDir/item_photos) into the project as an image attachment. */
    suspend fun storePhoto(context: Context, photo: File, name: String, projectId: String, id: String, at: String): OpsAttachmentEntity? =
        withContext(Dispatchers.IO) {
            if (!photo.isFile) return@withContext null
            val target = File(dir(context), id)
            photo.copyTo(target, overwrite = true)
            OpsAttachmentEntity(id, projectId, name, "image/jpeg", target.length(), at, id)
        }

    suspend fun delete(context: Context, a: OpsAttachmentEntity) = withContext(Dispatchers.IO) { file(context, a).delete() }

    enum class Kind { Image, Pdf, Other }

    /** Same type test as the V10.1 preview panel. */
    fun kind(a: OpsAttachmentEntity): Kind {
        val type = a.type.lowercase(); val name = a.name.lowercase()
        return when {
            type.startsWith("image/") || Regex("\\.(png|jpe?g|gif|webp|bmp)$").containsMatchIn(name) -> Kind.Image
            type == "application/pdf" || name.endsWith(".pdf") -> Kind.Pdf
            else -> Kind.Other
        }
    }

    suspend fun decodeImage(context: Context, a: OpsAttachmentEntity, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(context, a)
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxPx) s *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
        }.getOrNull()
    }

    /** "Download / Open externally": hands the private copy to another app via FileProvider. */
    fun openExternally(context: Context, a: OpsAttachmentEntity): Boolean {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file(context, a))
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.type.ifEmpty { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(Intent.createChooser(intent, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        catch (_: ActivityNotFoundException) { false }
    }
}

/** PDF preview with android.graphics.pdf.PdfRenderer (replaces the V10.1 `<iframe>`; no WebView). */
class PdfPreview private constructor(private val fd: ParcelFileDescriptor, private val renderer: PdfRenderer) : Closeable {
    private val lock = Mutex()
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val w = widthPx.coerceIn(200, 2000)
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.getOrNull()
        }
    }

    override fun close() { runCatching { renderer.close() }; runCatching { fd.close() } }

    companion object {
        suspend fun open(file: File): PdfPreview? = withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try { PdfPreview(fd, PdfRenderer(fd)) } catch (e: Exception) { fd.close(); throw e }
            }.getOrNull()
        }
    }
}
