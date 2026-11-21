package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.QeMigrations
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.QuoteOption
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeRevisions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException

class RevisionsTest {
    private fun rec(id: String, root: String, rev: Int, at: String, snap: WorkspaceState = WorkspaceState(), pos: Int = 0) =
        SavedProjectEntity(id, root, rev, "Kitchen", at, QeJson.encodeToString(WorkspaceState.serializer(), snap), pos)

    @Test fun numberingStartsAtR0AndEverySaveAppends() {
        assertEquals(0, QeRevisions.nextRevision(emptyList(), "root", 0))
        val r0 = rec("root", "root", 0, "2026-11-01T00:00:00Z")
        assertEquals(1, QeRevisions.nextRevision(listOf(r0), "root", 0)) // saving again never overwrites R0
        val list = listOf(rec("b", "root", 2, "c"), rec("a", "root", 1, "b"), r0)
        assertEquals(3, QeRevisions.nextRevision(list, "root", 2))
        assertEquals(3, QeRevisions.nextRevision(list, "root", 3)) // V10.1 Revise already set latest + 1
        assertEquals(0, QeRevisions.nextRevision(list, "other", 0)) // another job starts at R0
        assertEquals("Kitchen · R2", QeRevisions.label("Kitchen", 2))
    }

    @Test fun listGroupsRevisionsPerJobLatestFirstAndOnlyLatestIsEditable() {
        val list = listOf(rec("k1", "k", 1, "2026-11-03"), rec("w0", "w", 0, "2026-11-02"), rec("k0", "k", 0, "2026-11-01"), rec("legacy", "", 0, "2026-10-01"))
        val jobs = QeRevisions.jobs(list)
        assertEquals(listOf("k", "w", "legacy"), jobs.map { it.rootId })
        assertEquals(listOf(1, 0), jobs[0].revisions.map { it.revision })
        assertEquals("k1", jobs[0].latest.id)
        assertTrue(QeRevisions.isLatest(list, list[0]))
        assertFalse(QeRevisions.isLatest(list, list[2]))
        assertTrue(QeRevisions.isLatest(list, list[3]))
    }

    @Test fun snapshotsAreFullAndPhotosAreSharedByPath() {
        val s = WorkspaceState(
            projectName = "Kitchen", revision = 2, projectId = "k",
            items = listOf(CalcItem(name = "Cot", photo = "item-a.jpg")), estimationItems = listOf(CalcItem(name = "Cot", photo = "item-b.jpg")),
            quoteItems = listOf(QuoteItem(description = "Cot", unitPrice = "100", images = listOf("data:image/jpeg;base64,AA"))),
            quoteOptions = listOf(QuoteOption(name = "Teak", species = "Teak")),
        )
        val json = QeJson.encodeToString(WorkspaceState.serializer(), s)
        assertEquals(s, QeJson.decodeFromString(WorkspaceState.serializer(), json))
        assertEquals(setOf("item-a.jpg", "item-b.jpg"), QeRevisions.photoRefs(json))

        val r0 = rec("r0", "k", 0, "a", s.copy(items = listOf(CalcItem(photo = "item-a.jpg")), estimationItems = emptyList()))
        val r1 = rec("r1", "k", 1, "b", s)
        val all = listOf(r1, r0)
        // item-a is shared with R0, item-b only in R1 → deleting R1 frees item-b only
        assertEquals(setOf("item-b.jpg"), QeRevisions.orphanedPhotos(r1, all, WorkspaceState(), emptySet()))
        assertEquals(emptySet<String>(), QeRevisions.orphanedPhotos(r1, all, s, emptySet())) // still on screen
        assertEquals(emptySet<String>(), QeRevisions.orphanedPhotos(r1, all, WorkspaceState(), setOf("item-b.jpg"))) // an asset uses it
        assertEquals(emptySet<String>(), QeRevisions.orphanedPhotos(r0, all, WorkspaceState(), emptySet()))
    }

    private fun schema(v: Int) = Json.parseToJsonElement(File("schemas/com.moraswood.qande.data.QeDatabase/$v.json").readText()).jsonObject["database"]!!.jsonObject
    private fun createSql(db: JsonObject) = db["entities"]!!.jsonArray.map { it.jsonObject }.flatMap { e ->
        val t = e["tableName"]!!.jsonPrimitive.content
        listOf(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) +
            (e["indices"]?.jsonArray ?: emptyList()).map { it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t) }
    }

    @Test fun migration3to4ConvertsDataMatchesSchemaAndMakesRevisionsReadOnly() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            val st = c.createStatement()
            createSql(schema(3)).forEach(st::execute)
            st.execute("INSERT INTO saved_projects VALUES ('r0','',0,'Kitchen','2026-11-01T00:00:00.000Z','{\"projectName\":\"Kitchen\"}',1)")
            st.execute("INSERT INTO saved_projects VALUES ('r1','r0',1,'Kitchen','2026-11-02T00:00:00.000Z','{\"projectName\":\"Kitchen\",\"revision\":1}',0)")
            (QeMigrations.MIGRATION_3_4_SQL + QeMigrations.REVISION_TRIGGERS).forEach(st::execute)
            QeMigrations.REVISION_TRIGGERS.forEach(st::execute) // onOpen re-run is harmless

            // Columns and indices now equal what Room expects in 4.json.
            val fields = schema(4)["entities"]!!.jsonArray.map { it.jsonObject }.first { it["tableName"]!!.jsonPrimitive.content == "saved_projects" }
            val wantCols = fields["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content to (it.jsonObject["notNull"]!!.jsonPrimitive.content == "true") }
            val haveCols = st.executeQuery("PRAGMA table_info(saved_projects)").use { rs -> buildList { while (rs.next()) add(rs.getString("name") to (rs.getInt("notnull") == 1)) } }
            assertEquals(wantCols, haveCols)
            val wantIdx = fields["indices"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
            val haveIdx = st.executeQuery("PRAGMA index_list(saved_projects)").use { rs -> buildSet { while (rs.next()) rs.getString("name").takeIf { it.startsWith("index_") }?.let(::add) } }
            assertEquals(wantIdx, haveIdx)

            st.executeQuery("SELECT id, rootId, basedOn FROM saved_projects ORDER BY revision").use { rs ->
                assertTrue(rs.next()); assertEquals("r0", rs.getString(2)); rs.getInt(3); assertTrue(rs.wasNull())
                assertTrue(rs.next()); assertEquals("r0", rs.getString(2))
            }
            try { st.execute("UPDATE saved_projects SET snapshotJson = '{}' WHERE id = 'r0'"); fail("saved revisions must be read-only") }
            catch (e: SQLException) { assertTrue(e.message!!.contains("read-only")) }
            st.execute("INSERT INTO saved_projects VALUES ('r2','r0',2,'Kitchen','2026-11-03T00:00:00.000Z','{}',-1,1)")
            st.execute("DELETE FROM saved_projects WHERE id = 'r1'") // deleting a revision stays possible
            st.execute("INSERT OR REPLACE INTO saved_projects VALUES ('r2','r0',2,'Kitchen','x','{}',-1,1)") // backup restore path
            st.executeQuery("SELECT COUNT(*) FROM saved_projects").use { it.next(); assertEquals(2, it.getInt(1)) }
            assertTrue(QeMigrations.MIGRATION_3_4_SQL.none { it.contains("DROP", true) || it.contains("DELETE", true) })
        }
    }
}
