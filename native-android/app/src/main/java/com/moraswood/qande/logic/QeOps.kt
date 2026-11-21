package com.moraswood.qande.logic

import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeMath.num
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/** operations.js `{amount,note,at}` entry of payments / revisions / expenses. */
@Serializable
data class OpsEntry(val amount: Double = 0.0, val note: String = "", val at: String = "")

/** operations.js project record. */
@Serializable
data class OpsProject(
    val id: String = "", val name: String = "", val customer: String = "", val original: Double = 0.0,
    val status: String = "Saved",
    val payments: List<OpsEntry> = emptyList(), val revisions: List<OpsEntry> = emptyList(), val expenses: List<OpsEntry> = emptyList(),
    val notes: String = "", val created: String = "",
)

@Serializable
data class OpsMaterial(val id: String = "", val name: String = "", val unit: String = "", val category: String = "", val rate: Double = 0.0, val min: Double = 0.0)

@Serializable
data class OpsMovement(
    val id: String = "", val materialId: String = "", val qty: Double = 0.0, val type: String = "", val worker: String = "",
    val projectId: String = "", val rate: Double = 0.0, val at: String = "",
)

/** machine `history[]` entry `{at,status,worker}`. */
@Serializable
data class OpsMachineEvent(val at: String = "", val status: String = "", val worker: String = "")

/** operations.js machine record. */
@Serializable
data class OpsMachine(
    val id: String = "", val name: String = "", val serial: String = "", val worker: String = "", val status: String = "Available",
    val history: List<OpsMachineEvent> = emptyList(),
)

/** `mwi-operations-v1` (audit lives in its own table). */
data class OpsDb(
    val projects: List<OpsProject> = emptyList(),
    val materials: List<OpsMaterial> = emptyList(),
    val movements: List<OpsMovement> = emptyList(),
    val machines: List<OpsMachine> = emptyList(),
)

/** Create-project form (`#pCustomer`, `#pName`, `#pValue`, `#pPaid`, `#pNotes`). */
data class OpsProjectForm(val customer: String = "", val name: String = "", val value: String = "", val paid: String = "", val notes: String = "")

/** Exact ports of the operations.js helpers and handlers (V10.1). */
object QeOps {
    val STATUSES = listOf("Saved", "Running", "On Hold", "Completed", "Cancelled")
    val CATEGORIES = listOf("Hardware", "Adhesives & Fillers", "Polish & Chemicals", "Sanding Discs", "Timber", "Other")
    const val MAX_FILE = 15L * 1024 * 1024

    /** `stock(id)` */
    fun stock(db: OpsDb, id: String): Double =
        db.movements.filter { it.materialId == id }.fold(0.0) { s, m -> s + if (m.type == "purchase" || m.type == "return") m.qty else -m.qty }

    /** `cost(id)`: issued minus returned material for a project. */
    fun cost(db: OpsDb, id: String): Double =
        db.movements.filter { it.projectId == id && it.type == "issue" }.fold(0.0) { s, m -> s + m.qty * m.rate } -
            db.movements.filter { it.projectId == id && it.type == "return" }.fold(0.0) { s, m -> s + m.qty * m.rate }

    fun paid(p: OpsProject): Double = p.payments.fold(0.0) { s, x -> s + x.amount }
    fun value(p: OpsProject): Double = p.original + p.revisions.fold(0.0) { s, x -> s + x.amount }
    fun expense(db: OpsDb, p: OpsProject): Double = p.expenses.fold(0.0) { s, x -> s + x.amount } + cost(db, p.id)

    /** Project card figures: total, received, pending, recorded cost, profit before unrecorded costs. */
    data class Figures(val total: Double, val received: Double, val pending: Double, val cost: Double, val profit: Double)
    fun figures(db: OpsDb, p: OpsProject): Figures {
        val v = value(p); val pd = paid(p); val e = expense(db, p)
        return Figures(v, pd, v - pd, e, v - e)
    }

