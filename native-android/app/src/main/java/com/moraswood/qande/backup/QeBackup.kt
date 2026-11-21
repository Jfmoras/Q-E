package com.moraswood.qande.backup

import com.moraswood.qande.data.QeMigrations
import com.moraswood.qande.data.StorageKeys
import com.moraswood.qande.logic.QeOps
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.Writer
import java.util.Base64

/** Minimal SQL surface so the whole backup/restore runs unchanged on Android (SupportSQLiteDatabase) and in JVM tests (sqlite-jdbc). */
interface SqlPort {
    /** Rows as column -> Long | Double | String | null. */
    fun query(sql: String): List<Map<String, Any?>>
    fun exec(sql: String, args: List<Any?> = emptyList())
    fun columns(table: String): List<String>
    fun <T> transaction(block: () -> T): T
}

/**
 * Milestone 7 — Full Backup & Restore, V10.1 format (`app.js exportBackup` / `QEImportBackupData`):
 * `{format:'moras-qe-backup',version:1,date,storage}` where every storage key starts with `mwi-` and every value is a string.
 *
 * - The V10.1 keys (`mwi-projects-v1`, `mwi-saved-notes-v1`, `mwi-operations-v1`, `mwi-autosave-v1`, `mwi-quick-calc-v1`,
 *   `mwi-hoppus-v1`, `mwi-ai-settings-v1` without the key, ...) are rebuilt from Room, so the file still restores in V10.1.
 * - Native additions ride along as two extra `mwi-` string keys (V10.1 validation accepts them):
 *   `mwi-native-db-v1`  = every Room table dumped row-by-row (templates, component log, photos, asset logs, machine history...)
 *   `mwi-native-files-v1` = `{"item_photos/<f>":"<base64>","project-attachments/<f>":"<base64>"}` (photos + project files).
 * - Restore: with `mwi-native-db-v1` the whole database is replaced exactly; a plain V10.1 file replaces only the
 *   tables behind the keys it contains (V10.1 `localStorage.setItem` semantics) and keeps everything else.
 */
@OptIn(ExperimentalSerializationApi::class)
object QeBackup {
    const val FORMAT = "moras-qe-backup"
    const val FILE_NAME = "moras-qe-full-backup.json"
    const val NATIVE_DB = "mwi-native-db-v1"
    const val NATIVE_FILES = "mwi-native-files-v1"
    const val LEGACY_PLAIN_KEY = "mwi-openai-api-key-v1"
    const val CONFIRM = "Replace existing saved data with backup?"
    const val RESTORED = "Restored. Reloading."
    const val SCHEMA = 3
    val FILE_DIRS = setOf("item_photos", "project-attachments")

