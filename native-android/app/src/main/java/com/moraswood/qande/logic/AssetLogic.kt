package com.moraswood.qande.logic

import com.moraswood.qande.data.AssetCorrectionEntity
import com.moraswood.qande.data.AssetDecisionEntity
import com.moraswood.qande.data.AssetEntity
import com.moraswood.qande.data.AssetIssueEntity
import com.moraswood.qande.data.AssetReturnEntity
import java.time.LocalDate

/** Feature C status, derived on the fly from the append-only logs (never stored). */
enum class AssetStatus(val label: String) { InStock("In stock"), Issued("Issued"), Overdue("Overdue") }

/** The logs as loaded from Room. */
data class AssetLogs(
    val assets: List<AssetEntity> = emptyList(),
    val issues: List<AssetIssueEntity> = emptyList(),
    val returns: List<AssetReturnEntity> = emptyList(),
    val corrections: List<AssetCorrectionEntity> = emptyList(),
    val decisions: List<AssetDecisionEntity> = emptyList(),
)

/** An issue with approved corrections applied. */
data class EffectiveIssue(val raw: AssetIssueEntity, val worker: String, val issuedOn: String, val dueBack: String?, val notes: String, val void: Boolean)
data class EffectiveReturn(val raw: AssetReturnEntity, val returnedOn: String, val condition: String, val void: Boolean)

data class AssetState(
    val asset: AssetEntity,
    val status: AssetStatus,
    /** The open (unreturned) issue, if any. */
    val open: EffectiveIssue?,
    /** Last person the asset was issued to (open or returned), for "who had it last". */
    val lastWorker: String?,
)

data class TimelineEntry(val date: String, val at: String, val kind: String, val text: String, val struck: Boolean, val targetType: String? = null, val targetId: Long? = null)

object AssetLogic {
    val CATEGORIES = listOf("machine", "tool", "accessory")
    const val DEFAULT_DUE_DAYS = 2L

    fun defaultDueBack(today: LocalDate): String = today.plusDays(DEFAULT_DUE_DAYS).toString()

    fun decision(logs: AssetLogs, correctionId: Long): AssetDecisionEntity? = logs.decisions.firstOrNull { it.correctionId == correctionId }

    /** Requests without a decision (the owner review queue), oldest first. */
    fun pending(logs: AssetLogs): List<AssetCorrectionEntity> = logs.corrections.filter { decision(logs, it.id) == null }.sortedBy { it.id }

    /** Approved corrections for one entry, in approval order (later approvals win). */
    private fun approved(logs: AssetLogs, type: String, id: Long): List<AssetCorrectionEntity> =
        logs.corrections.filter { it.targetType == type && it.targetId == id }
            .mapNotNull { c -> decision(logs, c.id)?.takeIf { it.decision == "approved" }?.let { d -> d to c } }
            .sortedBy { it.first.id }.map { it.second }

    fun effectiveIssue(logs: AssetLogs, i: AssetIssueEntity): EffectiveIssue =
        approved(logs, "issue", i.id).fold(EffectiveIssue(i, i.worker, i.issuedOn, i.dueBack, i.notes, false)) { e, c ->
            e.copy(
                worker = c.worker ?: e.worker, issuedOn = c.issuedOn ?: e.issuedOn,
                dueBack = if (c.clearDueBack) null else c.dueBack ?: e.dueBack,
                notes = c.notes ?: e.notes, void = e.void || c.voidEntry,
            )
        }

    fun effectiveReturn(logs: AssetLogs, r: AssetReturnEntity): EffectiveReturn =
        approved(logs, "return", r.id).fold(EffectiveReturn(r, r.returnedOn, r.condition, false)) { e, c ->
            e.copy(returnedOn = c.returnedOn ?: e.returnedOn, condition = c.condition ?: e.condition, void = e.void || c.voidEntry)
        }

    private fun returnOf(logs: AssetLogs, issueId: Long): EffectiveReturn? =
        logs.returns.filter { it.issueId == issueId }.map { effectiveReturn(logs, it) }.lastOrNull { !it.void }

    /**
     * in stock: no open issue. issued: an open issue with no due-back date or today <= due-back.
     * overdue: an open issue whose due-back date is before today (the due-back day itself is not overdue).
     * Voided issues / returns (approved corrections) are ignored.
     */
    fun state(logs: AssetLogs, asset: AssetEntity, today: LocalDate): AssetState {
        val issues = logs.issues.filter { it.assetId == asset.id }.map { effectiveIssue(logs, it) }.filter { !it.void }
            .sortedWith(compareBy({ it.issuedOn }, { it.raw.id }))
        val open = issues.lastOrNull { returnOf(logs, it.raw.id) == null }
        val status = when {
            open == null -> AssetStatus.InStock
            open.dueBack != null && runCatching { today.isAfter(LocalDate.parse(open.dueBack)) }.getOrDefault(false) -> AssetStatus.Overdue
            else -> AssetStatus.Issued
        }
        return AssetState(asset, status, open, issues.lastOrNull()?.worker)
    }