    /** `fmt(n)` = "₹" + Number(n||0).toLocaleString("en-IN",{maximumFractionDigits:2}). */
    fun fmt(n: Double): String {
        val x = if (n.isNaN() || n == 0.0) 0.0 else n
        if (x.isInfinite()) return "₹" + (if (x < 0) "-∞" else "∞")
        val bd = BigDecimal(x.toString()).abs().setScale(2, RoundingMode.HALF_UP)
        val intPart = bd.setScale(0, RoundingMode.DOWN).toPlainString()
        val frac = bd.subtract(BigDecimal(intPart)).movePointRight(2).toInt()
        val fracText = when { frac == 0 -> ""; frac % 10 == 0 -> "." + (frac / 10); else -> "." + frac.toString().padStart(2, '0') }
        return "₹" + (if (x < 0) "-" else "") + QeFormat.groupIndian(intPart) + fracText
    }

    /** JS `String(number)` (shortest round-trip digits, exponent form outside 1e-7 ≤ |x| < 1e21). */
    fun jsString(d: Double): String {
        if (d.isNaN()) return "NaN"
        if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
        if (d == 0.0) return "0"
        val bd = BigDecimal(d.toString()).stripTrailingZeros()
        val digits = bd.unscaledValue().abs().toString()
        val k = digits.length
        val n = k - bd.scale() // decimal point position
        val sign = if (d < 0) "-" else ""
        return sign + when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val e = n - 1
                val exp = if (e >= 0) "+$e" else "$e"
                if (k == 1) digits + "e" + exp else digits[0] + "." + digits.substring(1) + "e" + exp
            }
        }
    }

    /** `today().slice(0,16).replace("T"," ")` for the movement list. */
    fun shortAt(at: String) = at.take(16).replace("T", " ")

    /** operations.js `uid()` = Date.now().toString(36) + 5 random base-36 chars. */
    fun uid(nowMs: Long = System.currentTimeMillis()): String =
        java.lang.Long.toString(nowMs, 36) + (1..5).map { "0123456789abcdefghijklmnopqrstuvwxyz"[(Math.random() * 36).toInt()] }.joinToString("")

    /* ---------- handlers (return the new db, or null when nothing changes, plus the #message text) ---------- */
    data class Outcome(val db: OpsDb?, val message: String, val project: OpsProject? = null)

    /** `#createProject` before the attachments loop. */
    fun createProject(db: OpsDb, f: OpsProjectForm, id: String, now: String): Outcome {
        val name = f.name.trim(); val customer = f.customer.trim()
        if (name.isEmpty() || customer.isEmpty()) return Outcome(null, "Enter customer and project name.")
        val amount = num(f.value); val advance = num(f.paid)
        if (amount < 0 || advance < 0) return Outcome(null, "Amounts cannot be negative.")
        val p = OpsProject(
            id = id, name = name, customer = customer, original = amount, status = "Saved",
            payments = if (advance != 0.0) listOf(OpsEntry(advance, "Opening balance imported", now)) else emptyList(),
            notes = f.notes, created = now,
        )
        return Outcome(db.copy(projects = db.projects + p), "", p)
    }

    fun createdMessage(uploaded: Int) = "Project created with $uploaded attachment(s)."

    /** `[data-add]`: kind payment | revision | expense. */
    fun addEntry(db: OpsDb, projectId: String, amountText: String, kind: String, note: String, now: String): Outcome {
        val p = db.projects.firstOrNull { it.id == projectId }
        val amount = num(amountText)
        if (p == null || amount <= 0) return Outcome(null, "Enter a positive amount.")
        val e = OpsEntry(amount, note, now)
        val np = when (kind) { "payment" -> p.copy(payments = p.payments + e); "revision" -> p.copy(revisions = p.revisions + e); else -> p.copy(expenses = p.expenses + e) }
        return Outcome(db.copy(projects = db.projects.map { if (it.id == projectId) np else it }), "Entry recorded.", np)
    }

    /** `#addMaterial` */
    fun addMaterial(db: OpsDb, name0: String, unit0: String, category: String, rate: String, min: String, id: String): Outcome {
        val name = name0.trim(); val unit = unit0.trim()
        if (name.isEmpty() || unit.isEmpty()) return Outcome(null, "Enter material name and unit.")
        return Outcome(db.copy(materials = db.materials + OpsMaterial(id, name, unit, category, num(rate), num(min))), "Material registered.")
    }

    /** `#recordMovement` */
    fun recordMovement(db: OpsDb, materialId: String, qtyText: String, type: String, worker: String, projectId: String, id: String, now: String): Outcome {
        val m = db.materials.firstOrNull { it.id == materialId }
        val qty = num(qtyText)
        if (m == null || qty <= 0) return Outcome(null, "Select a material and enter a positive quantity.")
        if (type != "purchase" && qty > stock(db, m.id)) return Outcome(null, "Insufficient stock for this transaction.")
        if (type == "return" && projectId.isEmpty()) return Outcome(null, "Select the project receiving the return.")
        val mv = OpsMovement(id, m.id, qty, type, worker.trim(), projectId, m.rate, now)
        return Outcome(db.copy(movements = db.movements + mv), "Transaction recorded and locked. Inventory updated.")
    }

    /** `#importQuote` from the autosaved workspace: returns the `#pValue` text (grand.toFixed(2)). */
    fun importQuoteValue(q: WorkspaceState): String {
        val items = q.quoteItems
        val subtotal = items.fold(0.0) { s, x -> s + num(x.qty) * num(x.unitPrice) }
        val discount = minOf(subtotal, if (q.quote.discountType == "percent") subtotal * num(q.quote.discountValue) / 100 else num(q.quote.discountValue))
        val taxable = subtotal - discount
        val grand = taxable * (1 + num(q.quote.gstPercent) / 100)
        return QeFormat.toFixed(grand, 2)
    }
    const val IMPORT_QUOTE_MESSAGE = "Imported quotation details into the form. Verify the amount and click Create project. Original quotation remains unchanged."
    const val PENDING_IMPORT_MESSAGE = "Quotation imported. Add photos or documents if needed, then tap Create project & attach files."

    /** Pending `mwi-project-import-pending` item line: `[description, qty, unit, "₹"+unitPrice].join(" · ")`. */
    data class PendingItem(val description: String, val qty: String, val unit: String, val unitPrice: String)

    /**
     * `#pNotes` after a pending quotation import. V10.1 quirk kept on purpose: the JS source joins with `"\\n"`,
     * i.e. a literal backslash + n, not a line break.
     */
    fun pendingNotes(notes: String, items: List<PendingItem>): String =
        notes + "\\n" + items.joinToString("\\n") { listOf(it.description, it.qty, it.unit, "₹" + it.unitPrice).joinToString(" · ") }

    /** `#pValue` after a pending import: Number(pending.amount||0).toFixed(2). */
    fun pendingValue(amount: Double): String = QeFormat.toFixed(if (amount.isNaN()) 0.0 else amount, 2)

    /** Payment, revision & expense history lines. */
    fun historyLines(p: OpsProject): List<String> =
        p.payments.map { "Payment " + fmt(it.amount) + " · " + it.note } +
            p.revisions.map { "Revision " + fmt(it.amount) + " · " + it.note } +
            p.expenses.map { "Expense " + fmt(it.amount) + " · " + it.note }

    /** `render()` search filter. */
    fun matches(p: OpsProject, query: String) = (p.name + " " + p.customer + " " + p.status).lowercase().contains(query.lowercase())

    /** Material card: "<stock> <unit> remaining", "<category> · <fmt(rate)>/<unit> · reorder at <min>", low-stock flag. */
    fun stockText(db: OpsDb, m: OpsMaterial) = jsString(stock(db, m.id)) + " " + m.unit + " remaining"
    fun materialDetail(m: OpsMaterial) = m.category + " · " + fmt(m.rate) + "/" + m.unit + " · reorder at " + jsString(m.min)
    fun lowStock(db: OpsDb, m: OpsMaterial) = stock(db, m.id) <= m.min

    /** Movement history line (newest first, at most 100). */
    fun movementLines(db: OpsDb): List<String> = db.movements.reversed().take(100).map { m ->
        shortAt(m.at) + " · " + m.type + " · " + (db.materials.firstOrNull { it.id == m.materialId }?.name ?: "Unknown") + " · " +
            jsString(m.qty) + " · " + m.worker.ifEmpty { "Owner" } + " · " + (db.projects.firstOrNull { it.id == m.projectId }?.name ?: "No project")
    }

    /* ---------- Machines & Tools ---------- */
    val MACHINE_STATUSES = listOf("Available", "Assigned", "In Use", "Under Repair", "Needs Maintenance", "Missing", "Retired")

    /** `#addMachine`: name is trimmed, serial / worker are stored as typed. */
    fun addMachine(db: OpsDb, name0: String, serial: String, worker: String): Outcome {
        val name = name0.trim()
        if (name.isEmpty()) return Outcome(null, "Enter machine name.")
        val id = "MWI-" + (db.machines.size + 1).toString().padStart(4, '0')
        return Outcome(db.copy(machines = db.machines + OpsMachine(id, name, serial, worker, "Available")), "Machine registered: $id")
    }

    /** `[data-machine]` / `[data-worker]` change: set status or worker, then push `{at,status,worker}` (no message). */
    fun changeMachine(db: OpsDb, id: String, status: String?, worker: String?, now: String): OpsDb? {
        val m = db.machines.firstOrNull { it.id == id } ?: return null
        val c = if (status != null) m.copy(status = status) else m.copy(worker = worker ?: m.worker)
        val n = c.copy(history = c.history + OpsMachineEvent(now, c.status, c.worker))
        return db.copy(machines = db.machines.map { if (it.id == id) n else it })
    }

    /** Machine card: "<id> · <name>", "Serial: <serial> · Assigned: <worker||None>", lookup note. */
    fun machineTitle(m: OpsMachine) = m.id + " · " + m.name
    fun machineDetail(m: OpsMachine) = "Serial: " + m.serial + " · Assigned: " + m.worker.ifEmpty { "None" }
    fun machineLookup(m: OpsMachine) = "Machine ID for manual lookup or future QR labels: " + m.id

    /* ---------- Monthly Reports ---------- */
    data class Report(val receipts: Double, val purchases: Double, val used: Double, val other: Double)

    /** `report()`: month = `#reportMonth` value (yyyy-MM) or the current UTC month; records match by `at.startsWith(month)`. */
    fun report(db: OpsDb, month: String): Report {
        val pays = db.projects.flatMap { it.payments }.filter { it.at.startsWith(month) }.fold(0.0) { s, x -> s + x.amount }
        val purchases = db.movements.filter { it.at.startsWith(month) && it.type == "purchase" }.fold(0.0) { s, x -> s + x.qty * x.rate }
        val used = db.movements.filter { it.at.startsWith(month) && it.type == "issue" }.fold(0.0) { s, x -> s + x.qty * x.rate }
        val other = db.projects.flatMap { it.expenses }.filter { it.at.startsWith(month) }.fold(0.0) { s, x -> s + x.amount }
        return Report(pays, purchases, used, other)
    }

    /** `#reportBody` lines. */
    fun reportLines(r: Report) = listOf(
        "Customer receipts: " to fmt(r.receipts),
        "Inventory purchases: " to fmt(r.purchases),
        "Material issued (cost): " to fmt(r.used),
        "Other recorded expenses: " to fmt(r.other),
    )
    const val REPORT_NOTE = "Purchases and material consumption are separate measures, not additive project expenses."
}