    /** Parents before children (insert order); deletes run in reverse. */
    val TABLES = listOf(
        "kv_store", "saved_projects", "saved_notes",
        "ops_projects", "ops_project_entries", "ops_attachments", "ops_materials", "ops_movements",
        "ops_machines", "ops_machine_history", "ops_audit",
        "job_component_log", "templates", "template_rows", "item_photos",
        "assets", "asset_issues", "asset_returns", "asset_corrections", "asset_correction_decisions",
    )
    private val ORDER_BY = mapOf(
        "saved_projects" to "position", "saved_notes" to "position", "ops_projects" to "position", "ops_materials" to "position",
        "ops_movements" to "position", "ops_machines" to "position", "kv_store" to "`key`", "item_photos" to "fileName",
        "ops_attachments" to "id",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ------------------------------------------------------------------ export

    fun dumpTables(port: SqlPort): Map<String, JsonArray> = TABLES.associateWith { t ->
        val order = ORDER_BY[t] ?: "rowid"
        JsonArray(port.query("SELECT * FROM `$t` ORDER BY $order").map { row -> JsonObject(row.mapValues { (_, v) -> toJson(v) }) })
    }

    private fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v.toLong())
        is Double -> JsonPrimitive(v)
        is Float -> JsonPrimitive(v.toDouble())
        is Boolean -> JsonPrimitive(if (v) 1L else 0L)
        else -> JsonPrimitive(v.toString())
    }

    /** `mwi-ai-settings-v1` as V10.1 exports it (`delete settings.apiKey`). */
    data class AiExport(val endpoint: String, val model: String, val provider: String)

    /** The storage map: V10.1 keys first (rebuilt from the tables), then the two native keys. */
    fun storage(tables: Map<String, JsonArray>, ai: AiExport?): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        rows(tables, "kv_store").forEach { r ->
            val k = str(r, "key")
            if (k.startsWith("mwi-") && k != StorageKeys.AI_SETTINGS && k != LEGACY_PLAIN_KEY && k != NATIVE_DB && k != NATIVE_FILES) out[k] = str(r, "json")
        }
        out[StorageKeys.PROJECTS] = JsonArray(rows(tables, "saved_projects").map { r ->
            buildJsonObject {
                put("id", str(r, "id")); put("rootId", str(r, "rootId")); put("revision", lng(r, "revision"))
                put("name", str(r, "name")); put("updatedAt", str(r, "updatedAt"))
                put("snapshot", runCatching { json.parseToJsonElement(str(r, "snapshotJson")) }.getOrElse { JsonPrimitive(str(r, "snapshotJson")) })
            }
        }).toString()
        out[StorageKeys.SAVED_NOTES] = JsonArray(rows(tables, "saved_notes").map { JsonPrimitive(str(it, "text")) }).toString()
        out[StorageKeys.OPERATIONS] = operationsJson(tables).toString()
        if (ai != null) out[StorageKeys.AI_SETTINGS] = buildJsonObject { put("endpoint", ai.endpoint); put("model", ai.model); put("provider", ai.provider) }.toString()
        out[NATIVE_DB] = buildJsonObject {
            put("schema", SCHEMA)
            put("tables", JsonObject(tables))
        }.toString()
        return out
    }

    private fun jsNum(d: Double): JsonElement = JsonUnquotedLiteral(QeOps.jsString(d))

    /** `mwi-operations-v1` exactly as operations.js `save()` writes it. */
    fun operationsJson(tables: Map<String, JsonArray>): JsonObject {
        val entries = rows(tables, "ops_project_entries").groupBy { str(it, "projectId") }
        fun list(id: String, kind: String) = JsonArray(entries[id].orEmpty().filter { str(it, "kind") == kind }.map {
            buildJsonObject { put("amount", jsNum(dbl(it, "amount"))); put("note", str(it, "note")); put("at", str(it, "at")) }
        })
        val history = rows(tables, "ops_machine_history").groupBy { str(it, "machineId") }
        return buildJsonObject {
            put("projects", JsonArray(rows(tables, "ops_projects").map { p ->
                val id = str(p, "id")
                buildJsonObject {
                    put("id", id); put("name", str(p, "name")); put("customer", str(p, "customer")); put("original", jsNum(dbl(p, "original")))
                    put("status", str(p, "status"))
                    put("payments", list(id, "payment")); put("revisions", list(id, "revision")); put("expenses", list(id, "expense"))
                    put("notes", str(p, "notes")); put("created", str(p, "created"))
                }
            }))
            put("materials", JsonArray(rows(tables, "ops_materials").map { m ->
                buildJsonObject {
                    put("id", str(m, "id")); put("name", str(m, "name")); put("unit", str(m, "unit")); put("category", str(m, "category"))
                    put("rate", jsNum(dbl(m, "rate"))); put("min", jsNum(dbl(m, "min")))
                }
            }))
            put("movements", JsonArray(rows(tables, "ops_movements").map { m ->
                buildJsonObject {
                    put("id", str(m, "id")); put("materialId", str(m, "materialId")); put("qty", jsNum(dbl(m, "qty"))); put("type", str(m, "type"))
                    put("worker", str(m, "worker")); put("projectId", str(m, "projectId")); put("rate", jsNum(dbl(m, "rate"))); put("at", str(m, "at"))
                }
            }))
            put("machines", JsonArray(rows(tables, "ops_machines").map { m ->
                val id = str(m, "id")
                buildJsonObject {
                    put("id", id); put("name", str(m, "name")); put("serial", str(m, "serial")); put("worker", str(m, "worker")); put("status", str(m, "status"))
                    put("history", JsonArray(history[id].orEmpty().map { h -> buildJsonObject { put("at", str(h, "at")); put("status", str(h, "status")); put("worker", str(h, "worker")) } }))
                }
            }))
            put("audit", JsonArray(rows(tables, "ops_audit").map { a ->
                buildJsonObject {
                    put("at", str(a, "at")); put("type", str(a, "type")); put("id", str(a, "refId"))
                    (a["value"] as? JsonPrimitive)?.contentOrNull?.let { put("value", it) }
                    (a["name"] as? JsonPrimitive)?.contentOrNull?.let { put("name", it) }
                }
            }))
        }
    }

    /** A file to embed: `path` = "<dir>/<name>". */
    class BackupFile(val path: String, val open: () -> InputStream)

    /** `JSON.stringify({format,version,date,storage},null,2)`, streamed so photos never sit in memory twice. */
    fun write(out: Writer, date: String, storage: Map<String, String>, files: List<BackupFile>) {
        out.write("{\n  \"format\": \"$FORMAT\",\n  \"version\": 1,\n  \"date\": ${quote(date)},\n  \"storage\": {")
        var first = true
        for ((k, v) in storage) {
            out.write(if (first) "\n" else ",\n"); first = false
            out.write("    "); out.write(quote(k)); out.write(": "); out.write(quote(v))
        }
        if (files.isNotEmpty()) {
            out.write(if (first) "\n" else ",\n"); first = false
            out.write("    "); out.write(quote(NATIVE_FILES)); out.write(": \"{")
            files.forEachIndexed { i, f ->
                if (i > 0) out.write(",")
                val inner = quote(f.path) // inner JSON string, then escaped again for the outer string
                out.write(quote(inner).let { it.substring(1, it.length - 1) })
                out.write(":\\\"")
                f.open().use { input -> streamBase64(input, out) }
                out.write("\\\"")
            }
            out.write("}\"")
        }
        out.write(if (first) "}\n}" else "\n  }\n}")
        out.flush()
    }

    private fun streamBase64(input: InputStream, out: Writer) {
        val enc = Base64.getEncoder()
        val buf = ByteArray(3 * 16 * 1024)
        while (true) {
            var n = 0
            while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
            if (n == 0) break
            out.write(enc.encodeToString(if (n == buf.size) buf else buf.copyOf(n)))
            if (n < buf.size) break
        }
    }

    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b"); '\u000C' -> sb.append("\\f")
            else -> if (c < ' ') sb.append("\\u").append(String.format("%04x", c.code)) else sb.append(c)
        }
        return sb.append('"').toString()
    }

    // ------------------------------------------------------------------ import

    /** `QEImportBackupData` validation; throws with the V10.1 messages ("Invalid file" / "Invalid data"). */
    fun parse(content: String): LinkedHashMap<String, String> {
        val root = try { json.parseToJsonElement(content) } catch (e: Exception) { throw IllegalArgumentException("The file is not valid JSON.") }
        val b = root as? JsonObject ?: throw IllegalArgumentException("Invalid file")
        val format = (b["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val version = (b["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        val st = b["storage"]
        if (format != FORMAT || version != 1.0 || st == null || st is JsonNull || st is JsonPrimitive) throw IllegalArgumentException("Invalid file")
        if (st is JsonArray) { if (st.isEmpty()) return LinkedHashMap() else throw IllegalArgumentException("Invalid data") }
        val out = LinkedHashMap<String, String>()
        for ((k, v) in st as JsonObject) {
            if (!k.startsWith("mwi-") || v !is JsonPrimitive || !v.isString) throw IllegalArgumentException("Invalid data")
            out[k] = v.content
        }
        return out
    }

    /** What the Android side still has to do after the database transaction. */
    data class RestoreResult(
        /** "<dir>/<name>" -> bytes to write (already path-checked). */
        val files: Map<String, String>,
        /** endpoint/model/provider to apply (never a key). */
        val ai: AiExport?,
        /** project-attachment files whose rows are gone (V10.1-only restore dropped their project). */
        val orphanAttachmentFiles: List<String>,
        val nativeRestore: Boolean,
    )

    fun safePath(path: String): Boolean {
        val parts = path.split('/')
        return parts.size == 2 && parts[0] in FILE_DIRS && parts[1].isNotEmpty() && parts[1] != "." && parts[1] != ".." && !parts[1].contains('\\')
    }

    fun restore(port: SqlPort, storage: Map<String, String>): RestoreResult = port.transaction {
        val orphans = mutableListOf<String>()
        val nativeDb = storage[NATIVE_DB]
        val derived = setOf(StorageKeys.PROJECTS, StorageKeys.SAVED_NOTES, StorageKeys.OPERATIONS, StorageKeys.AI_SETTINGS, LEGACY_PLAIN_KEY, NATIVE_DB, NATIVE_FILES)
        if (nativeDb != null) {
            val obj = json.parseToJsonElement(nativeDb) as JsonObject
            val tables = obj["tables"] as? JsonObject ?: throw IllegalArgumentException("Invalid data")
            QeMigrations.ASSET_TRIGGER_DROPS.forEach { port.exec(it) }
            TABLES.reversed().forEach { port.exec("DELETE FROM `$it`") }
            for (t in TABLES) insertRows(port, t, (tables[t] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject })
            QeMigrations.ASSET_TRIGGERS.forEach { port.exec(it) }
            val inDump = rowsOf(tables, "kv_store").map { str(it, "key") }.toSet()
            storage.filterKeys { it !in derived && it !in inDump }.forEach { (k, v) -> port.exec("INSERT OR REPLACE INTO kv_store(`key`, json) VALUES (?, ?)", listOf(k, v)) }
        } else {
            storage[StorageKeys.PROJECTS]?.let { restoreProjects(port, it) }
            storage[StorageKeys.SAVED_NOTES]?.let { restoreNotes(port, it) }
            storage[StorageKeys.OPERATIONS]?.let { orphans += restoreOperations(port, it) }
            storage.filterKeys { it !in derived }.forEach { (k, v) -> port.exec("INSERT OR REPLACE INTO kv_store(`key`, json) VALUES (?, ?)", listOf(k, v)) }
        }
        val ai = storage[StorageKeys.AI_SETTINGS]?.let { raw ->
            runCatching {
                val o = json.parseToJsonElement(raw) as JsonObject
                AiExport(str(o, "endpoint"), str(o, "model"), str(o, "provider"))
            }.getOrNull()
        }
        val files = storage[NATIVE_FILES]?.let { raw ->
            (json.parseToJsonElement(raw) as JsonObject).entries.filter { safePath(it.key) && (it.value as? JsonPrimitive)?.isString == true }
                .associate { it.key to (it.value as JsonPrimitive).content }
        } ?: emptyMap()
        RestoreResult(files, ai, orphans, nativeDb != null)
    }

    private fun insertRows(port: SqlPort, table: String, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val cols = port.columns(table).toSet()
        for (r in rows) {
            val keys = r.keys.filter { it in cols }
            if (keys.isEmpty()) continue
            port.exec(
                "INSERT OR REPLACE INTO `$table` (${keys.joinToString { "`$it`" }}) VALUES (${keys.joinToString { "?" }})",
                keys.map { fromJson(r[it]) },
            )
        }
    }

    private fun fromJson(e: JsonElement?): Any? = when (e) {
        null, JsonNull -> null
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.content == "true" -> 1L
            e.content == "false" -> 0L
            e.content.any { it == '.' || it == 'e' || it == 'E' } -> e.doubleOrNull
            else -> e.longOrNull ?: e.doubleOrNull
        }
        else -> e.toString()
    }

    private fun restoreProjects(port: SqlPort, raw: String) {
        val arr = runCatching { json.parseToJsonElement(raw) as JsonArray }.getOrNull() ?: return
        port.exec("DELETE FROM saved_projects")
        arr.mapNotNull { it as? JsonObject }.forEachIndexed { i, p ->
            val snap = p["snapshot"]
            port.exec(
                "INSERT OR REPLACE INTO saved_projects(id, rootId, revision, name, updatedAt, snapshotJson, position) VALUES (?,?,?,?,?,?,?)",
                listOf(str(p, "id"), str(p, "rootId"), lng(p, "revision"), str(p, "name"), str(p, "updatedAt"),
                    if (snap is JsonPrimitive && snap.isString) snap.content else (snap ?: JsonObject(emptyMap())).toString(), i.toLong()),
            )
        }
    }

    private fun restoreNotes(port: SqlPort, raw: String) {
        val arr = runCatching { json.parseToJsonElement(raw) as JsonArray }.getOrNull() ?: return
        port.exec("DELETE FROM saved_notes")
        arr.forEachIndexed { i, n -> port.exec("INSERT INTO saved_notes(position, text) VALUES (?, ?)", listOf(i.toLong(), (n as? JsonPrimitive)?.content ?: n.toString())) }
    }

    /** Returns attachment file names whose project no longer exists. */
    private fun restoreOperations(port: SqlPort, raw: String): List<String> {
        val o = runCatching { json.parseToJsonElement(raw) as JsonObject }.getOrNull() ?: return emptyList()
        val keepAttachments = port.query("SELECT * FROM ops_attachments ORDER BY id")
        listOf("ops_machine_history", "ops_machines", "ops_audit", "ops_movements", "ops_materials", "ops_attachments", "ops_project_entries", "ops_projects")
            .forEach { port.exec("DELETE FROM `$it`") }
        val projectIds = mutableSetOf<String>()
        arr(o, "projects").forEachIndexed { i, p ->
            val id = str(p, "id"); projectIds += id
            port.exec(
                "INSERT OR REPLACE INTO ops_projects(id, name, customer, original, status, notes, created, position) VALUES (?,?,?,?,?,?,?,?)",
                listOf(id, str(p, "name"), str(p, "customer"), dbl(p, "original"), str(p, "status").ifEmpty { "Saved" }, str(p, "notes"), str(p, "created"), i.toLong()),
            )
            for ((key, kind) in listOf("payments" to "payment", "revisions" to "revision", "expenses" to "expense")) arr(p, key).forEach { e ->
                port.exec("INSERT INTO ops_project_entries(projectId, kind, amount, note, at) VALUES (?,?,?,?,?)", listOf(id, kind, dbl(e, "amount"), str(e, "note"), str(e, "at")))
            }
        }
        arr(o, "materials").forEachIndexed { i, m ->
            port.exec("INSERT OR REPLACE INTO ops_materials(id, name, unit, category, rate, min, position) VALUES (?,?,?,?,?,?,?)",
                listOf(str(m, "id"), str(m, "name"), str(m, "unit"), str(m, "category"), dbl(m, "rate"), dbl(m, "min"), i.toLong()))
        }
        arr(o, "movements").forEachIndexed { i, m ->
            port.exec("INSERT OR REPLACE INTO ops_movements(id, materialId, qty, type, worker, projectId, rate, at, position) VALUES (?,?,?,?,?,?,?,?,?)",
                listOf(str(m, "id"), str(m, "materialId"), dbl(m, "qty"), str(m, "type"), str(m, "worker"), str(m, "projectId"), dbl(m, "rate"), str(m, "at"), i.toLong()))
        }
        arr(o, "machines").forEachIndexed { i, m ->
            val id = str(m, "id")
            port.exec("INSERT OR REPLACE INTO ops_machines(id, name, serial, worker, status, position) VALUES (?,?,?,?,?,?)",
                listOf(id, str(m, "name"), str(m, "serial"), str(m, "worker"), str(m, "status").ifEmpty { "Available" }, i.toLong()))
            arr(m, "history").forEach { h -> port.exec("INSERT INTO ops_machine_history(machineId, at, status, worker) VALUES (?,?,?,?)", listOf(id, str(h, "at"), str(h, "status"), str(h, "worker"))) }
        }
        arr(o, "audit").forEach { a ->
            port.exec("INSERT INTO ops_audit(at, type, refId, value, name) VALUES (?,?,?,?,?)",
                listOf(str(a, "at"), str(a, "type"), str(a, "id"), (a["value"] as? JsonPrimitive)?.contentOrNull, (a["name"] as? JsonPrimitive)?.contentOrNull))
        }
        val orphans = mutableListOf<String>()
        for (a in keepAttachments) {
            if (a["projectId"] as? String in projectIds) {
                val cols = a.keys.toList()
                port.exec("INSERT INTO ops_attachments (${cols.joinToString { "`$it`" }}) VALUES (${cols.joinToString { "?" }})", cols.map { a[it] })
            } else (a["internalFileName"] as? String)?.let(orphans::add)
        }
        return orphans
    }

    // ------------------------------------------------------------------ helpers

    private fun rows(tables: Map<String, JsonArray>, t: String) = tables[t].orEmpty().mapNotNull { it as? JsonObject }
    private fun rowsOf(tables: JsonObject, t: String) = (tables[t] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun arr(o: JsonObject, k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun str(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.contentOrNull ?: ""
    private fun lng(o: JsonObject, k: String): Long = (o[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() ?: it.contentOrNull?.toDoubleOrNull()?.toLong() } ?: 0L
    private fun dbl(o: JsonObject, k: String): Double = (o[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() } ?: 0.0
}
