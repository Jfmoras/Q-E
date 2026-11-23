package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Customer
import com.moraswood.qande.data.Quote
import com.moraswood.qande.data.QeMigrations
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.OpsEntry
import com.moraswood.qande.logic.OpsProject
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.logic.QeOps
import com.moraswood.qande.logic.QeProjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.sql.DriverManager

class ProjectsTest {
    private val p = OpsProject(
        id = "a", name = "Cot", original = 50000.0,
        payments = listOf(OpsEntry(10000.0, "advance", "2026-11-02T12:00:00.000Z"), OpsEntry(5000.0, "", "2026-10-20T12:00:00.000Z")),
        revisions = listOf(OpsEntry(2000.0, "extra drawer", "2026-11-05T10:00:00.000Z")), created = "2026-10-01T00:00:00.000Z",
    )

    @Test fun paymentMath() {
        val m = QeProjects.money(p)
        assertEquals(52000.0, m.total, 0.0) // V10.1 value(): original + revisions
        assertEquals(15000.0, m.paid, 0.0)
        assertEquals(37000.0, m.remaining, 0.0) // total − Σ payments
        assertEquals(29, m.pct)
        assertEquals(QeOps.value(p) - QeOps.paid(p), m.remaining, 0.0)
        assertEquals(listOf("2026-10-20T12:00:00.000Z", "2026-11-02T12:00:00.000Z"), QeProjects.chronological(p).map { it.at })
        assertEquals("2026-11-21T12:00:00.000Z", QeProjects.paymentAt(" 2026-11-21 "))
        assertNull(QeProjects.paymentAt("21/11/2026"))
        assertNull(QeProjects.validAmount("0")); assertNull(QeProjects.validAmount("-5")); assertEquals(250.5, QeProjects.validAmount("250.5")!!, 0.0)
        assertEquals(0, QeProjects.money(0.0, listOf(10.0)).pct)
        assertEquals(100, QeProjects.money(100.0, listOf(150.0)).pct)
    }

    @Test fun summaryAndStatusFiltering() {
        val q = p.copy(id = "b", original = 10000.0, payments = listOf(OpsEntry(1000.0, "", "2026-11-10T12:00:00.000Z")), revisions = emptyList(), created = "2026-11-01")
        val d = p.copy(id = "c", original = 99999.0, payments = listOf(OpsEntry(500.0, "", "2026-11-11T12:00:00.000Z")), created = "2026-09-01")
        val status = mapOf("a" to QeProjects.RUNNING, "b" to QeProjects.FINISHED, "c" to QeProjects.DELETED)
        val s = QeProjects.summary(listOf(p, q, d), { status[it]!! }, "2026-11")
        assertEquals(11000.0, s.collectedThisMonth, 0.0) // deleted project excluded
        assertEquals(16000.0, s.collected, 0.0); assertEquals(62000.0, s.value, 0.0); assertEquals(1, s.running)
        val extra = p.copy(id = "e", created = "2026-11-15")
        val sec = QeProjects.sections(listOf(p, q, d, extra), { status[it.id] ?: "bogus" }, { it.created })
        assertEquals(listOf("e", "a"), sec.running.map { it.id }) // unknown status = running; newest first
        assertEquals(listOf("b"), sec.finished.map { it.id }); assertEquals(listOf("c"), sec.deleted.map { it.id })
        assertEquals(QeProjects.RUNNING, QeProjects.status(null))
    }

