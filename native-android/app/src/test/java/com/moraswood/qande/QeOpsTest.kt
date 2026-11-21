package com.moraswood.qande

import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.OpsDb
import com.moraswood.qande.logic.OpsMachine
import com.moraswood.qande.logic.OpsMaterial
import com.moraswood.qande.logic.OpsMovement
import com.moraswood.qande.logic.OpsProject
import com.moraswood.qande.logic.OpsProjectForm
import com.moraswood.qande.logic.QeOps
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Projects / payments / inventory against V10.1: expected-v101.json is the unchanged operations.js run in node on a
 * fake DOM (see v101-harness.js). Every scripted step is replayed through QeOps and compared: stored records
 * (ids/timestamps aside), the #message text, the form after imports, and the rendered ₹ figures / stock lines.
 */
class QeOpsTest {
    private fun res(name: String) = File("src/test/resources/operations/$name").readText()
    private val lenient = Json { ignoreUnknownKeys = true }
    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")

    private data class St(var db: OpsDb = OpsDb(), var message: String = "", var form: OpsProjectForm = OpsProjectForm(), var n: Int = 0, var month: String = "NOW")

    private fun apply(st: St, op: JsonArray, autosave: WorkspaceState?) {
        fun s(i: Int) = op[i].jsonPrimitive.content
        fun i(i: Int) = op[i].jsonPrimitive.int
        val id = "id${st.n++}"
        val out: QeOps.Outcome? = when (s(0)) {
            "create" -> {
                st.form = OpsProjectForm(s(1), s(2), s(3), s(4), s(5))
                QeOps.createProject(st.db, st.form, id, "NOW-t").let { if (it.db != null) QeOps.Outcome(it.db, QeOps.createdMessage(0)) else it }
            }
            "entry" -> QeOps.addEntry(st.db, st.db.projects[i(1)].id, s(2), s(3), s(4), "NOW-t")
            "status" -> { val p = st.db.projects[i(1)]; QeOps.Outcome(st.db.copy(projects = st.db.projects.map { if (it.id == p.id) it.copy(status = s(2)) else it }), st.message) }
            "notes" -> { val p = st.db.projects[i(1)]; QeOps.Outcome(st.db.copy(projects = st.db.projects.map { if (it.id == p.id) it.copy(notes = s(2)) else it }), "Project notes saved.") }
            "material" -> QeOps.addMaterial(st.db, s(1), s(2), s(3), s(4), s(5), id)
            "move" -> QeOps.recordMovement(st.db, if (i(1) < 0) "" else st.db.materials[i(1)].id, s(2), s(3), s(4), if (i(5) < 0) "" else st.db.projects[i(5)].id, id, "NOW-t")
            "import" -> {
                if (autosave == null) QeOps.Outcome(null, "No autosaved quotation found.")
                else { st.form = st.form.copy(customer = autosave.customer.name, name = autosave.projectName, value = QeOps.importQuoteValue(autosave)); QeOps.Outcome(null, QeOps.IMPORT_QUOTE_MESSAGE) }
            }
            "machine" -> QeOps.addMachine(st.db, s(1), s(2), s(3))
            "mstatus" -> QeOps.Outcome(QeOps.changeMachine(st.db, st.db.machines[i(1)].id, s(2), null, "NOW-t"), st.message)
            "mworker" -> QeOps.Outcome(QeOps.changeMachine(st.db, st.db.machines[i(1)].id, null, s(2), "NOW-t"), st.message)
            "report" -> { st.month = s(1).let { if (it == "now" || it.isEmpty()) "NOW" else it }; QeOps.Outcome(null, st.message) }
            else -> error(s(0))
        }
        out?.db?.let { st.db = it }
        out?.let { st.message = it.message }
    }

