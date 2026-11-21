package com.moraswood.qande.settings

import android.app.Application
import android.database.Cursor
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moraswood.qande.ai.AiService
import com.moraswood.qande.backup.QeBackup
import com.moraswood.qande.backup.SqlPort
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.logic.QeAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.Base64

/** Room-backed [SqlPort]; transactions go through Room so its invalidation tracker sees the changes. */
class RoomSqlPort(private val room: QeDatabase) : SqlPort {
    private val db: SupportSQLiteDatabase get() = room.openHelper.writableDatabase
    override fun query(sql: String): List<Map<String, Any?>> = db.query(sql).use { c ->
        buildList {
            while (c.moveToNext()) add((0 until c.columnCount).associate { i ->
                c.getColumnName(i) to when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(c.getBlob(i))
                    else -> c.getString(i)
                }
            })
        }
    }
    override fun exec(sql: String, args: List<Any?>) { if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray()) }
    override fun columns(table: String): List<String> = query("PRAGMA table_info(`$table`)").map { it["name"] as String }
    override fun <T> transaction(block: () -> T): T = room.runInTransaction<T> { block() }
}

/** AI form state (`#aiEndpoint`, `#aiModel`, `#aiApiKey`, `#aiDetectionStatus`, `#aiSettingsStatus`) + the `dataset.userEdited` flags. */
data class AiForm(
    val endpoint: String = QeAi.DEFAULT_ENDPOINT,
    val model: String = QeAi.DEFAULT_MODEL,
    val apiKey: String = "",
    val detection: String = QeAi.DETECTION_IDLE,
    val status: String = "",
    val endpointEdited: Boolean = false,
    val modelEdited: Boolean = false,
)