    @Test fun commentsCanBeEditedAndDeleted() {
        val a = QeProjects.append(emptyList(), "c1", QeProjects.USER, " Started ", "t1")!!
        val b = QeProjects.append(a, "c2", QeProjects.CLIENT, "Darker polish please", "t2")!!
        assertEquals(listOf("user", "client"), b.map { it.author })
        assertNull(QeProjects.append(b, "c3", QeProjects.USER, "  ", "t3"))
        assertNull(QeProjects.append(b, "c3", "owner", "x", "t3"))
        val e = QeProjects.edit(b, "c2", " Walnut polish ", "t4")!!
        assertEquals(QeProjects.Comment("c2", "client", "Walnut polish", "t2", "t4"), e[1]) // author + original time kept, edited stamped
        assertEquals(b[0], e[0])
        assertNull(QeProjects.edit(b, "c2", " ", "t4")); assertNull(QeProjects.edit(b, "nope", "x", "t4"))
        assertEquals(listOf("c2"), QeProjects.delete(e, "c1").map { it.id })
        // migration 10 → 11 removes the v10 append-only triggers and adds editedAt: UPDATE / DELETE now work
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use { st ->
                st.execute("CREATE TABLE `pp_comments` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `author` TEXT NOT NULL, `text` TEXT NOT NULL, `at` TEXT NOT NULL, PRIMARY KEY(`id`))")
                st.execute("CREATE TABLE `pp_projects` (`projectId` TEXT NOT NULL, PRIMARY KEY(`projectId`))")
                QeMigrations.COMMENT_TRIGGERS.forEach(st::execute)
                st.execute("INSERT INTO pp_comments VALUES ('1','p','user','hello','t')")
                try { st.execute("UPDATE pp_comments SET text='x'"); fail("v10 refuses") } catch (e: java.sql.SQLException) { assertTrue(e.message!!.contains("append-only")) }
                QeMigrations.MIGRATION_10_11_SQL.forEach(st::execute)
                st.execute("UPDATE pp_comments SET text='hello again', editedAt='t2' WHERE id='1'")
                st.executeQuery("SELECT text, editedAt FROM pp_comments").use { rs -> rs.next(); assertEquals("hello again", rs.getString(1)); assertEquals("t2", rs.getString(2)) }
                st.execute("DELETE FROM pp_comments WHERE id='1'")
                st.executeQuery("SELECT COUNT(*) FROM pp_comments").use { rs -> rs.next(); assertEquals(0, rs.getInt(1)) }
            }
        }
    }

    @Test fun deletingAPaymentRaisesRemaining() {
        val m = QeProjects.money(p)
        val after = QeProjects.withoutPayment(QeOps.value(p), p.payments.map { it.amount }, 0) // delete the ₹10,000 advance
        assertEquals(37000.0, m.remaining, 0.0)
        assertEquals(5000.0, after.paid, 0.0)
        assertEquals(47000.0, after.remaining, 0.0)
        assertEquals(m.remaining + 10000.0, after.remaining, 0.0)
        assertEquals(10, after.pct)
        // summary card follows: same project without that payment
        val s = QeProjects.summary(listOf(p.copy(payments = p.payments.drop(1))), { QeProjects.RUNNING }, "2026-11")
        assertEquals(5000.0, s.collected, 0.0); assertEquals(0.0, s.collectedThisMonth, 0.0)
    }

    @Test fun revisionSnapshotAndTotalFollowLatest() {
        val r = QeProjects.revise(0, QeOps.value(p), "55500", "Added mirror, removed one drawer")!!
        assertEquals(1, r.next); assertEquals(3500.0, r.delta, 0.0)
        assertEquals("R1: Added mirror, removed one drawer", r.note)
        // the new total is the V10.1 value with the revision entry added → remaining follows the latest revision
        val revised = p.copy(revisions = p.revisions + OpsEntry(r.delta, r.note, "t"))
        assertEquals(55500.0, QeProjects.money(revised).total, 0.0)
        assertEquals(40500.0, QeProjects.money(revised).remaining, 0.0)
        // extras removed: negative difference
        val down = QeProjects.revise(1, 55500.0, "50000", " ")!!
        assertEquals(-5500.0, down.delta, 0.0); assertEquals("R2: Client revision", down.note)
        assertNull(QeProjects.revise(0, 1.0, "abc", "")); assertNull(QeProjects.revise(0, 1.0, "-1", ""))
        assertEquals("R0", QeProjects.label(0))
        // snapshot round-trips (frozen, read-only copy of the old revision)
        val snap = QeProjects.Snapshot(0, 52000.0, 15000.0, "Cot", "Teak", "2026-10-01",
            listOf(QeProjects.SnapItem("Cot", "1", "set", 40000.0, "7 ft × 4 in × 3 in × 4 pcs (CFT)", "Teak — 1.000 CFT")),
            listOf(QeProjects.SnapHardware("Hinges", "4", "brass")), "2026-11-21T10:00:00.000Z")
        assertEquals(snap, QeProjects.decode(QeProjects.encode(snap)))
        assertNull(QeProjects.decode("not json"))
    }

    @Test fun quotationToProjectMapping() {
        val cot = CalcItem("Cot", listOf(Row("7", "ft", "4", "in", "3", "in", "4", "CFT"), Row("6", "ft", "6", "in", "1", "in", "2", "KOL")))
        val table = CalcItem("Table", listOf(Row("4", "ft", "3", "in", "2", "in", "4", "CFT")))
        val s = WorkspaceState(
            projectName = "Ravi house", revision = 2, customer = Customer("Ravi", "Kinnigoli"),
            estimationItems = listOf(table, cot),
            quoteItems = listOf(QuoteItem("Cot", "set", "1", "40000", species = "Teak"), QuoteItem("Table", "", "2", "6000"), QuoteItem()),
            quote = Quote("percent", "10", "18", listOf("Delivery in 30 days", "")),
        )
        val d = QeProjects.fromQuotation(s)
        assertEquals("Ravi house", d.name); assertEquals("Ravi", d.customer)
        assertEquals(QeOps.importQuoteValue(s).toDouble(), d.total, 1e-9) // same grand total V10.1 import uses
        assertEquals((40000.0 + 12000.0) * 0.9 * 1.18, d.total, 1e-6)
        assertEquals(2, d.items.size) // blank line skipped
        val c = d.items[0]
        assertEquals("Cot", c.name); assertEquals(40000.0, c.price, 0.0); assertEquals("1", c.qty); assertEquals("set", c.unit)
        assertTrue(c.wood.startsWith("Teak — "))
        val t = QeMath.itemTotals(cot)
        assertTrue(c.wood.contains(com.moraswood.qande.logic.QeFormat.toFixed(t.cft, 3) + " CFT"))
        assertEquals("7 ft × 4 in × 3 in × 4 pcs (CFT)\n6 ft × 6 in × 1 in × 2 pcs (KOL)", c.sizes) // matched by name, not index
        assertEquals(12000.0, d.items[1].price, 0.0)
        assertEquals("4 ft × 3 in × 2 in × 4 pcs (CFT)", d.items[1].sizes)
        assertEquals("Delivery in 30 days", d.notes)
        assertEquals("Cot, Table", d.description)
    }
}
