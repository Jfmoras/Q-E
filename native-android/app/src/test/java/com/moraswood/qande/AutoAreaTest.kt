package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.Row
import com.moraswood.qande.logic.QeArea
import com.moraswood.qande.logic.QeEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAreaTest {
    private fun row(l: String, w: String, t: String, q: String, lUnit: String = "ft", wUnit: String = "in", face: String? = null) =
        Row(l, lUnit, w, wUnit, t, "in", q, "CFT", face)

    @Test fun areaSumsRowsTimesPieces() {
        // 7 ft × 48 in × 1 = 28 sq.ft; 6 ft × 6 in × 2 = 6 sq.ft
        val item = CalcItem(name = "Door", rows = listOf(row("7", "48", "2", "1"), row("6", "6", "1", "2")))
        assertEquals(34.0, QeArea.itemArea(item), 1e-9)
        assertEquals("34.00", QeArea.label(item, null))
    }

    @Test fun unitsAndVerticalFace() {
        // 2100 mm length, 0.9 m width → 2.1 × 0.9 m² = 20.34 sq.ft ; vertical row uses thickness: 7 ft × 3 in = 1.75
        val metric = CalcItem(name = "M", rows = listOf(row("2100", "0.9", "1", "1", lUnit = "mm", wUnit = "m")))
        assertEquals(2.1 * 0.9 / (0.3048 * 0.3048), QeArea.itemArea(metric), 1e-6)
        val vert = CalcItem(name = "V", rows = listOf(row("7", "48", "3", "1", face = "LT")))
        assertEquals(1.75, QeArea.itemArea(vert), 1e-9)
    }

    @Test fun zeroOrMissingWidthIsNaNotCrash() {
        val item = CalcItem(name = "X", rows = listOf(row("7", "", "2", "1"), row("", "4", "2", "1")))
        assertEquals(0.0, QeArea.itemArea(item), 0.0)
        assertFalse(QeArea.hasArea(item))
        assertEquals("n/a", QeArea.label(item, null))
        assertEquals("n/a", QeArea.label(CalcItem(name = "E", rows = emptyList()), Costing.blank()))
    }

    @Test fun perSqftLabourDefaultsToItemAreaAndOverride() {
        val item = CalcItem(name = "Door", rows = listOf(row("7", "48", "2", "1")))
        val c = Costing.blank().copy(labourRate = "100")
        assertTrue(QeArea.autoLabour(c))
        assertEquals(2800.0, QeEstimate.costingResult(item, c).labour, 1e-9)
        assertEquals(5600.0, QeEstimate.costingResult(item, c.copy(labourQty = "2")).labour, 1e-9)
        assertEquals(3000.0, QeEstimate.costingResult(item, c.copy(areaOverride = "30")).labour, 1e-9)
        // lumpsum unchanged
        assertEquals(500.0, QeEstimate.costingResult(item, c.copy(labourMode = "lumpsum", labourLumpsum = "500")).labour, 1e-9)
    }

    @Test fun explicitLabourLwKeepsV101Numbers() {
        val item = CalcItem(name = "Door", rows = listOf(row("7", "48", "2", "1")))
        val c = Costing.blank().copy(labourLength = "6", labourWidth = "3", labourQty = "2", labourRate = "100", areaOverride = "99")
        assertFalse(QeArea.autoLabour(c))
        assertEquals(QeEstimate.labourAreaSqft(c), QeEstimate.costingResult(item, c).labourArea, 0.0)
        assertEquals(3600.0, QeEstimate.costingResult(item, c).labour, 1e-9)
        // V10.1 quirk preserved: one of L/W filled → V10.1 formula (0 area)
        assertEquals(0.0, QeEstimate.costingResult(item, c.copy(labourWidth = "")).labour, 0.0)
    }
}
