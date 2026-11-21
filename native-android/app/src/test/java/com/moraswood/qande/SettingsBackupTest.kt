package com.moraswood.qande

import com.moraswood.qande.backup.QeBackup
import com.moraswood.qande.backup.SqlPort
import com.moraswood.qande.data.QeMigrations
import com.moraswood.qande.data.StorageKeys
import com.moraswood.qande.logic.LengthConverter
import com.moraswood.qande.logic.QeAi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class SettingsBackupTest {
    private class JdbcPort(val c: Connection) : SqlPort {
        override fun query(sql: String): List<Map<String, Any?>> = c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val md = rs.metaData
                buildList {
                    while (rs.next()) add((1..md.columnCount).associate { i ->
                        md.getColumnName(i) to when (val o = rs.getObject(i)) {
                            null -> null; is Int -> o.toLong(); is Long -> o; is Double -> o; is Float -> o.toDouble(); is Number -> o.toDouble(); else -> o.toString()
                        }
                    })
                }
            }
        }
        override fun exec(sql: String, args: List<Any?>) {
            c.prepareStatement(sql).use { ps -> args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }; ps.execute() }
        }
        override fun columns(table: String) = query("PRAGMA table_info(`$table`)").map { it["name"] as String }
        override fun <T> transaction(block: () -> T): T {
            c.autoCommit = false
            try { val r = block(); c.commit(); return r } catch (e: Throwable) { c.rollback(); throw e } finally { c.autoCommit = true }
        }
    }

    private fun freshDb(): JdbcPort {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        val schema = Json.parseToJsonElement(File("schemas/com.moraswood.qande.data.QeDatabase/4.json").readText()).jsonObject["database"]!!.jsonObject
        c.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            schema["entities"]!!.jsonArray.map { it.jsonObject }.forEach { e ->
                val t = e["tableName"]!!.jsonPrimitive.content
                st.execute(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
                (e["indices"]?.jsonArray ?: emptyList()).forEach { st.execute(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
            }
            (QeMigrations.ASSET_TRIGGERS + QeMigrations.REVISION_TRIGGERS).forEach(st::execute)
        }
        return JdbcPort(c)
    }

    private fun envelope(storage: Map<String, String>): String {
        val w = StringWriter(); QeBackup.write(w, "2026-11-21T08:00:00.000Z", storage, emptyList()); return w.toString()
    }

    private val ops = """{"projects":[{"id":"P1","name":"Wardrobe","customer":"Ravi","original":125000,"status":"Running","payments":[{"amount":50000,"note":"Advance","at":"2026-11-02T05:00:00.000Z"}],"revisions":[{"amount":2500.5,"note":"Extra shelf","at":"2026-11-03T05:00:00.000Z"}],"expenses":[],"notes":"Teak","created":"2026-11-01T05:00:00.000Z"}],"materials":[{"id":"M1","name":"Fevicol","unit":"kg","category":"Adhesives & Fillers","rate":420,"min":2}],"movements":[{"id":"V1","materialId":"M1","qty":5,"type":"purchase","worker":"","projectId":"","rate":420,"at":"2026-11-02T06:00:00.000Z"},{"id":"V2","materialId":"M1","qty":1.5,"type":"issue","worker":"Raju","projectId":"P1","rate":420,"at":"2026-11-04T06:00:00.000Z"}],"machines":[{"id":"MWI-0001","name":"Planer","serial":"PL-9","worker":"Raju","status":"In Use","history":[{"at":"2026-11-05T06:00:00.000Z","status":"In Use","worker":"Raju"}]}],"audit":[{"at":"2026-11-03T05:00:00.000Z","type":"status","id":"P1","value":"Running"},{"at":"2026-11-06T05:00:00.000Z","type":"delete-project","id":"P0","name":"Old"}]}"""
    private val projects = """[{"id":"r1","rootId":"root","revision":1,"name":"Kitchen","updatedAt":"2026-11-10T05:00:00.000Z","snapshot":{"projectName":"Kitchen","revision":1}},{"id":"root","rootId":"root","revision":0,"name":"Kitchen","updatedAt":"2026-11-09T05:00:00.000Z","snapshot":{"projectName":"Kitchen","revision":0}}]"""
    private val v101 = linkedMapOf(
        StorageKeys.AUTOSAVE to """{"projectName":"Kitchen"}""",
        StorageKeys.PROJECTS to projects,
        StorageKeys.SAVED_NOTES to """["Teak polish extra","Transport included"]""",
        StorageKeys.OPERATIONS to ops,
        StorageKeys.QUICK_CALC to """{"rows":[]}""",
        StorageKeys.HOPPUS to """{"rows":[],"sawRate":"12"}""",
        StorageKeys.AI_SETTINGS to """{"endpoint":"https://api.x.ai/v1","model":"grok-4.7","provider":"xai"}""",
        QeBackup.LEGACY_PLAIN_KEY to "sk-should-never-be-stored",
    )

    @Test fun validationUsesV101Messages() {
        fun msg(s: String) = try { QeBackup.parse(s); null } catch (e: IllegalArgumentException) { e.message }
        assertEquals("Invalid file", msg("""{"format":"other","version":1,"storage":{}}"""))
        assertEquals("Invalid file", msg("""{"format":"moras-qe-backup","version":"1","storage":{}}"""))
        assertEquals("Invalid file", msg("""{"format":"moras-qe-backup","version":2,"storage":{}}"""))
        assertEquals("Invalid file", msg("""{"format":"moras-qe-backup","version":1,"storage":null}"""))
        assertEquals("Invalid file", msg("""[1]"""))
        assertEquals("Invalid data", msg("""{"format":"moras-qe-backup","version":1,"storage":{"other":"x"}}"""))
        assertEquals("Invalid data", msg("""{"format":"moras-qe-backup","version":1,"storage":{"mwi-x":5}}"""))
        assertEquals("Invalid data", msg("""{"format":"moras-qe-backup","version":1,"storage":["a"]}"""))
        assertNull(msg("""{"format":"moras-qe-backup","version":1.0,"storage":[]}"""))
        assertEquals("The file is not valid JSON.", msg("not json"))
        val text = envelope(mapOf("mwi-a" to "x\"y\n"))
        assertEquals("{\n  \"format\": \"moras-qe-backup\",\n  \"version\": 1,\n  \"date\": \"2026-11-21T08:00:00.000Z\",\n  \"storage\": {\n    \"mwi-a\": \"x\\\"y\\n\"\n  }\n}", text)
        assertEquals(mapOf("mwi-a" to "x\"y\n"), QeBackup.parse(text))
        assertEquals("{\n  \"format\": \"moras-qe-backup\",\n  \"version\": 1,\n  \"date\": \"d\",\n  \"storage\": {}\n}", StringWriter().also { QeBackup.write(it, "d", emptyMap(), emptyList()) }.toString())
    }

    @Test fun v101BackupRestoresIntoRoomAndExportsIdenticalV101Keys() {
        val db = freshDb()
        val r = QeBackup.restore(db, QeBackup.parse(envelope(v101)))
        assertFalse(r.nativeRestore)
        assertEquals(QeBackup.AiExport("https://api.x.ai/v1", "grok-4.7", "xai"), r.ai)
        assertEquals(2L, db.query("SELECT COUNT(*) AS n FROM saved_projects").single()["n"])
        assertEquals(listOf("Teak polish extra", "Transport included"), db.query("SELECT text FROM saved_notes ORDER BY position").map { it["text"] })
        assertEquals(2L, db.query("SELECT COUNT(*) AS n FROM ops_project_entries").single()["n"])
        assertEquals("Running", db.query("SELECT value FROM ops_audit ORDER BY rowId").first()["value"])
        assertTrue("the plaintext legacy key is never stored", db.query("SELECT `key` FROM kv_store").none { it["key"] == QeBackup.LEGACY_PLAIN_KEY || it["key"] == StorageKeys.AI_SETTINGS })

        val out = QeBackup.storage(QeBackup.dumpTables(db), r.ai)
        for (k in listOf(StorageKeys.AUTOSAVE, StorageKeys.PROJECTS, StorageKeys.SAVED_NOTES, StorageKeys.OPERATIONS, StorageKeys.QUICK_CALC, StorageKeys.HOPPUS, StorageKeys.AI_SETTINGS))
            assertEquals(k, v101[k], out[k])
        assertFalse(out.containsKey(QeBackup.LEGACY_PLAIN_KEY))
        assertTrue(out.keys.all { it.startsWith("mwi-") })
    }

    @Test fun v101RestoreKeepsOtherNativeDataAndOnlyDropsOrphanAttachments() {
        val db = freshDb()
        db.exec("INSERT INTO ops_projects VALUES ('P1','A','',1,'Saved','','t',0)")
        db.exec("INSERT INTO ops_projects VALUES ('P9','Gone','',1,'Saved','','t',1)")
        db.exec("INSERT INTO ops_attachments VALUES ('F1','P1','a.jpg','image/jpeg',10,'t','keep.jpg')")
        db.exec("INSERT INTO ops_attachments VALUES ('F2','P9','b.pdf','application/pdf',10,'t','orphan.pdf')")
        db.exec("INSERT INTO templates(name, furnitureType, createdAt, sourceJobKey) VALUES ('Cot','cot','t','j')")
        val r = QeBackup.restore(db, mapOf(StorageKeys.OPERATIONS to ops))
        assertEquals(listOf("orphan.pdf"), r.orphanAttachmentFiles)
        assertEquals(listOf("F1"), db.query("SELECT id FROM ops_attachments").map { it["id"] })
        assertEquals(1L, db.query("SELECT COUNT(*) AS n FROM templates").single()["n"])
    }

    @Test fun nativeBackupRoundTripsEveryTableAndFiles() {
        val src = freshDb()
        QeBackup.restore(src, v101)
        src.exec("INSERT INTO templates(name, furnitureType, createdAt, sourceJobKey) VALUES ('Cot','cot','t','j')")
        src.exec("INSERT INTO template_rows(templateId, position, l, lUnit, w, wUnit, t, tUnit, q, mode) VALUES (1,0,'6','ft','4','in','2','in','2','')")
        src.exec("INSERT INTO item_photos VALUES ('item-1.jpg','t',3)")
        src.exec("INSERT INTO kv_store VALUES ('native-job-session-v1','abc')")
        src.exec("INSERT INTO assets(name, category, purchaseDate, cost, serial, photoFile, createdAt) VALUES ('Drill','tool','2026-01-01',4500.5,'D1','item-1.jpg','t')")
        src.exec("INSERT INTO asset_issues(assetId, worker, issuedOn, dueBack, notes, createdAt) VALUES (1,'Ravi','2026-11-21','2026-11-23','','t')")
        src.exec("INSERT INTO asset_returns(issueId, returnedOn, `condition`, createdAt) VALUES (1,'2026-11-22','ok','t')")
        src.exec("INSERT INTO asset_corrections(targetType, targetId, worker, issuedOn, dueBack, clearDueBack, notes, returnedOn, `condition`, voidEntry, reason, requestedAt) VALUES ('issue',1,'Raju',NULL,NULL,0,NULL,NULL,NULL,0,'typo','t')")
        src.exec("INSERT INTO asset_correction_decisions(correctionId, decision, decidedAt) VALUES (1,'approved','t')")
        val before = QeBackup.dumpTables(src)
        val photo = byteArrayOf(1, 2, 3, -1, 0, 127)
        val w = StringWriter()
        QeBackup.write(w, "d", QeBackup.storage(before, QeBackup.AiExport("https://api.openai.com/v1", "gpt-4o-mini", "openai")),
            listOf(QeBackup.BackupFile("item_photos/item-1.jpg") { photo.inputStream() }, QeBackup.BackupFile("project-attachments/x y.pdf") { ByteArray(70000) { (it % 251).toByte() }.inputStream() }))
        val parsed = QeBackup.parse(w.toString())

        val dst = freshDb()
        dst.exec("INSERT INTO saved_notes(position, text) VALUES (0,'will be replaced')")
        val r = QeBackup.restore(dst, parsed)
        assertTrue(r.nativeRestore)
        assertEquals(before.mapValues { it.value.toString() }, QeBackup.dumpTables(dst).mapValues { it.value.toString() })
        assertEquals(photo.toList(), java.util.Base64.getDecoder().decode(r.files.getValue("item_photos/item-1.jpg")).toList())
        assertEquals(70000, java.util.Base64.getDecoder().decode(r.files.getValue("project-attachments/x y.pdf")).size)
        try { dst.exec("DELETE FROM asset_issues"); fail("append-only triggers must be back after restore") } catch (e: SQLException) { assertTrue(e.message!!.contains("append-only")) }
        dst.exec("INSERT INTO assets(name, category, purchaseDate, cost, serial, photoFile, createdAt) VALUES ('Saw','tool','',0,'',NULL,'t')")
        assertEquals(2L, dst.query("SELECT MAX(id) AS m FROM assets").single()["m"])
    }

    @Test fun restoreIsAtomicAndRejectsUnsafeFilePaths() {
        val db = freshDb()
        db.exec("INSERT INTO saved_notes(position, text) VALUES (0,'keep me')")
        try { QeBackup.restore(db, mapOf(QeBackup.NATIVE_DB to "{\"tables\":{\"saved_notes\":[{\"position\":0,\"text\":null}]}}")); fail() } catch (_: Exception) {}
        assertEquals(listOf("keep me"), db.query("SELECT text FROM saved_notes").map { it["text"] })
        assertTrue(QeBackup.safePath("item_photos/a.jpg"))
        listOf("../a", "item_photos/../../x", "item_photos/..", "other/a", "item_photos/a/b", "item_photos/").forEach { assertFalse(it, QeBackup.safePath(it)) }
    }

    @Test fun lengthConverterMatchesV101() {
        val cases = listOf(
            listOf("25.4", "mm", "in", "25.4 mm = 1 in"), listOf("1", "mi", "ft", "1 mi = 5,280 ft"),
            listOf("1000", "mm", "in", "1,000 mm = 39.37007874 in"), listOf("1234567.891", "mm", "ft", "1,234,567.891 mm = 4,050.41959 ft"),
            listOf("0.1", "yd", "cm", "0.1 yd = 9.144 cm"), listOf("-3.5", "ft", "mm", "-3.5 ft = -1,066.8 mm"),
            listOf("1e3", "m", "mi", "1,000 m = 0.62137119 mi"), listOf("2.0005", "in", "in", "2.001 in = 2.0005 in"),
        )
        for ((v, f, t, want) in cases) assertEquals(want, LengthConverter.result(v, f, t, LengthConverter::enUs))
        assertEquals(LengthConverter.EMPTY, LengthConverter.result("", "mm", "in", LengthConverter::enUs))
        assertEquals(LengthConverter.EMPTY, LengthConverter.result("abc", "mm", "in", LengthConverter::enUs))
        assertEquals(listOf("mm", "cm", "m", "in", "ft", "yd", "mi"), LengthConverter.UNITS.map { it.code })
    }

    @Test fun aiProviderDetectionAndValidation() {
        assertEquals("openrouter", QeAi.detect("sk-or-v1-abc")); assertEquals("anthropic", QeAi.detect("sk-ant-x"))
        assertEquals("google", QeAi.detect("AIzaSy")); assertEquals("groq", QeAi.detect("gsk_1")); assertEquals("xai", QeAi.detect("xai-1"))
        assertEquals("perplexity", QeAi.detect("pplx-1")); assertEquals("openai", QeAi.detect("sk-proj-1")); assertEquals("openai", QeAi.detect("sk-1"))
        assertEquals("generic", QeAi.detect("abc")); assertEquals("", QeAi.detect("  "))
        assertEquals(QeAi.DETECTION_IDLE, QeAi.detectionText(""))
        assertEquals("Detected xAI. Suggested model: grok-4.7. Check your provider account’s model access.", QeAi.detectionText("xai-123"))
        assertEquals("This sk- prefix is shared by multiple providers. Using OpenAI-compatible defaults; verify the endpoint and model for your key.", QeAi.detectionText("sk-123"))
        assertEquals("Detected OpenAI. Suggested model: gpt-4o-mini. Check your provider account’s model access.", QeAi.detectionText("sk-proj-1"))
        assertEquals("Key format not recognized. You can still use it with a compatible provider; enter its endpoint and model below.", QeAi.detectionText("zzz"))
        assertEquals("Saved key is configured for OpenAI.", QeAi.savedText("openai"))
        assertEquals("Saved key provider is not recognized; configure its endpoint and model.", QeAi.savedText(""))
        assertEquals("Enter a valid API endpoint URL.", QeAi.validate("", "m"))
        assertEquals("Enter a valid API endpoint URL.", QeAi.validate("not a url", "m"))
        assertEquals("The API endpoint must use HTTPS so your key is not sent insecurely.", QeAi.validate("http://api.x.ai/v1", "m"))
        assertEquals("Enter a model name for your AI provider.", QeAi.validate("https://api.x.ai/v1", ""))
        assertNull(QeAi.validate("https://api.x.ai/v1", "grok-4.7"))
    }
}
