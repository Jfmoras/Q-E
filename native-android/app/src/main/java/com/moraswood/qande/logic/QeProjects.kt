package com.moraswood.qande.logic

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.WorkspaceState

/**
 * Projects & Payments dashboard — pure part. Money figures reuse the V10.1 operations formulas
 * ([QeOps.value] = original + approved revision extras, [QeOps.paid] = Σ payments); remaining = total − Σ payments.
 */
object QeProjects {
    const val RUNNING = "running"
    const val FINISHED = "finished"
    const val DELETED = "deleted"
    val STATUSES = listOf(RUNNING, FINISHED, DELETED)
    fun statusLabel(s: String) = when (s) { FINISHED -> "Finished"; DELETED -> "Deleted"; else -> "Running" }
    fun status(s: String?) = if (s in STATUSES) s!! else RUNNING

    const val USER = "user"
    const val CLIENT = "client"
    fun authorLabel(a: String) = if (a == CLIENT) "Client" else "User"

    /* ---------- payments ---------- */

    data class Money(val total: Double, val paid: Double, val remaining: Double, val pct: Int)

    fun money(total: Double, payments: List<Double>): Money {
        val paid = payments.sum()
        val pct = if (total > 0) Math.round(paid / total * 100).toInt().coerceIn(0, 100) else 0
        return Money(total, paid, total - paid, pct)
    }

    fun money(p: OpsProject): Money = money(QeOps.value(p), p.payments.map { it.amount })

    /** Payments oldest first (ISO `at` sorts chronologically). */
    fun chronological(p: OpsProject): List<OpsEntry> = p.payments.sortedBy { it.at }

    /** A manually dated payment's `at`: the chosen day at 12:00 UTC (stays on that date in IST). */
    fun paymentAt(date: String): String? =
        runCatching { java.time.LocalDate.parse(date.trim()) }.getOrNull()?.let { "${it}T12:00:00.000Z" }

    fun validAmount(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /** Summary card: collected in [month] (yyyy-MM) and overall collected vs value of non-deleted projects. */
    data class Summary(val month: String, val collectedThisMonth: Double, val collected: Double, val value: Double, val pct: Int, val running: Int)

    fun summary(projects: List<OpsProject>, statusOf: (String) -> String, month: String): Summary {
        val live = projects.filter { statusOf(it.id) != DELETED }
        val m = money(live.sumOf { QeOps.value(it) }, live.flatMap { p -> p.payments.map { it.amount } })
        val thisMonth = live.flatMap { it.payments }.filter { it.at.take(7) == month }.sumOf { it.amount }
        return Summary(month, thisMonth, m.paid, m.total, m.pct, live.count { statusOf(it.id) == RUNNING })
    }

    /* ---------- status sections ---------- */

    data class Sections<T>(val running: List<T>, val finished: List<T>, val deleted: List<T>)

    /** Running first (newest first inside each section), then finished, then deleted. */
    fun <T> sections(items: List<T>, status: (T) -> String, created: (T) -> String): Sections<T> {
        val sorted = items.sortedByDescending(created)
        return Sections(sorted.filter { status(status(it)) == RUNNING }, sorted.filter { status(status(it)) == FINISHED }, sorted.filter { status(status(it)) == DELETED })
    }

    /** Remaining after deleting the payment at [index] (t47u: payments can be deleted). */
    fun withoutPayment(total: Double, payments: List<Double>, index: Int): Money =
        money(total, payments.filterIndexed { i, _ -> i != index })

    /* ---------- comments (editable since t47u) ---------- */

    data class Comment(val id: String, val author: String, val text: String, val at: String, val editedAt: String = "")

    /** Adds a comment; null when the text is blank / author unknown. */
    fun append(list: List<Comment>, id: String, author: String, text: String, at: String): List<Comment>? {
        val t = text.trim()
        if (t.isEmpty() || author !in listOf(USER, CLIENT)) return null
        return list + Comment(id, author, t, at)
    }

    /** Edits one comment's text (author and original time kept, [editedAt] stamped); null when blank / unknown id. */
    fun edit(list: List<Comment>, id: String, text: String, editedAt: String): List<Comment>? {
        val t = text.trim()
        if (t.isEmpty() || list.none { it.id == id }) return null
        return list.map { if (it.id == id) it.copy(text = t, editedAt = editedAt) else it }
    }

    fun delete(list: List<Comment>, id: String): List<Comment> = list.filterNot { it.id == id }

    /* ---------- project revisions (R0, R1, ...) ---------- */

    @kotlinx.serialization.Serializable
    data class SnapItem(val name: String, val qty: String = "", val unit: String = "", val price: Double = 0.0, val sizes: String = "", val wood: String = "")
    @kotlinx.serialization.Serializable
    data class SnapHardware(val name: String, val qty: String = "", val note: String = "")
    /** Everything a frozen revision shows (read-only). */
    @kotlinx.serialization.Serializable
    data class Snapshot(
        val revision: Int, val total: Double, val paid: Double, val description: String, val notes: String, val startDate: String,
        val items: List<SnapItem>, val hardware: List<SnapHardware>, val at: String,
    )

    private val snapJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(s: Snapshot): String = snapJson.encodeToString(Snapshot.serializer(), s)
    fun decode(raw: String): Snapshot? = runCatching { snapJson.decodeFromString(Snapshot.serializer(), raw) }.getOrNull()

    fun label(revision: Int) = "R$revision"

    /**
     * "Revise project": freezes the current state as R[current] and starts R[current]+1. The new total is reached by
     * adding a V10.1 revision entry of [delta] (= newTotal − current total, may be negative when extras are removed),
     * so QeOps.value — and with it the Operations screen, remaining amount and summary card — follows the latest revision.
     */
    data class Revise(val next: Int, val delta: Double, val note: String)

    fun revise(current: Int, currentTotal: Double, newTotalText: String, reason: String): Revise? {
        val t = newTotalText.trim().toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() } ?: return null
        val next = current + 1
        val note = (label(next) + ": " + reason.trim().ifEmpty { "Client revision" })
        return Revise(next, Math.round((t - currentTotal) * 100) / 100.0, note)
    }

