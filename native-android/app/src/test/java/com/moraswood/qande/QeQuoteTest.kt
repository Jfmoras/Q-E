package com.moraswood.qande

import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.QuoteOption
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath.num
import com.moraswood.qande.logic.QeQuote
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Quotation math and option handling against V10.1: expected-v101.json is the output of the unchanged app.js
 * functions (num … quoteTotals, lines 5-73) and the option button handlers (lines 417-419) run in node on
 * cases.json (see v101-harness.js). Each case is replayed step by step and every snapshot compared.
 */
class QeQuoteTest {
    private fun res(name: String) = File("src/test/resources/quotation/$name").readText()

    private fun apply(s: WorkspaceState, op: JsonArray): WorkspaceState {
        val k = op[0].jsonPrimitive.content
        fun str(i: Int) = op[i].jsonPrimitive.content
        return when (k) {
            "click" -> when (str(1)) {
                "addQuoteOption" -> QeQuote.addOption(s)
                "duplicateQuoteOption" -> QeQuote.duplicateOption(s)
                "removeQuoteOption" -> QeQuote.removeOption(s)
                else -> error(str(1))
            }
            "activate" -> QeQuote.activateOption(s, op[1].jsonPrimitive.int)
            "name" -> QeQuote.ensureQuoteOptions(s).let { e -> e.copy(quoteOptions = e.quoteOptions.mapIndexed { i, o -> if (i == e.activeQuoteOption) o.copy(name = str(1)) else o }) }
            "field" -> {
                val i = op[1].jsonPrimitive.int
                s.copy(quoteItems = s.quoteItems.mapIndexed { j, it ->
                    if (j != i) it else when (str(2)) {
                        "description" -> it.copy(description = str(3)); "unit" -> it.copy(unit = str(3))
                        "qty" -> it.copy(qty = str(3)); "unitPrice" -> it.copy(unitPrice = str(3)); else -> error(str(2))
                    }
                })
            }
            "addItem" -> s.copy(quoteItems = s.quoteItems + QuoteItem.blank())
            "quote" -> when (str(1)) {
                "discountType" -> s.copy(quote = s.quote.copy(discountType = str(2)))
                "discountValue" -> s.copy(quote = s.quote.copy(discountValue = str(2)))
                "gstPercent" -> s.copy(quote = s.quote.copy(gstPercent = str(2)))
                else -> error(str(1))
            }
            else -> error(k)
        }.let(QeQuote::syncActiveOption) // render side effect after every handler (AppViewModel.update)
    }

    private fun check(tag: String, s: WorkspaceState, e: JsonObject) {
        // t48u: auto default names ("Option N", "… Copy") are stored blank natively; everything else must match V10.1.
        fun norm(l: List<QuoteOption>) = l.map { if (com.moraswood.qande.logic.QeSpecies.isDefaultName(it.name)) it.copy(name = "") else it }
        assertEquals("$tag options", norm(QeJson.decodeFromJsonElement(ListSerializer(QuoteOption.serializer()), e["quoteOptions"]!!)), norm(s.quoteOptions))
        assertEquals("$tag active", e["activeQuoteOption"]!!.jsonPrimitive.int, s.activeQuoteOption)
        assertEquals("$tag items", QeJson.decodeFromJsonElement(ListSerializer(QuoteItem.serializer()), e["quoteItems"]!!), s.quoteItems)
        val q = e["quote"]!!.jsonObject
        assertEquals("$tag discountType", q["discountType"]!!.jsonPrimitive.content, s.quote.discountType)
        assertEquals("$tag discountValue", q["discountValue"]!!.jsonPrimitive.content, s.quote.discountValue)
        assertEquals("$tag gstPercent", q["gstPercent"]!!.jsonPrimitive.content, s.quote.gstPercent)
        val totals = e["totals"]!!.jsonArray
        assertEquals("$tag totals size", totals.size, s.quoteOptions.size)
        s.quoteOptions.forEachIndexed { i, o ->
            val t = QeQuote.totalsForOption(o)
            val et = totals[i].jsonObject
            fun d(key: String) = et[key]!!.jsonPrimitive.double
            assertEquals("$tag opt $i subtotal", d("subtotal"), t.subtotal, 1e-9)
            assertEquals("$tag opt $i discount", d("discount"), t.discount, 1e-9)
            assertEquals("$tag opt $i taxable", d("taxable"), t.taxable, 1e-9)
            assertEquals("$tag opt $i gst", d("gst"), t.gst, 1e-9)
            assertEquals("$tag opt $i grand", d("grand"), t.grand, 1e-9)
            assertEquals("$tag opt $i rs", et["rs"]!!.jsonArray.map { it.jsonPrimitive.content },
                listOf(t.subtotal, t.discount, t.gst, t.grand).map(QeFormat::rs))
            assertEquals("$tag opt $i gst label", et["gstLabel"]!!.jsonPrimitive.content, QeQuote.jsNumber(num(o.gstPercent)))
        }
        val sc = e["screen"]!!.jsonObject
        val t = QeQuote.quoteTotals(s)
        assertEquals("$tag screen grand", sc["grand"]!!.jsonPrimitive.double, t.grand, 1e-9)
        assertEquals("$tag screen subtotal", sc["subtotal"]!!.jsonPrimitive.double, t.subtotal, 1e-9)
        assertEquals("$tag screen money", sc["money"]!!.jsonArray.map { it.jsonPrimitive.content },
            listOf(t.subtotal, t.discount, t.gst, t.grand).map(QeFormat::money))
        assertEquals("$tag discount label", sc["discountLabel"]!!.jsonPrimitive.content, QeQuote.discountLabel(s.quote.discountType, s.quote.discountValue))
    }

    @Test fun quotationMatchesAppJs() {
        val cases = Json.parseToJsonElement(res("cases.json")).jsonArray
        val expected = Json.parseToJsonElement(res("expected-v101.json")).jsonArray
        assertEquals(cases.size, expected.size)
        cases.forEachIndexed { n, case ->
            val c = case.jsonObject
            var s = QeQuote.syncActiveOption(QeJson.decodeFromJsonElement(WorkspaceState.serializer(), c["state"]!!))
            val steps = expected[n].jsonArray
            check("case $n step 0", s, steps[0].jsonObject)
            c["ops"]?.jsonArray?.forEachIndexed { k, op ->
                s = apply(s, op.jsonArray)
                check("case $n step ${k + 1} $op", s, steps[k + 1].jsonObject)
            }
        }
    }

    @Test fun printHelpers() {
        assertEquals("05 January 2026", QeQuote.longDate(LocalDate.of(2026, 1, 5)))
        assertEquals("5/1/2026", QeQuote.shortDate(LocalDate.of(2026, 1, 5)))
        assertEquals("2 Nos", QeQuote.unitsText(QuoteItem(qty = "2", unit = "Nos")))
        assertEquals("Set", QeQuote.unitsText(QuoteItem(unit = "Set")))
        assertEquals("-", QeQuote.unitsText(QuoteItem()))
        assertEquals("12.5", QeQuote.jsNumber(12.5)); assertEquals("0", QeQuote.jsNumber(0.0)); assertEquals("18", QeQuote.jsNumber(18.0))
        val opt = QuoteOption(items = List(3) { QuoteItem(images = listOf("a$it", "b$it")) })
        assertEquals(listOf("a0", "b0", "a1", "b1"), QeQuote.optionImages(opt))
    }
}
