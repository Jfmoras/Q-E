package com.moraswood.qande

import com.moraswood.qande.assets.OwnerPin
import com.moraswood.qande.data.AssetCorrectionEntity
import com.moraswood.qande.data.AssetDecisionEntity
import com.moraswood.qande.data.AssetEntity
import com.moraswood.qande.data.AssetIssueEntity
import com.moraswood.qande.data.AssetReturnEntity
import com.moraswood.qande.data.QeMigrations
import com.moraswood.qande.logic.AssetLogic
import com.moraswood.qande.logic.AssetLogs
import com.moraswood.qande.logic.AssetStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDate

/** Feature C: derived status, corrections, owner PIN, v2→v3 migration and the append-only triggers. */
class AssetTrackingTest {
    private val today = LocalDate.parse("2026-11-21")
    private val drill = AssetEntity(1, "Drill", "tool", "2025-01-10", 4500.0, "SN-1", null, "t")
    private fun issue(id: Long, on: String, due: String?, worker: String = "Ravi", asset: Long = 1) = AssetIssueEntity(id, asset, worker, on, due, "", "t$id")
    private fun ret(id: Long, issueId: Long, on: String) = AssetReturnEntity(id, issueId, on, "ok", "r$id")
    private fun logs(vararg i: AssetIssueEntity, returns: List<AssetReturnEntity> = emptyList(), corr: List<AssetCorrectionEntity> = emptyList(), dec: List<AssetDecisionEntity> = emptyList()) =
        AssetLogs(listOf(drill), i.toList(), returns, corr, dec)

    @Test fun neverIssuedIsInStock() {
        assertEquals(AssetStatus.InStock, AssetLogic.state(logs(), drill, today).status)
    }

    @Test fun issuedUntilAndIncludingDueDateThenOverdue() {
        val l = logs(issue(1, "2026-11-19", "2026-11-21"))
        assertEquals(AssetStatus.Issued, AssetLogic.state(l, drill, today).status)
        assertEquals(AssetStatus.Overdue, AssetLogic.state(l, drill, today.plusDays(1)).status)
        assertEquals("Ravi", AssetLogic.state(l, drill, today).open?.worker)
    }

    @Test fun noDueDateNeverOverdue() {
        assertEquals(AssetStatus.Issued, AssetLogic.state(logs(issue(1, "2020-01-01", null)), drill, today).status)
    }

    @Test fun returnedIsInStockAndRemembersLastWorker() {
        val s = AssetLogic.state(logs(issue(1, "2026-11-01", "2026-11-03", "Suresh"), returns = listOf(ret(1, 1, "2026-11-10"))), drill, today)
        assertEquals(AssetStatus.InStock, s.status)
        assertNull(s.open)
        assertEquals("Suresh", s.lastWorker)
    }

    @Test fun defaultDueBackIsTodayPlusTwo() {
        assertEquals("2026-11-23", AssetLogic.defaultDueBack(today))
        assertEquals("2027-01-01", AssetLogic.defaultDueBack(LocalDate.parse("2026-12-30")))
    }

    @Test fun correctionsOnlyCountOnceApproved() {
        val l0 = logs(issue(1, "2026-11-10", "2026-11-12"))
        assertEquals(AssetStatus.Overdue, AssetLogic.state(l0, drill, today).status)
        val c = AssetCorrectionEntity(id = 1, targetType = "issue", targetId = 1, dueBack = "2026-11-30", reason = "Agreed longer job", requestedAt = "x")
        val pending = l0.copy(corrections = listOf(c))
        assertEquals("pending request changes nothing", AssetStatus.Overdue, AssetLogic.state(pending, drill, today).status)
        assertEquals(listOf(c), AssetLogic.pending(pending))
        val rejected = pending.copy(decisions = listOf(AssetDecisionEntity(1, 1, "rejected", "y")))
        assertEquals(AssetStatus.Overdue, AssetLogic.state(rejected, drill, today).status)
        assertTrue(AssetLogic.pending(rejected).isEmpty())
        val approved = pending.copy(decisions = listOf(AssetDecisionEntity(1, 1, "approved", "y")))
        assertEquals(AssetStatus.Issued, AssetLogic.state(approved, drill, today).status)
        assertEquals("raw entry untouched", "2026-11-12", approved.issues[0].dueBack)
    }