    private fun check(tag: String, st: St, e: JsonObject) {
        val db = e["db"]!!.jsonObject
        val ps = lenient.decodeFromJsonElement(ListSerializer(OpsProject.serializer()), db["projects"]!!)
        val ms = lenient.decodeFromJsonElement(ListSerializer(OpsMaterial.serializer()), db["materials"]!!)
        val mv = lenient.decodeFromJsonElement(ListSerializer(OpsMovement.serializer()), db["movements"]!!)
        val mc = lenient.decodeFromJsonElement(ListSerializer(OpsMachine.serializer()), db["machines"]!!)
        assertEquals("$tag machines", mc, st.db.machines.map { m -> m.copy(history = m.history.map { it.copy(at = "") }) })
        val machineList = st.db.machines.flatMap { m ->
            listOf(QeOps.machineTitle(m), m.status, QeOps.machineDetail(m), "Status") + QeOps.MACHINE_STATUSES + listOf("Assigned worker", QeOps.machineLookup(m))
        }.map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("$tag machine list", e["machineList"]!!.jsonArray.map { unescape(it.jsonPrimitive.content) }, machineList)
        val report = QeOps.reportLines(QeOps.report(st.db, st.month)).flatMap { listOf(it.first.trim(), it.second) } + QeOps.REPORT_NOTE
        assertEquals("$tag report", e["reportBody"]!!.jsonArray.map { unescape(it.jsonPrimitive.content) }, report)
        fun OpsProject.norm() = copy(id = "", created = "", payments = payments.map { it.copy(at = "") }, revisions = revisions.map { it.copy(at = "") }, expenses = expenses.map { it.copy(at = "") })
        assertEquals("$tag projects", ps.map { it.norm() }, st.db.projects.map { it.norm() })
        assertEquals("$tag materials", ms.map { it.copy(id = "") }, st.db.materials.map { it.copy(id = "") })
        assertEquals("$tag movements", mv.map { it.copy(id = "", materialId = "", projectId = "", at = "") }, st.db.movements.map { it.copy(id = "", materialId = "", projectId = "", at = "") })
        assertEquals("$tag message", e["message"]!!.jsonPrimitive.content, st.message)
        val form = e["form"]!!.jsonObject
        assertEquals("$tag form value", form["value"]!!.jsonPrimitive.content, st.form.value)
        assertEquals("$tag form customer", form["customer"]!!.jsonPrimitive.content, st.form.customer)
        assertEquals("$tag form notes", form["notes"]!!.jsonPrimitive.content, st.form.notes)
        // Rendered project figures + history lines (all projects, the V10.1 list is unfiltered here).
        val figures = st.db.projects.flatMap { p ->
            val f = QeOps.figures(st.db, p)
            listOf(f.total, f.received, f.pending, f.cost, f.profit).map(QeOps::fmt) + QeOps.historyLines(p)
        }
        assertEquals("$tag project list", e["projectList"]!!.jsonArray.map { unescape(it.jsonPrimitive.content) }, figures)
        val materials = st.db.materials.flatMap { m ->
            listOf(m.name, QeOps.stockText(st.db, m), QeOps.materialDetail(m)) + (if (QeOps.lowStock(st.db, m)) listOf("Low stock: reorder needed") else emptyList())
        }
        assertEquals("$tag material list", e["materialList"]!!.jsonArray.map { unescape(it.jsonPrimitive.content) }, materials)
        val moves = QeOps.movementLines(st.db).map { it.substringAfter("NOW-t").let { rest -> rest } }
        val expectedMoves = e["movementList"]!!.jsonArray.map { unescape(it.jsonPrimitive.content) }
        if (st.db.movements.isEmpty()) assertEquals("$tag no moves", listOf("No transactions."), expectedMoves.map { "No transactions." }.ifEmpty { listOf("No transactions.") })
        else assertEquals("$tag movement list", expectedMoves, moves)
    }

    @Test fun operationsMatchV101() {
        val cases = Json.parseToJsonElement(res("cases.json")).jsonArray
        val expected = Json.parseToJsonElement(res("expected-v101.json")).jsonArray
        assertEquals(cases.size, expected.size)
        cases.forEachIndexed { n, c0 ->
            val c = c0.jsonObject
            val autosave = c["autosave"]?.let { QeJson.decodeFromJsonElement(WorkspaceState.serializer(), it) }
            val st = St()
            val pending = c["pending"]?.jsonObject
            if (pending != null && c["search"]?.jsonPrimitive?.content == "?import=quotation") {
                val items = pending["quotation"]!!.jsonObject["items"]!!.jsonArray.map { it.jsonObject }.map {
                    QeOps.PendingItem(it["description"]!!.jsonPrimitive.content, it["qty"]!!.jsonPrimitive.content, it["unit"]!!.jsonPrimitive.content, it["unitPrice"]!!.jsonPrimitive.content)
                }
                st.form = OpsProjectForm(pending["customer"]!!.jsonPrimitive.content, pending["name"]!!.jsonPrimitive.content,
                    QeOps.pendingValue(pending["amount"]!!.jsonPrimitive.double), "", QeOps.pendingNotes(pending["notes"]!!.jsonPrimitive.content, items))
                st.message = QeOps.PENDING_IMPORT_MESSAGE
            }
            val steps = expected[n].jsonArray
            check("case $n step 0", st, steps[0].jsonObject)
            c["ops"]?.jsonArray?.forEachIndexed { k, op ->
                apply(st, op.jsonArray, autosave)
                check("case $n step ${k + 1} $op", st, steps[k + 1].jsonObject)
            }
        }
    }

    @Test fun pendingNotesKeepsLiteralBackslashN() {
        val notes = QeOps.pendingNotes("Imported", listOf(QeOps.PendingItem("Cot", "2", "Nos", "50000")))
        assertEquals("Imported\\nCot · 2 · Nos · ₹50000", notes)
        assertEquals(false, notes.contains('\n'))
    }

    @Test fun fmtAndJsString() {
        assertEquals("₹1.01", QeOps.fmt(1.005)); assertEquals("₹0.13", QeOps.fmt(0.125)); assertEquals("₹12,34,567.9", QeOps.fmt(1234567.895))
        assertEquals("₹-0", QeOps.fmt(-0.001)); assertEquals("₹0", QeOps.fmt(-0.0)); assertEquals("₹-1.5", QeOps.fmt(-1.5))
        assertEquals("₹1,23,45,67,89,012.35", QeOps.fmt(123456789012.345)); assertEquals("₹0.3", QeOps.fmt(0.1 + 0.2))
        assertEquals("0.30000000000000004", QeOps.jsString(0.1 + 0.2)); assertEquals("1e+21", QeOps.jsString(1e21)); assertEquals("1e-7", QeOps.jsString(1e-7))
        assertEquals("0.000001", QeOps.jsString(1e-6)); assertEquals("100", QeOps.jsString(100.0)); assertEquals("-2.5", QeOps.jsString(-2.5))
        assertEquals("17.199999999999996", QeOps.jsString(50 - 30.3 + 10.1 - 12.6))
    }
}
