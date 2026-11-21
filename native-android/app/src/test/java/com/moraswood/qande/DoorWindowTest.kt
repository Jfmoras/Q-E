package com.moraswood.qande

import com.moraswood.qande.data.DwDefaults
import com.moraswood.qande.data.DwStructure
import com.moraswood.qande.logic.QeDoorWindow
import com.moraswood.qande.logic.QeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorWindowTest {
    private val d = DwDefaults(juttuExt = "3", wastage = "0")
    private val main = DwStructure(kind = "main_door", height = "7", width = "4", wood = "custom", woodW = "11", woodT = "3")
    private fun lens(r: QeDoorWindow.Result) = r.pieces.map { Triple(it.label, it.cutIn, it.qty) }

    @Test fun mainDoorJuttu() {
        val r = QeDoorWindow.compute(main, null, d)
        // longs 84 + 2×3 = 90; shorts 48 − 2×3 + 2×3 = 48
        assertEquals(listOf(Triple("Long (side)", 90.0, 2), Triple("Short (top/bottom)", 48.0, 2)), lens(r))
        assertEquals(2 * 90.0 / 12 * 33 / 144 + 2 * 4.0 * 33 / 144, r.totalCft, 1e-9)
        assertEquals(4, r.totalPieces)
    }

    @Test fun mainDoorScrewAndWastage() {
        val r = QeDoorWindow.compute(main.copy(mounting = "screw"), null, d)
        assertEquals(listOf(Triple("Long (side)", 78.0, 2), Triple("Short (top/bottom)", 48.0, 2)), lens(r))
        val w = QeDoorWindow.compute(main.copy(mounting = "screw"), "0.5", d)
        assertEquals(listOf(78.5, 48.5), w.pieces.map { it.cutIn })
        assertEquals(listOf(78.0, 48.0), w.pieces.map { it.finishedIn })
        assertEquals(2 * 78.5 / 12 * 33 / 144 + 2 * 48.5 / 12 * 33 / 144, w.totalCft, 1e-9)
        // structure wastage beats job; job beats Settings
        assertEquals(78.25, QeDoorWindow.compute(main.copy(mounting = "screw", wastage = "0.25"), "0.5", d).pieces[0].cutIn, 0.0)
        assertEquals(78.5, QeDoorWindow.compute(main.copy(mounting = "screw"), "", DwDefaults(wastage = "0.5")).pieces[0].cutIn, 0.0)
    }

    @Test fun bedroomDoorAndWindowSupports() {
        val bed = QeDoorWindow.compute(main.copy(kind = "bedroom_door", wood = "5x3"), null, d)
        assertEquals(listOf(2, 1), bed.pieces.map { it.qty })
        val win = QeDoorWindow.compute(DwStructure(kind = "window", height = "4", width = "3", wood = "4.5x2.5", supports = "2", mounting = "screw"), null, d)
        val sup = win.pieces.single { it.group == QeDoorWindow.GROUP_WINDOW }
        assertEquals(48 - 2 * 2.5, sup.finishedIn, 1e-9)
        assertEquals(2, sup.qty)
    }

    @Test fun planking4ftDoor5inSides6inPlanks() {
        val r = QeDoorWindow.compute(main.copy(planking = true, plankSide = "5", plankWidth = "6"), null, d)
        val planks = r.pieces.filter { it.group == QeDoorWindow.GROUP_PLANK }
        // middle = 48 − 10 = 38 → 7 planks: 6 × 6 in + 1 trimmed 2 in; length = 84 − 2 × 3 = 78; thickness = frame 3
        assertEquals(listOf("Plank" to 6, "Plank (trimmed)" to 1), planks.map { it.label to it.qty })
        assertEquals(2.0, planks[1].widthIn, 1e-9)
        assertEquals(78.0, planks[0].finishedIn, 1e-9)
        assertEquals(3.0, planks[0].thicknessIn, 0.0)
        // exact fit → no trimmed row
        val exact = QeDoorWindow.compute(main.copy(planking = true, plankSide = "6", plankWidth = "12"), null, d).pieces.filter { it.group == QeDoorWindow.GROUP_PLANK }
        assertEquals(listOf("Plank" to 3), exact.map { it.label to it.qty })
    }

    @Test fun architraveAndQuantity() {
        val r = QeDoorWindow.compute(main.copy(architrave = true, archW = "3", archT = "1", quantity = "4"), null, d)
        val a = r.pieces.filter { it.group == QeDoorWindow.GROUP_ARCH }
        assertEquals(listOf(Triple("Architrave header", 54.0, 4), Triple("Architrave jamb", 87.0, 8)), a.map { Triple(it.label, it.cutIn, it.qty) })
        assertEquals(listOf(8, 8), r.pieces.filter { it.group == QeDoorWindow.GROUP_FRAME }.map { it.qty })
        val single = QeDoorWindow.compute(main.copy(architrave = true, archW = "3", archT = "1"), null, d)
        assertEquals(single.totalCft * 4, r.totalCft, 1e-9)
        val win = QeDoorWindow.compute(DwStructure(kind = "window", height = "4", width = "3", architrave = true, archW = "3", archT = "1", archBottom = true), null, d)
        assertEquals(4, win.pieces.filter { it.group == QeDoorWindow.GROUP_ARCH }.sumOf { it.qty })
    }

    @Test fun addToCalculatorRowsMatchCft() {
        val s = main.copy(planking = true)
        val r = QeDoorWindow.compute(s, "0.25", d)
        val item = QeDoorWindow.toCalcItem(s, r)
        assertEquals(r.pieces.size, item.rows.size)
        assertEquals(r.totalCft, item.rows.sumOf { QeMath.calcRow(it).value }, 1e-9)
    }

    @Test fun badInputsWarnNotCrash() {
        val r = QeDoorWindow.compute(DwStructure(height = "", width = "4"), null, d)
        assertTrue(r.pieces.isEmpty()); assertTrue(r.warnings.isNotEmpty())
        val narrow = QeDoorWindow.compute(main.copy(height = "0.4", mounting = "screw"), null, d)
        assertTrue(narrow.warnings.any { it.contains("Long") })
        assertTrue(QeDoorWindow.assumptions(main, null, d).any { it.contains("placeholder") })
    }
}