/** Settings milestone 7: Full Backup & Restore, AI API form, length converter (V10.1 `#settings`). */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val db = QeDatabase.get(app)
    private val ai = AiService(app)

    private val _ai = MutableStateFlow(AiForm())
    val aiForm: StateFlow<AiForm> = _ai.asStateFlow()
    /** app.js `aiSettings.provider` + `detectedAiProvider`. */
    private var provider = ""
    private var detected = ""

    private val _confirm = MutableStateFlow<String?>(null)
    val confirm: StateFlow<String?> = _confirm.asStateFlow()
    private val _alert = MutableStateFlow<String?>(null)
    val alert: StateFlow<String?> = _alert.asStateFlow()
    /** True once a restore committed: dismissing the alert restarts the app (`location.reload()`). */
    private val _reload = MutableStateFlow(false)
    val reload: StateFlow<Boolean> = _reload.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private var pending: Map<String, String>? = null

    /** `showTab('settings')` → `loadAiSettingsFields()`. */
    fun loadAiSettingsFields() {
        provider = ai.provider()
        detected = provider
        _ai.value = AiForm(
            endpoint = ai.endpoint().ifEmpty { QeAi.DEFAULT_ENDPOINT }, model = ai.model().ifEmpty { QeAi.DEFAULT_MODEL }, apiKey = "",
            detection = QeAi.savedText(provider), status = QeAi.statusText(ai.hasApiKey()),
        )
    }

    fun setEndpoint(v: String) { _ai.value = _ai.value.copy(endpoint = v, endpointEdited = true) }
    fun setModel(v: String) { _ai.value = _ai.value.copy(model = v, modelEdited = true) }

    /** `input` on `#aiApiKey` → `updateDetectedProvider(value)`. */
    fun setApiKey(v: String) {
        var f = _ai.value.copy(apiKey = v)
        val id = QeAi.detect(v)
        if (id.isEmpty()) { _ai.value = f.copy(detection = QeAi.DETECTION_IDLE); return }
        val untouched = !f.endpointEdited && !f.modelEdited
        if (id != detected) {
            provider = id; detected = id
            if (id != "generic" && untouched) {
                val p = QeAi.PROVIDERS.getValue(id)
                f = f.copy(endpoint = p.endpoint, model = p.model, endpointEdited = false, modelEdited = false)
            }
        }
        _ai.value = f.copy(detection = QeAi.detectionText(v))
    }

    fun saveAiSettings() {
        val f = _ai.value
        val endpoint = f.endpoint.trim(); val model = f.model.trim(); val key = f.apiKey.trim()
        QeAi.validate(endpoint, model)?.let { _ai.value = f.copy(status = it); return }
        val prov = if (key.isNotEmpty()) QeAi.detect(key) else provider.ifEmpty { "generic" }
        val error = ai.saveSettings(endpoint, model, key, prov)
        if (error.isNotEmpty()) { _ai.value = f.copy(status = error); return }
        provider = prov
        _ai.value = f.copy(apiKey = "", status = "AI settings saved on this device.")
    }

    fun removeAiKey() {
        val error = ai.clearApiKey()
        if (error.isNotEmpty()) { _ai.value = _ai.value.copy(status = error); return }
        provider = ""
        _ai.value = _ai.value.copy(apiKey = "", detection = QeAi.DETECTION_IDLE, status = "API key removed from this device.")
    }

    private fun backupFiles(): List<QeBackup.BackupFile> = QeBackup.FILE_DIRS.sorted().flatMap { dir ->
        (File(ctx.filesDir, dir).listFiles() ?: emptyArray()).filter { it.isFile && it.length() > 0 }.sortedBy { it.name }
            .map { f -> QeBackup.BackupFile("$dir/${f.name}") { f.inputStream() } }
    }

    private fun writeBackup(w: java.io.Writer) {
        val tables = QeBackup.dumpTables(RoomSqlPort(db))
        val storage = QeBackup.storage(tables, QeBackup.AiExport(ai.endpoint(), ai.model(), ai.provider()))
        QeBackup.write(w, Instant.now().toString(), storage, backupFiles())
    }

    /** "Backup" → CreateDocument("moras-qe-full-backup.json") → one portable file (all tables + base64 photos/PDFs/files; no API key). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use(::writeBackup) ?: error("Could not open the file.")
                }
                Toast.makeText(ctx, "Backup exported.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Backup export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally { _busy.value = false }
        }
    }

    /** "Share backup" → the same file written to app cache, then ACTION_SEND through the FileProvider. */
    fun share() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "backup").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    File(dir, QeBackup.FILE_NAME).also { f -> f.bufferedWriter(Charsets.UTF_8).use(::writeBackup) }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(android.content.Intent.createChooser(send, "Share backup").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Backup share failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally { _busy.value = false }
        }
    }

    /** `#backupImport` → OpenDocument → `QEImportBackupData(content)`. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val content = try {
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Could not open the file.") }
            } catch (e: Throwable) {
                Toast.makeText(ctx, "Backup import failed: ${e.message}", Toast.LENGTH_LONG).show(); return@launch
            }
            try {
                pending = withContext(Dispatchers.Default) { QeBackup.parse(content) }
                _confirm.value = QeBackup.CONFIRM
            } catch (e: Exception) {
                _alert.value = "Restore failed: " + e.message
            }
        }
    }

    fun onConfirm(ok: Boolean) {
        _confirm.value = null
        val storage = pending ?: return
        pending = null
        if (!ok) return
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    val r = QeBackup.restore(RoomSqlPort(db), storage)
                    val dec = Base64.getDecoder()
                    r.files.forEach { (path, b64) ->
                        val f = File(ctx.filesDir, path)
                        f.parentFile?.mkdirs()
                        f.writeBytes(dec.decode(b64))
                    }
                    r.orphanAttachmentFiles.forEach { File(File(ctx.filesDir, "project-attachments"), it).delete() }
                    r.ai?.let { a -> if (QeAi.validate(a.endpoint.trim(), a.model.trim()) == null) ai.saveSettings(a.endpoint, a.model, "", a.provider.ifEmpty { "openai" }) }
                }
                _reload.value = true
                _alert.value = QeBackup.RESTORED
            } catch (e: Exception) {
                _alert.value = "Restore failed: " + e.message
            } finally { _busy.value = false }
        }
    }

    fun dismissAlert() { _alert.value = null }
}
