package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.ItemGroup
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.RateGroup
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeEstimate
import com.moraswood.qande.logic.QeGroups
import com.moraswood.qande.logic.QeMath.num
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemGroupsTest {
    private fun row(l: String, w: String, t: String, q: String, mode: String = "CFT") = Row(l = l, lUnit = "ft", w = w, wUnit = "in", t = t, tUnit = "in", q = q, mode = mode)
    private val cot = CalcItem(name = "Cot", rows = listOf(row("6.5", "4", "3", "2"), row("4", "4", "2", "2")))
    private val table = CalcItem(name = "Table", rows = listOf(row("5", "3", "2", "4"), row("3", "5", "1", "6", "KOL")))
    private val shelf = CalcItem(name = "Shelf", rows = listOf(row("3", "10", "1", "4")))
    private val costings = listOf(
        Costing.blank().copy(cftRate = "", kolRate = "", labourMode = "lumpsum", labourLumpsum = "3000"),
        Costing.blank().copy(cftRate = "3200", kolRate = "", polishMode = "lumpsum", polishLumpsum = "900",
            rateGroups = listOf(RateGroup(id = "g1", name = "Group 1", mode = "CFT", color = "#ef4444", rate = "4100", rows = listOf(0)))),
        Costing.blank().copy(cftRate = "2800", kolRate = "95", consumables = "250", sellingPrice = "9000", sellingAuto = false),
    )
    private fun base() = QeEstimate.syncCostings(WorkspaceState(estimationItems = listOf(cot, table, shelf), costings = costings))

    @Test fun ungroupedJobGivesExactlyV101Numbers() {
        val s = base()
        assertEquals(QeEstimate.syncCostings(s), QeGroups.syncCostings(s))
        val g = QeEstimate.estimateGrand(s); val q = QeGroups.grand(s)
        assertEquals(g.cost, q.cost, 0.0); assertEquals(g.selling, q.selling, 0.0); assertEquals(g.profit, q.profit, 0.0)
        assertEquals(s.costings.map { it.sellingPrice }, QeGroups.quotePrices(s))
        s.estimationItems.indices.forEach { assertEquals(QeEstimate.costingResult(s.estimationItems[it], s.costings[it]), QeGroups.itemResult(s, it)) }
        assertFalse(QeJson.encodeToString(WorkspaceState.serializer(), s).contains("itemGroups"))
    }

    @Test fun groupRatesAreDefaultsAndItemRatesWin() {
        val s = QeGroups.syncCostings(QeGroups.group(base(), listOf(0, 1))!!.let { QeGroups.update(it, "ig-1") { g -> g.copy(cftRate = "2500", kolRate = "100") } })
        val cotEff = QeGroups.effectiveCosting(s, 0)
        assertEquals("2500", cotEff.cftRate); assertEquals("100", cotEff.kolRate)
        assertEquals("", s.costings[0].cftRate) // stored item rate stays blank
        val tableEff = QeGroups.effectiveCosting(s, 1)
        assertEquals("3200", tableEff.cftRate); assertEquals("100", tableEff.kolRate)
        // the auto selling price follows the cost with group defaults
        val r = QeEstimate.costingResult(cot, costings[0].copy(cftRate = "2500", kolRate = "100"))
        assertEquals(r.costing.sellingPrice, s.costings[0].sellingPrice)
        assertEquals(r.cost, QeGroups.itemResult(s, 0).cost, 1e-9)
        // V10.1 row rate group inside the table still wins (row 0 at 4100)
        assertEquals("4100", QeEstimate.effectiveTimberRate(tableEff, table.rows[0], 0))
        // shelf is outside the group: untouched
        assertEquals(base().costings[2], s.costings[2])
    }

    @Test fun groupTotalsAndGrandTotal() {
        var s = QeGroups.group(base(), listOf(0, 1))!!
        s = QeGroups.syncCostings(QeGroups.update(s, "ig-1") { it.copy(labour = "2000", polish = "1500", consumables = "300", other = "200") })
        val t = QeGroups.groupTotals(s, s.itemGroups!![0])
        val r0 = QeGroups.itemResult(s, 0); val r1 = QeGroups.itemResult(s, 1); val r2 = QeGroups.itemResult(s, 2)
        assertEquals(r0.cost + r1.cost, t.itemsCost, 1e-9)
        assertEquals(4000.0, t.extras, 0.0)
        assertEquals(t.itemsCost + 4000, t.cost, 1e-9)
        assertEquals(r0.selling + r1.selling + 4000, t.selling, 1e-9) // blank group selling = auto
        assertEquals(t.selling - t.cost, t.profit, 1e-9)
        val grand = QeGroups.grand(s)
        assertEquals(t.cost + r2.cost, grand.cost, 1e-9); assertEquals(t.selling + r2.selling, grand.selling, 1e-9)
        val over = QeGroups.update(s, "ig-1") { it.copy(selling = "60000") }
        assertEquals(60000.0, QeGroups.groupTotals(over, over.itemGroups!![0]).selling, 0.0)
    }

    @Test fun groupPriceIsDistributedByCostAndAddsUp() {
        var s = QeGroups.group(base(), listOf(0, 1))!!
        s = QeGroups.syncCostings(QeGroups.update(s, "ig-1") { it.copy(labour = "2000", selling = "50001") })
        val p = QeGroups.quotePrices(s)
        assertEquals(50001.0, num(p[0]) + num(p[1]), 0.0)
        assertEquals(s.costings[2].sellingPrice, p[2]) // ungrouped line keeps its V10.1 price
        val r0 = QeGroups.itemResult(s, 0); val r1 = QeGroups.itemResult(s, 1)
        val extra = 50001 - (r0.selling + r1.selling)
        val exp0 = r0.selling + extra * r0.cost / (r0.cost + r1.cost)
        assertTrue(Math.abs(num(p[0]) - exp0) <= 1.0)
        p.forEach { assertFalse(it.contains('.')) }
        // zero-cost members share equally
        val z = WorkspaceState(estimationItems = listOf(CalcItem(name = "A"), CalcItem(name = "B")), costings = listOf(Costing.blank(), Costing.blank()),
            itemGroups = listOf(ItemGroup(id = "ig-1", name = "G", items = listOf(0, 1), selling = "1001")))
        assertEquals(listOf("501", "500"), QeGroups.quotePrices(QeGroups.syncCostings(z)).let { if (num(it[0]) >= num(it[1])) it else it.reversed() })
        // no lumpsum and no override → member prices are exactly their own selling strings
        val plain = QeGroups.syncCostings(QeGroups.group(base(), listOf(0, 1))!!)
        assertEquals(plain.costings.map { it.sellingPrice }, QeGroups.quotePrices(plain))
    }

    @Test fun groupUngroupRenameMoveAndJson() {
        val s0 = base()
        assertNull(QeGroups.group(s0, emptyList()))
        assertNull(QeGroups.group(s0, listOf(7)))
        val s1 = QeGroups.group(s0, listOf(1, 0))!!
        assertEquals(listOf(ItemGroup(id = "ig-1", name = "Group 1", items = listOf(0, 1))), s1.itemGroups)
        val s2 = QeGroups.group(s1, listOf(1, 2))!! // moves item 1 into the new group
        assertEquals(listOf(listOf(0), listOf(1, 2)), s2.itemGroups!!.map { it.items })
        assertEquals("Group 2", s2.itemGroups!![1].name)
        val s3 = QeGroups.update(s2, "ig-2") { QeGroups.setField(it, "name", "Bedroom") }
        assertEquals("Bedroom", s3.itemGroups!![1].name)
        val s4 = QeGroups.group(s3, listOf(0))!! // group 1 becomes empty → removed
        assertEquals(listOf("ig-2", "ig-3"), s4.itemGroups!!.map { it.id })
        val back = QeGroups.ungroup(QeGroups.ungroup(s4, "ig-2"), "ig-3")
        assertNull(back.itemGroups)
        assertEquals(QeGroups.grand(s0), QeGroups.grand(QeGroups.syncCostings(back)))
        val json = QeJson.encodeToString(WorkspaceState.serializer(), s3)
        assertEquals(s3, QeJson.decodeFromString(WorkspaceState.serializer(), json))
    }
}
