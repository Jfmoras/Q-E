package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeEstimate
import com.moraswood.qande.logic.QeFormat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Estimation math against V10.1: expected-v101.json was produced by running the unchanged app.js
 * functions (num … estimateGrand, lines 5-54) in node on cases.json (see v101-harness.js).
 */
class QeEstimateTest {
    private fun res(name: String) = File("src/test/resources/estimation/$name").readText()

    @Test fun costingResultMatchesAppJs() {
        val cases = Json.parseToJsonElement(res("cases.json")).jsonArray
        val expected = Json.parseToJsonElement(res("expected-v101.json")).jsonArray
        assertEquals(cases.size, expected.size)
        cases.forEachIndexed { n, case ->
            val items = QeJson.decodeFromJsonElement(ListSerializer(CalcItem.serializer()), case.jsonObject["items"]!!)
            val costings = QeJson.decodeFromJsonElement(ListSerializer(Costing.serializer()), case.jsonObject["costings"]!!)
            val exp = expected[n].jsonObject
            items.forEachIndexed { i, item ->
                val r = QeEstimate.costingResult(item, costings[i])
                val e = exp["rs"]!!.jsonArray[i].jsonObject
                fun d(k: String) = e[k]!!.jsonPrimitive.double
                val tag = "case $n item $i"
                assertEquals("$tag wood", d("wood"), r.wood, 1e-9)
                assertEquals("$tag grouped", d("grouped"), r.groupedWood, 1e-9)
                assertEquals("$tag ungrouped", d("ungrouped"), r.ungroupedWood, 1e-9)
                assertEquals("$tag labourArea", d("labourArea"), r.labourArea, 1e-9)
                assertEquals("$tag labour", d("labour"), r.labour, 1e-9)
                assertEquals("$tag polish", d("polish"), r.polish, 1e-9)
                assertEquals("$tag polishArea", d("polishArea"), r.polishArea, 1e-9)
                assertEquals("$tag surface", d("surface"), r.surface, 1e-9)
                assertEquals("$tag cost", d("cost"), r.cost, 1e-9)
                assertEquals("$tag selling", d("selling"), r.selling, 1e-9)
                assertEquals("$tag profit", d("profit"), r.profit, 1e-9)
                assertEquals("$tag markup", e["markup"]!!.jsonPrimitive.content, QeFormat.toFixed(r.markup, 1))
                assertEquals("$tag margin", e["margin"]!!.jsonPrimitive.content, QeFormat.toFixed(r.margin, 1))
                assertEquals("$tag sellingPrice", e["sellingPrice"]!!.jsonPrimitive.content, r.costing.sellingPrice)
                assertEquals("$tag sellingAuto", e["sellingAuto"]!!.jsonPrimitive.boolean, r.costing.sellingAuto)
                assertEquals("$tag money", e["money"]!!.jsonPrimitive.content, QeFormat.money(r.cost))
            }
            val g = QeEstimate.estimateGrand(WorkspaceState(estimationItems = items, costings = costings))
            val eg = exp["grand"] as JsonObject
            assertEquals(eg["cost"]!!.jsonPrimitive.double, g.cost, 1e-9)
            assertEquals(eg["selling"]!!.jsonPrimitive.double, g.selling, 1e-9)
            assertEquals(eg["profit"]!!.jsonPrimitive.double, g.profit, 1e-9)
            assertEquals(exp["grandMoney"]!!.jsonPrimitive.content, QeFormat.money(g.selling))
        }
    }

    @Test fun groupRateAppliesToEveryRowInGroupButSummaryCountsOwnMode() {
        val cases = Json.parseToJsonElement(res("cases.json")).jsonArray
        val item = QeJson.decodeFromJsonElement(CalcItem.serializer(), cases[2].jsonObject["items"]!!.jsonArray[0])
        val c = QeJson.decodeFromJsonElement(Costing.serializer(), cases[2].jsonObject["costings"]!!.jsonArray[0])
        val s = QeEstimate.groupSummary(item, c.rateGroups[0])
        assertEquals(2.4, s.volume, 1e-12)
        assertEquals(2880.0, s.amount, 1e-9)
        assertEquals("1200", QeEstimate.effectiveTimberRate(c, item.rows[1], 1))
    }

    @Test fun blankSellingPriceAutoFillsAndManualPriceSticks() {
        val item = CalcItem("x", listOf(com.moraswood.qande.data.Row("5", "ft", "30", "in", "1", "in", "1", "CFT")))
        val auto = QeEstimate.costingResult(item, Costing.blank().copy(cftRate = "2000"))
        assertEquals("2083", auto.costing.sellingPrice)
        assertEquals(true, auto.costing.sellingAuto)
        val manual = QeEstimate.costingResult(item, Costing.blank().copy(cftRate = "2000", sellingPrice = "3000", sellingAuto = false))
        assertEquals("3000", manual.costing.sellingPrice)
        val zero = QeEstimate.costingResult(CalcItem("y", emptyList()), Costing.blank())
        assertEquals("", zero.costing.sellingPrice)
    }

    @Test fun syncCostingsCreatesMissingCostings() {
        val s = QeEstimate.syncCostings(WorkspaceState(estimationItems = listOf(CalcItem("a", emptyList()), CalcItem("b", emptyList())), costings = emptyList()))
        assertEquals(2, s.costings.size)
        assertEquals("sqft", s.costings[1].labourMode)
    }

    @Test fun jsRound() {
        assertEquals("3", QeEstimate.jsRoundString(2.5))
        assertEquals("-2", QeEstimate.jsRoundString(-2.5))
        assertEquals("0", QeEstimate.jsRoundString(-0.3))
        assertEquals("22618", QeEstimate.jsRoundString(22617.94))
    }
}