    @Test fun laterApprovalWinsAndClearDueBack() {
        val c1 = AssetCorrectionEntity(id = 1, targetType = "issue", targetId = 1, worker = "Mahesh", reason = "wrong name", requestedAt = "a")
        val c2 = AssetCorrectionEntity(id = 2, targetType = "issue", targetId = 1, clearDueBack = true, worker = "Manoj", reason = "typo", requestedAt = "b")
        val l = logs(issue(1, "2026-11-01", "2026-11-02"), corr = listOf(c1, c2), dec = listOf(AssetDecisionEntity(1, 2, "approved", "1"), AssetDecisionEntity(2, 1, "approved", "2")))
        val s = AssetLogic.state(l, drill, today)
        assertEquals("approved later (c1) wins", "Mahesh", s.open?.worker)
        assertNull(s.open?.dueBack)
        assertEquals(AssetStatus.Issued, s.status)
    }

    @Test fun voidedIssueOrReturnIsIgnored() {
        val voidIssue = AssetCorrectionEntity(id = 1, targetType = "issue", targetId = 1, voidEntry = true, reason = "wrong asset", requestedAt = "a")
        val l = logs(issue(1, "2026-11-01", "2026-11-02"), corr = listOf(voidIssue), dec = listOf(AssetDecisionEntity(1, 1, "approved", "x")))
        assertEquals(AssetStatus.InStock, AssetLogic.state(l, drill, today).status)
        val voidReturn = AssetCorrectionEntity(id = 2, targetType = "return", targetId = 1, voidEntry = true, reason = "not really back", requestedAt = "b")
        val l2 = logs(issue(1, "2026-11-01", "2026-11-02"), returns = listOf(ret(1, 1, "2026-11-03")), corr = listOf(voidReturn), dec = listOf(AssetDecisionEntity(1, 2, "approved", "x")))
        assertEquals(AssetStatus.Overdue, AssetLogic.state(l2, drill, today).status)
    }

    @Test fun overdueListAndValidation() {
        val saw = drill.copy(id = 2, name = "Saw")
        val l = AssetLogs(listOf(drill, saw), listOf(issue(1, "2026-11-01", "2026-11-05", "Ravi", 1), issue(2, "2026-11-20", "2026-11-22", "Anil", 2)))
        assertEquals(listOf("Drill"), AssetLogic.overdue(l, today).map { it.asset.name })
        assertEquals("This asset is already issued. Record its return first.", AssetLogic.validateIssue(l, drill, "X", "2026-11-21", "", today))
        assertEquals("Enter the worker's name.", AssetLogic.validateIssue(AssetLogs(listOf(drill)), drill, " ", "2026-11-21", "", today))
        assertEquals("Due-back date cannot be before the issue date.", AssetLogic.validateIssue(AssetLogs(listOf(drill)), drill, "A", "2026-11-21", "2026-11-20", today))
        assertNull(AssetLogic.validateIssue(AssetLogs(listOf(drill)), drill, "A", "2026-11-21", AssetLogic.defaultDueBack(today), today))
        assertEquals("This asset is not issued.", AssetLogic.validateReturn(null, "2026-11-21"))
        assertEquals("Give a reason for the correction.", AssetLogic.validateCorrection(AssetCorrectionEntity(targetType = "issue", targetId = 1, worker = "A", reason = " ", requestedAt = "")))
        assertEquals("Change at least one field.", AssetLogic.validateCorrection(AssetCorrectionEntity(targetType = "issue", targetId = 1, reason = "r", requestedAt = "")))
    }

