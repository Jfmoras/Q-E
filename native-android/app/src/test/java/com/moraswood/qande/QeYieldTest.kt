package com.moraswood.qande

import com.moraswood.qande.data.HoppusLog
import com.moraswood.qande.data.HoppusState
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.YieldEntryEntity
import com.moraswood.qande.logic.QeYield
import com.moraswood.qande.yieldtracker.YieldDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QeYieldTest {
    private fun e(id: Long, date: String, sp: String, job: String, input: Double, out: Double, unit: String = "CFT", off: Double = 0.0) =
        YieldEntryEntity(id, date, sp, job, input, unit, "direct", out, "[]", off, 0.0, 0.0, "", "", id)

    @Test fun ratiosAndWaste() {
        val x = e(1, "2026-11-01", "Teak", "Doors", 20.0, 13.0, off = 4.0)
        assertEquals(65.0, QeYield.yieldPct(20.0, 13.0)!!, 1e-9)
        assertEquals(35.0, QeYield.lossPct(20.0, 13.0)!!, 1e-9)
        assertEquals(3.0, QeYield.unaccounted(x), 1e-9)
        assertNull(QeYield.yieldPct(0.0, 5.0))
        assertEquals("n/a", QeYield.pct(null))
        assertEquals(QeYield.M3_TO_CFT, QeYield.inputCft(e(2, "d", "", "", 1.0, 0.0, unit = "m3")), 1e-9)
    }

    @Test fun outputFromFinishedRowsUsesCftHelper() {
        // 7 ft × 6 in × 3 in × 2 pcs = 7 × 18 / 144 × 2 = 1.75 CFT (mode forced to CFT even if the row says KOL)
        val rows = listOf(Row("7", "ft", "6", "in", "3", "in", "2", "KOL"))
        assertEquals(1.75, QeYield.rowsCft(rows), 1e-9)
        val d = YieldDraft(inputVolume = "3.5", outputMode = "rows", outputRows = rows + Row.blank())
        val ent = d.toEntity(0)
        assertEquals(1, QeYield.decodeRows(ent.outputRowsJson).size) // blank rows dropped
        assertEquals(50.0, QeYield.yieldPct(QeYield.inputCft(ent), QeYield.outputCft(ent))!!, 1e-9)
    }

    @Test fun aggregatesBySpeciesAndJobType() {
        val list = listOf(
            e(1, "2026-11-01", "Teak", "Doors", 10.0, 6.0),
            e(2, "2026-11-02", "Teak", "Windows", 30.0, 24.0),
            e(3, "2026-11-03", "Sal", "Doors", 20.0, 10.0),
        )
        val sp = QeYield.bySpecies(list).associateBy { it.key }
        assertEquals(75.0, sp["Teak"]!!.weightedYield!!, 1e-9) // 30/40
        assertEquals(70.0, sp["Teak"]!!.meanYield!!, 1e-9)    // (60+80)/2
        assertEquals(2, sp["Teak"]!!.count)
        val jt = QeYield.byJobType(list).associateBy { it.key }
        assertEquals(16.0 / 30 * 100, jt["Doors"]!!.weightedYield!!, 1e-9)
        assertEquals(listOf(60.0, 80.0, 50.0), QeYield.trend(list.reversed()).map { it.second })
    }

    @Test fun hoppusHandOff() {
        val h = HoppusState(listOf(HoppusLog("12", "48"), HoppusLog("", ""), HoppusLog("10", "40")))
        assertEquals(12 * 48.0 * 48 / 2304 + 10 * 40.0 * 40 / 2304, QeYield.hoppusTotal(h), 1e-9)
        assertEquals("Log 1: 12 ft × 48 in girth = 12.00 CFT\nLog 3: 10 ft × 40 in girth = 6.94 CFT", QeYield.hoppusDetails(h))
    }
}
