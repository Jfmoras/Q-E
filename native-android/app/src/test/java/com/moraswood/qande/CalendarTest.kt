package com.moraswood.qande

import com.moraswood.qande.data.JobMetaEntity
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeCalendar
import com.moraswood.qande.logic.QeCalendar.Kind
import com.moraswood.qande.logic.QeCalendar.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarTest {
    @Test fun monthGridStartsMondayAndPadsWeeks() {
        val g = QeCalendar.monthGrid(YearMonth.of(2026, 11)) // 1 Nov 2026 is a Sunday
        assertEquals(6, g.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), g[6])
        assertEquals(0, g.size % 7)
        assertEquals(30, g.count { it != null })
    }

    @Test fun february2027() {
        val g = QeCalendar.monthGrid(YearMonth.of(2027, 2))
        assertEquals(LocalDate.of(2027, 2, 1), g[0])
        assertEquals(28, g.size)
    }

    private fun row(root: String, uuid: String, delivery: String?) = SavedProjectEntity(
        root, root, 0, "Job $root", "2026-11-20T00:00:00.000Z",
        QeJson.encodeToString(WorkspaceState.serializer(), WorkspaceState(deliveryDate = delivery)), 0, null, uuid,
    )

    @Test fun entriesStatusesAndOverdue() {
        val today = LocalDate.of(2026, 11, 21)
        val saved = listOf(
            row("a", "ua", "2026-11-18"),            // overdue (snapshot only)
            row("b", "ub", null),                    // jobs row date, due today
            row("c", "uc", "2026-11-10"),            // completed late
            row("d", "ud", "2026-11-30"),            // upcoming
            row("e", "ue", "2026-11-05"),            // jobs row without date wins → unscheduled
        )
        val meta = listOf(
            JobMetaEntity("ub", deliveryDate = "2026-11-21"),
            JobMetaEntity("uc", deliveryDate = "2026-11-10", completedAt = "2026-11-12"),
            JobMetaEntity("ue"),
        )
        val e = QeCalendar.entries(saved, meta, today)
        fun st(root: String) = e.first { it.rootId == root && it.kind == Kind.DELIVERY }.status
        assertEquals(Status.OVERDUE, st("a"))
        assertEquals(Status.DUE_TODAY, st("b"))
        assertEquals(Status.DONE, st("c"))
        assertEquals(Status.UPCOMING, st("d"))
        assertNull(e.firstOrNull { it.rootId == "e" })
        assertEquals(LocalDate.of(2026, 11, 12), e.first { it.rootId == "c" && it.kind == Kind.COMPLETED }.date)
        assertEquals(listOf("a"), QeCalendar.overdue(e).map { it.rootId })
        assertEquals(2, QeCalendar.byDay(e).getValue(LocalDate.of(2026, 11, 10)).size + QeCalendar.byDay(e).getValue(LocalDate.of(2026, 11, 12)).size)
        assertEquals("November 2026", QeCalendar.monthTitle(YearMonth.of(2026, 11)))
        assertNull(QeCalendar.parse("31/11/2026"))
    }
}
