package com.moraswood.qande.logic

import com.moraswood.qande.data.JobMetaEntity
import com.moraswood.qande.data.SavedProjectEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Feature P — delivery calendar (custom month grid, no libraries). */
object QeCalendar {
    enum class Kind { DELIVERY, COMPLETED }
    enum class Status { UPCOMING, DUE_TODAY, OVERDUE, DONE }

    data class Entry(
        val rootId: String, val name: String, val revision: Int, val jobUuid: String,
        val date: LocalDate, val kind: Kind, val status: Status,
        val deliveryDate: LocalDate?, val completedAt: LocalDate?,
    )

    val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun parse(s: String?): LocalDate? = s?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    /** Cells of a month starting on Monday: leading/trailing nulls pad whole weeks. */
    fun monthGrid(month: YearMonth): List<LocalDate?> {
        val lead = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = List<LocalDate?>(lead) { null } + days
        return cells + List((7 - cells.size % 7) % 7) { null }
    }

    fun status(delivery: LocalDate?, completed: LocalDate?, today: LocalDate): Status = when {
        completed != null -> Status.DONE
        delivery == null -> Status.UPCOMING
        delivery.isBefore(today) -> Status.OVERDUE
        delivery == today -> Status.DUE_TODAY
        else -> Status.UPCOMING
    }

    /**
     * One entry on the delivery date of each scheduled job, plus one on its completion date. The `jobs` row's date wins;
     * without one, the delivery date saved in the latest snapshot is used.
     */
    fun entries(saved: List<SavedProjectEntity>, meta: List<JobMetaEntity>, today: LocalDate): List<Entry> {
        val byUuid = meta.associateBy { it.jobUuid }
        return QeRevisions.jobs(saved).flatMap { job ->
            val x = job.latest
            val uuid = job.revisions.firstOrNull { it.jobUuid.isNotBlank() }?.jobUuid.orEmpty()
            val row = byUuid[uuid]
            val delivery = parse(row?.deliveryDate) ?: if (row == null) parse(QeClients.decode(x)?.deliveryDate) else null
            val done = parse(row?.completedAt)
            val st = status(delivery, done, today)
            listOfNotNull(
                delivery?.let { Entry(job.rootId, x.name, x.revision, uuid, it, Kind.DELIVERY, st, delivery, done) },
                done?.let { Entry(job.rootId, x.name, x.revision, uuid, it, Kind.COMPLETED, Status.DONE, delivery, done) },
            )
        }
    }

    fun byDay(entries: List<Entry>): Map<LocalDate, List<Entry>> = entries.groupBy { it.date }.mapValues { (_, v) -> v.sortedBy { it.name.lowercase() } }

    /** Overdue jobs (any month), oldest first — shown above the grid. */
    fun overdue(entries: List<Entry>): List<Entry> = entries.filter { it.kind == Kind.DELIVERY && it.status == Status.OVERDUE }.sortedBy { it.date }

    fun monthTitle(m: YearMonth): String = m.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + m.year
}