    /* ---------- Confirm as Project (quotation → project) ---------- */

    data class Item(val name: String, val qty: String, val unit: String, val price: Double, val sizes: String, val wood: String)
    data class Draft(val name: String, val customer: String, val total: Double, val description: String, val notes: String, val items: List<Item>)

    private fun n(s: String) = s.trim().toDoubleOrNull() ?: 0.0

    fun sizes(item: CalcItem?): String = item?.rows.orEmpty().filter { it.l.isNotBlank() || it.w.isNotBlank() || it.t.isNotBlank() }.joinToString("\n") { r ->
        "${r.l} ${r.lUnit.ifEmpty { "ft" }} × ${r.w} ${r.wUnit.ifEmpty { "in" }} × ${r.t} ${r.tUnit.ifEmpty { "in" }} × ${r.q.ifBlank { "0" }} pcs (${r.mode.ifEmpty { "CFT" }})"
    }

    fun wood(species: String?, item: CalcItem?): String {
        val t = item?.let { QeMath.itemTotals(it) }
        val vol = t?.let { listOfNotNull(if (it.cft != 0.0) QeFormat.toFixed(it.cft, 3) + " CFT" else null, if (it.kol != 0.0) QeFormat.toFixed(it.kol, 3) + " KOL" else null).joinToString(" · ") }.orEmpty()
        return listOf(species?.trim().orEmpty(), vol).filter { it.isNotEmpty() }.joinToString(" — ")
    }

    /**
     * Active quotation lines → project items. Each line is matched to the estimation item it came from (Send to
     * Quotation uses the item name as description; same name first, else the same index); total = the quotation grand
     * total exactly as V10.1's "Import current Q&E quotation details" computes it.
     */
    fun fromQuotation(s: WorkspaceState): Draft {
        val est = s.estimationItems
        val used = mutableSetOf<Int>()
        val lines = s.quoteItems.filter { it.description.isNotBlank() || n(it.unitPrice) != 0.0 }
        val items = lines.mapIndexed { i, q ->
            val byName = est.indices.firstOrNull { it !in used && est[it].name.trim().isNotEmpty() && est[it].name.trim() == q.description.trim() }
            val idx = byName ?: i.takeIf { it < est.size && it !in used && lines.size == est.size }
            idx?.let { used += it }
            val e = idx?.let { est[it] }
            // V10.1 line amount: Number(qty) × Number(unitPrice) (blank qty counts 0, as in the quotation total).
            Item(q.description.trim(), q.qty, q.unit, n(q.qty) * n(q.unitPrice), sizes(e), wood(q.species ?: s.quoteOptions.getOrNull(s.activeQuoteOption)?.species, e))
        }
        val notes = s.quote.notes.filter { it.isNotBlank() }.joinToString("\n")
        val name = s.projectName.ifBlank { s.customer.name }
        val description = items.joinToString(", ") { it.name }.ifBlank { name }
        return Draft(name, s.customer.name, n(QeOps.importQuoteValue(s)), description, notes, items)
    }
}
