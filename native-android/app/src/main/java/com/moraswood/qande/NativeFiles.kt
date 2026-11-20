package com.moraswood.qande

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class NativeFiles(private val context: Context, private val dao: MwiDao) {
    suspend fun save(projectId: String, uri: Uri) {
        val id = UUID.randomUUID().toString()
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Attachment"
        val dir = File(context.filesDir, "project-attachments").apply { mkdirs() }
        val target = File(dir, id)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read selected document")
            dao.putAttachment(AttachmentEntity(id, projectId, name, mime, id))
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }
    fun viewIntent(attachment: AttachmentEntity): Intent {
        val file = File(File(context.filesDir, "project-attachments"), attachment.internalFileName)
        require(file.isFile) { "Attachment file missing" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