    @Test fun ownerPinIsSaltedPbkdf2() {
        val a = OwnerPin.create("2468", iterations = 1000)
        val b = OwnerPin.create("2468", iterations = 1000)
        assertTrue(a.salt != b.salt && a.hash != b.hash)
        assertEquals(32, a.salt.length); assertEquals(64, a.hash.length)
        assertTrue(OwnerPin.verify("2468", a)); assertFalse(OwnerPin.verify("2469", a)); assertFalse(OwnerPin.verify("24", a))
        assertFalse(OwnerPin.valid("12a4")); assertFalse(OwnerPin.valid("123456789"))
        assertEquals(OwnerPin.ITERATIONS, OwnerPin.create("1234").iterations)
        // RFC 6070-style known vector for PBKDF2-HMAC-SHA256 (password / salt, 1 iteration).
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            OwnerPin.hex(OwnerPin.derive("password", "salt".toByteArray(), 1)))
    }

    /* ---------- Room schema / SQLite ---------- */
    private fun schema(v: Int) = Json.parseToJsonElement(File("schemas/com.moraswood.qande.data.QeDatabase/$v.json").readText()).jsonObject["database"]!!.jsonObject
    private fun createSql(db: JsonObject, only: Set<String>? = null) = db["entities"]!!.jsonArray.map { it.jsonObject }
        .filter { only == null || it["tableName"]!!.jsonPrimitive.content in only }.flatMap { e ->
            val t = e["tableName"]!!.jsonPrimitive.content
            listOf(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) +
                (e["indices"]?.jsonArray ?: emptyList()).map { it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t) }
        }

    @Test fun migration2to3MatchesExportedSchema() {
        val v2 = schema(2)["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()
        val added = schema(3)["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet() - v2
        assertEquals(setOf("assets", "asset_issues", "asset_returns", "asset_corrections", "asset_correction_decisions"), added)
        assertEquals(createSql(schema(3), added).toSet(), QeMigrations.MIGRATION_2_3_SQL.toSet())
        assertTrue(QeMigrations.MIGRATION_2_3_SQL.none { it.contains("DROP", true) || it.contains("ALTER", true) })
        assertEquals(8, QeMigrations.ASSET_TRIGGERS.size)
    }

    @Test fun migratedDatabaseKeepsDataAndLogsAreAppendOnly() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            val st = c.createStatement()
            st.execute("PRAGMA foreign_keys = ON")
            createSql(schema(2)).forEach(st::execute)
            st.execute("INSERT INTO kv_store(`key`, json) VALUES ('mwi-autosave-v1', '{\"x\":1}')")
            st.execute("INSERT INTO ops_machines(id, name, serial, worker, status, position) VALUES ('MWI-0001', 'Grinder', '', '', 'Available', 0)")
            // v2 -> v3 exactly as Room runs it.
            (QeMigrations.MIGRATION_2_3_SQL + QeMigrations.ASSET_TRIGGERS).forEach(st::execute)
            QeMigrations.ASSET_TRIGGERS.forEach(st::execute) // onOpen re-run is harmless (IF NOT EXISTS)
            st.executeQuery("SELECT json FROM kv_store").use { assertTrue(it.next()); assertEquals("{\"x\":1}", it.getString(1)) }
            st.executeQuery("SELECT name FROM ops_machines").use { assertTrue(it.next()); assertEquals("Grinder", it.getString(1)) }

            st.execute("INSERT INTO assets(name, category, purchaseDate, cost, serial, photoFile, createdAt) VALUES ('Drill','tool','',0,'',NULL,'t')")
            st.execute("INSERT INTO asset_issues(assetId, worker, issuedOn, dueBack, notes, createdAt) VALUES (1,'Ravi','2026-11-21','2026-11-23','','t')")
            st.execute("INSERT INTO asset_returns(issueId, returnedOn, `condition`, createdAt) VALUES (1,'2026-11-22','ok','t')")
            st.execute("INSERT INTO asset_corrections(targetType, targetId, worker, issuedOn, dueBack, clearDueBack, notes, returnedOn, `condition`, voidEntry, reason, requestedAt) VALUES ('issue',1,'Raju',NULL,NULL,0,NULL,NULL,NULL,0,'typo','t')")
            st.execute("INSERT INTO asset_correction_decisions(correctionId, decision, decidedAt) VALUES (1,'approved','t')")
            val blocked = listOf(
                "UPDATE asset_issues SET worker = 'X'", "DELETE FROM asset_issues",
                "UPDATE asset_returns SET `condition` = 'X'", "DELETE FROM asset_returns",
                "UPDATE asset_corrections SET reason = 'X'", "DELETE FROM asset_corrections",
                "UPDATE asset_correction_decisions SET decision = 'rejected'", "DELETE FROM asset_correction_decisions",
            )
            for (sql in blocked) {
                try { st.execute(sql); fail("should abort: $sql") } catch (e: SQLException) { assertTrue(sql, e.message!!.contains("append-only")) }
            }
            try { st.execute("INSERT INTO asset_correction_decisions(correctionId, decision, decidedAt) VALUES (1,'rejected','t2')"); fail("one decision per request") } catch (_: SQLException) {}
            try { st.execute("INSERT INTO asset_issues(assetId, worker, issuedOn, dueBack, notes, createdAt) VALUES (99,'X','2026-11-21',NULL,'','t')"); fail("asset must exist") } catch (_: SQLException) {}
            st.executeQuery("SELECT worker FROM asset_issues").use { assertTrue(it.next()); assertEquals("Ravi", it.getString(1)) }
            assertNotNull(st)
        }
    }
}
