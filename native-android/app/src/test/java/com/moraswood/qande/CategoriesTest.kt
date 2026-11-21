package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.CategoryDefaults
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeCategories
import com.moraswood.qande.logic.QeEstimate
import com.moraswood.qande.logic.QeGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {
    private fun item(name: String, cat: String? = null) = CalcItem(name, listOf(Row("7", "ft", "6", "in", "2", "in", "2", "CFT")), category = cat)
    private val furnitureOnly = WorkspaceState(
        estimationItems = listOf(item("Table"), item("Chair")),
        costings = listOf(Costing.blank().copy(cftRate = "2000", labourMode = "lumpsum", labourLumpsum = "500"), Costing.blank().copy(cftRate = "1800")),
    )

    @Test fun furnitureOnlyJobIsExactlyV101() {
        val s = QeGroups.syncCostings(furnitureOnly)
        assertEquals(QeEstimate.syncCostings(furnitureOnly), s)
        val v = QeEstimate.estimateGrand(QeEstimate.syncCostings(furnitureOnly))
        val g = QeGroups.grand(s)
        assertEquals(v.selling, g.selling, 0.0); assertEquals(v.cost, g.cost, 0.0)
        val sub = QeCategories.subtotal(s, QeCategories.FURNITURE)
        assertEquals(v.selling, sub.selling, 1e-9); assertEquals(2, sub.count)
        assertEquals(0, QeCategories.subtotal(s, QeCategories.DW).count)
    }

    @Test fun categoryDefaultsApplyOnlyToBlankRatesInThatCategory() {
        val s0 = WorkspaceState(
            estimationItems = listOf(item("Table"), item("Door", QeCategories.DW)),
            costings = listOf(Costing.blank(), Costing.blank()),
            categoryDefaults = mapOf(QeCategories.DW to CategoryDefaults(cftRate = "2500", labourRate = "100")),
        )
        val s = QeGroups.syncCostings(s0)
        assertEquals("", QeGroups.effectiveCosting(s, 0).cftRate)
        assertEquals("2500", QeGroups.effectiveCosting(s, 1).cftRate)
        val cft = 7.0 * 6 * 2 * 2 / 144
        assertEquals(cft * 2500, QeGroups.itemResult(s, 1).wood, 1e-9)
        // own rate wins
        val own = QeGroups.syncCostings(s0.copy(costings = listOf(Costing.blank(), Costing.blank().copy(cftRate = "3000"))))
        assertEquals(cft * 3000, QeGroups.itemResult(own, 1).wood, 1e-9)
        val g = QeGroups.grand(s)
        val a = QeCategories.subtotal(s, QeCategories.FURNITURE); val b = QeCategories.subtotal(s, QeCategories.DW)
        assertEquals(g.selling, a.selling + b.selling, 1e-9)
        assertEquals(g.cost, a.cost + b.cost, 1e-9)
    }

    @Test fun moveLeavesGroupAndGroupsNeedOneCategory() {
        val s = QeGroups.group(furnitureOnly, listOf(0, 1))!!
        val moved = QeCategories.move(s, 1, QeCategories.DW)
        assertEquals(QeCategories.DW, moved.estimationItems[1].category)
        assertEquals(listOf(0), moved.itemGroups!!.single().items)
        assertFalse(QeCategories.sameCategory(moved, listOf(0, 1)))
        assertNull(QeCategories.move(moved, 1, QeCategories.FURNITURE).estimationItems[1].category)
        assertTrue(QeCategories.sameCategory(furnitureOnly, listOf(0, 1)))
    }

    @Test fun printOrderGroupsOnlyWhenAsked() {
        val lines = listOf("a" to QeCategories.DW, "b" to null, "c" to QeCategories.DW)
        assertEquals(listOf(null), QeCategories.printOrder(lines, { it.second }, false).map { it.first })
        val g = QeCategories.printOrder(lines, { it.second }, true)
        assertEquals(listOf("Furniture", "Doors & Windows"), g.map { it.first })
        assertEquals(listOf(listOf("b"), listOf("a", "c")), g.map { it.second.map { p -> p.second.first } })
        assertEquals(listOf(null), QeCategories.printOrder(listOf("x" to null), { it.second }, true).map { it.first })
    }
}
