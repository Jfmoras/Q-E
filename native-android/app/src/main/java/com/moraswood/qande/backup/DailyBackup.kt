package com.moraswood.qande.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moraswood.qande.ai.AiService
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.settings.RoomSqlPort
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Offline, once-per-day FULL JSON backup, including native Room tables, photos, attachments and
 * payment history. Android 10+ saves under Downloads/Moras Q-E Backups so backups survive
 * app uninstallation. Older devices use app-specific Documents (no broad storage permission).
 * Credentials are intentionally excluded using the existing QeBackup format.
 */
object DailyBackup {
    private const val PERIODIC = "qe-daily-full-backup"
    private const val STARTUP = "qe-startup-full-backup"
    private const val PREFS = "qe-device-backup"
    private const val LAST_DATE = "lastSuccessfulDate"
    private const val DIR = "Moras Q-E Backups/"

    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS).build())
        work.enqueueUniqueWork(STARTUP, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DailyBackupWorker>().build())
    }

    fun lastSuccessful(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_DATE, "") ?: ""

    fun location(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Downloads / Moras Q-E Backups"
        else (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "Unavailable")

    fun backup(context: Context): String {
        val app = context.applicationContext
        val today = LocalDate.now().toString()
        val pref = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (pref.getString(LAST_DATE, "") == today) return "Already backed up today"
        val room = QeDatabase.get(app)
        val tables = room.runInTransaction(java.util.concurrent.Callable { QeBackup.dumpTables(RoomSqlPort(room)) })
        val ai = AiService(app)
        val storage = QeBackup.storage(tables, QeBackup.AiExport(ai.endpoint(), ai.model(), ai.provider()))
        val files = QeBackup.FILE_DIRS.sorted().flatMap { folder ->
            (File(app.filesDir, folder).listFiles() ?: emptyArray()).filter { it.isFile }
                .sortedBy { it.name }.map { f -> QeBackup.BackupFile("$folder/${f.name}") { f.inputStream() } }
        }
        val name = "moras-qe-full-backup-$today.json"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DIR)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Device Downloads is unavailable")
            try {
                resolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                    QeBackup.write(it, Instant.now().toString(), storage, files)
                } ?: error("Could not write backup")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val folder = File(app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: error("Device Documents storage unavailable"), "Moras Q-E Backups").apply { mkdirs() }
            val temp = File(folder, "$name.tmp")
            try {
                temp.bufferedWriter(Charsets.UTF_8).use { QeBackup.write(it, Instant.now().toString(), storage, files) }
                if (!temp.renameTo(File(folder, name))) error("Could not finalize backup")
            } finally { temp.delete() }
        }
        pref.edit().putString(LAST_DATE, today).apply()
        return name
    }
}

class DailyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { DailyBackup.backup(applicationContext); Result.success() }
            catch (_: Exception) { Result.retry() }
        }
}