    fun states(logs: AssetLogs, today: LocalDate) = logs.assets.map { state(logs, it, today) }

    /** Overdue alerts: (asset, open issue) for every overdue asset. */
    fun overdue(logs: AssetLogs, today: LocalDate): List<AssetState> = states(logs, today).filter { it.status == AssetStatus.Overdue }

    private fun date(s: String) = runCatching { LocalDate.parse(s.trim()) }.getOrNull()

    /* ---------- validation (returns an error message or null) ---------- */
    fun validateAsset(name: String, category: String, purchaseDate: String, cost: String): String? = when {
        name.isBlank() -> "Enter asset name."
        category !in CATEGORIES -> "Choose a category."
        purchaseDate.isNotBlank() && date(purchaseDate) == null -> "Purchase date must be YYYY-MM-DD."
        cost.isNotBlank() && (cost.trim().toDoubleOrNull() == null || cost.trim().toDouble() < 0) -> "Cost must be a positive number."
        else -> null
    }

    fun validateIssue(logs: AssetLogs, asset: AssetEntity, worker: String, issuedOn: String, dueBack: String, today: LocalDate): String? {
        val d = date(issuedOn)
        return when {
            worker.isBlank() -> "Enter the worker's name."
            d == null -> "Issue date must be YYYY-MM-DD."
            dueBack.isNotBlank() && date(dueBack) == null -> "Due-back date must be YYYY-MM-DD."
            dueBack.isNotBlank() && date(dueBack)!!.isBefore(d) -> "Due-back date cannot be before the issue date."
            state(logs, asset, today).open != null -> "This asset is already issued. Record its return first."
            else -> null
        }
    }

    fun validateReturn(open: EffectiveIssue?, returnedOn: String): String? {
        val d = date(returnedOn)
        return when {
            open == null -> "This asset is not issued."
            d == null -> "Return date must be YYYY-MM-DD."
            date(open.issuedOn)?.let { d.isBefore(it) } == true -> "Return date cannot be before the issue date."
            else -> null
        }
    }

    fun validateCorrection(c: AssetCorrectionEntity): String? {
        val dates = listOfNotNull(c.issuedOn, c.dueBack, c.returnedOn)
        val changes = listOfNotNull(c.worker, c.issuedOn, c.dueBack, c.notes, c.returnedOn, c.condition).isNotEmpty() || c.clearDueBack || c.voidEntry
        return when {
            c.reason.isBlank() -> "Give a reason for the correction."
            !changes -> "Change at least one field."
            dates.any { date(it) == null } -> "Dates must be YYYY-MM-DD."
            c.worker != null && c.worker.isBlank() -> "Worker name cannot be empty."
            else -> null
        }
    }

    /* ---------- history timeline (oldest first) ---------- */
    fun timeline(logs: AssetLogs, assetId: Long): List<TimelineEntry> {
        val out = mutableListOf<TimelineEntry>()
        val issues = logs.issues.filter { it.assetId == assetId }
        for (i in issues) {
            val e = effectiveIssue(logs, i)
            out += TimelineEntry(e.issuedOn, i.createdAt, "Issued", "Issued to ${e.worker}" + (e.dueBack?.let { " · due back $it" } ?: "") + (e.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), e.void, "issue", i.id)
            for (r in logs.returns.filter { it.issueId == i.id }) {
                val er = effectiveReturn(logs, r)
                out += TimelineEntry(er.returnedOn, r.createdAt, "Returned", "Returned by ${e.worker}" + (er.condition.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), er.void, "return", r.id)
            }
        }
        val ids = issues.map { "issue" to it.id }.toSet() + logs.returns.filter { r -> issues.any { it.id == r.issueId } }.map { "return" to it.id }
        for (c in logs.corrections.filter { (it.targetType to it.targetId) in ids }) {
            val d = decision(logs, c.id)
            out += TimelineEntry(c.requestedAt.take(10), c.requestedAt, "Correction", "Correction requested for ${c.targetType} #${c.targetId}: ${describe(c)} (${c.reason})", false)
            if (d != null) out += TimelineEntry(d.decidedAt.take(10), d.decidedAt, if (d.decision == "approved") "Approved" else "Rejected", "Owner ${d.decision} correction #${c.id}", false)
        }
        return out.sortedWith(compareBy({ it.date }, { it.at }))
    }

    fun describe(c: AssetCorrectionEntity): String = listOfNotNull(
        c.worker?.let { "worker → $it" }, c.issuedOn?.let { "issued → $it" },
        if (c.clearDueBack) "no due-back date" else c.dueBack?.let { "due back → $it" },
        c.notes?.let { "notes → $it" }, c.returnedOn?.let { "returned → $it" }, c.condition?.let { "condition → $it" },
        if (c.voidEntry) "void entry" else null,
    ).joinToString(", ")
}
