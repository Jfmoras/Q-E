package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.Quote
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.QuoteOption
import com.moraswood.qande.data.RateGroup
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeEstimate
import com.moraswood.qande.logic.QeQuote
import com.moraswood.qande.logic.QeSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesOptionTest {
    // Bed: 2 CFT rows (7ft x 6in x 2in x 4 = 2.333 CFT; second row in a ₹2,000 group) + 1 KOL row.
    private val bed = CalcItem(
        name = "Bed",
        rows = listOf(
            Row(l = "7", lUnit = "ft", w = "6", wUnit = "in", t = "2", tUnit = "in", q = "4", mode = "CFT"),
            Row(l = "6", lUnit = "ft", w = "4", wUnit = "in", t = "3", tUnit = "in", q = "2", mode = "CFT"),
            Row(l = "3", lUnit = "ft", w = "5", wUnit = "in", t = "1", tUnit = "in", q = "5", mode = "KOL"),
        ),
    )
    private val costing = Costing.blank().copy(
        cftRate = "2500", kolRate = "90", labourMode = "lumpsum", labourLumpsum = "4000", consumables = "600",
        rateGroups = listOf(RateGroup(id = "g", name = "Group 1", mode = "CFT", color = "#ef4444", rate = "2000", rows = listOf(1))),
    )

    @Test fun linkCapturesVolumesAndWoodCostExactly() {
        val r = QeEstimate.costingResult(bed, costing)
        val l = QeSpecies.link(bed, r.costing)
        assertEquals(7.0 * 6 * 2 * 4 / 144 + 6.0 * 4 * 3 * 2 / 144, l.cft, 1e-12)
        assertEquals(3.0 * 5 * 1 * 5 / 37.5, l.kol, 1e-12)
        assertEquals(r.wood, l.woodCft + l.woodKol, 1e-9) // same wood figure the estimation card shows
        assertEquals("2500", l.cftRate); assertEquals("90", l.kolRate)
    }

    @Test fun repriceReplacesOnlyTheWoodPart() {
        val r = QeEstimate.costingResult(bed, costing)
        val line = QuoteItem(description = "Bed", unitPrice = r.costing.sellingPrice, wood = QeSpecies.link(bed, r.costing))
        val teak = QeSpecies.reprice(line, "4000", "150")
        val l = line.wood!!
        val expected = Math.floor(r.selling - r.wood + l.cft * 4000 + l.kol * 150 + 0.5)
        assertEquals(QeEstimate.jsRoundString(expected), teak.unitPrice)
        // auto selling price = cost, so the re-priced line equals a fresh estimate with the species rates and no group
        val fresh = QeEstimate.costingResult(bed, costing.copy(cftRate = "4000", kolRate = "150", rateGroups = emptyList(), sellingPrice = "", sellingAuto = null))
        assertEquals(fresh.costing.sellingPrice, teak.unitPrice)
        // a blank rate keeps that part of the wood cost as captured
        val kolOnly = QeSpecies.reprice(line, "", "150")
        assertEquals(QeEstimate.jsRoundString(Math.floor(r.selling - l.woodKol + l.kol * 150 + 0.5)), kolOnly.unitPrice)
        assertEquals(l.woodCft, kolOnly.wood!!.woodCft, 0.0); assertEquals("2500", kolOnly.wood!!.cftRate); assertEquals("150", kolOnly.wood!!.kolRate)
        val noRates = QeSpecies.reprice(line, " ", "")
        assertTrue(noRates === line)
        val unlinked = QuoteItem(description = "Old line", unitPrice = "9000")
        assertTrue(QeSpecies.reprice(unlinked, "4000", "150") === unlinked)
    }

    private fun quotation(): WorkspaceState {
        val r = QeEstimate.costingResult(bed, costing)
        val linked = QuoteItem(description = "Bed", unit = "Set", qty = "2", unitPrice = r.costing.sellingPrice, images = listOf("data:image/jpeg;base64,AAA"),
            aiOriginal = "bed", wood = QeSpecies.link(bed, r.costing))
        val plain = QuoteItem(description = "Transport", unit = "Job", qty = "1", unitPrice = "1500")
        val s = WorkspaceState(quoteItems = listOf(linked, plain), quote = Quote(discountType = "percent", discountValue = "5", gstPercent = "18", notes = listOf("Teak polish")))
        return QeQuote.syncActiveOption(s)
    }

    @Test fun duplicateCopiesEverythingAndActivatesTheNewOption() {
        val s = quotation()
        val d = QeSpecies.duplicateAsSpecies(s, " Teak ", "4000", "150")
        val n = d.state
        assertEquals(2, n.quoteOptions.size)
        assertEquals(1, n.activeQuoteOption)
        val src = n.quoteOptions[0]; val copy = n.quoteOptions[1]
        assertEquals(s.quoteOptions[0], src) // source option untouched
        assertEquals("Teak", copy.name); assertEquals("Teak", copy.species)
        assertEquals(src.discountType, copy.discountType); assertEquals(src.discountValue, copy.discountValue); assertEquals(src.gstPercent, copy.gstPercent)
        assertEquals(src.items.size, copy.items.size)
        src.items.zip(copy.items).forEach { (a, b) ->
            assertEquals(a.description, b.description); assertEquals(a.unit, b.unit); assertEquals(a.qty, b.qty)
            assertEquals(a.images, b.images); assertEquals(a.aiOriginal, b.aiOriginal); assertEquals("Teak", b.species)
        }
        assertEquals("1500", copy.items[1].unitPrice) // unlinked line keeps its price
        assertEquals(1, d.repriced); assertEquals(1, d.unlinked)
        assertEquals("Duplicated as “Teak”. 1 line re-priced from CFT/KOL. 1 line has no CFT/KOL link — check the price.", QeSpecies.duplicateMessage(d, " Teak "))
        assertEquals(copy.items, n.quoteItems) // on-screen items are the new option
        assertEquals(s.quote.notes, n.quote.notes) // notes are quotation-level, shared by every option
        // no species: behaves like V10.1 "Duplicate option"
        val plainDup = QeSpecies.duplicateAsSpecies(s, "", "", "").state
        assertEquals(QeQuote.duplicateOption(s).quoteOptions.map { it.copy(species = null) }, plainDup.quoteOptions)
    }

    @Test fun onlyTheActiveOptionIsOfficialAndPrintedByDefault() {
        val n = QeSpecies.duplicateAsSpecies(quotation(), "Teak", "4000", "150").state
        val t1 = QeQuote.totalsForOption(n.quoteOptions[1])
        assertEquals(t1, QeSpecies.officialTotals(n))
        assertEquals(t1, QeQuote.quoteTotals(n))
        val back = QeQuote.activateOption(n, 0)
        assertEquals(QeQuote.totalsForOption(back.quoteOptions[0]), QeSpecies.officialTotals(back))
        assertFalse(QeSpecies.officialTotals(back) == t1)
        assertEquals(listOf(1), QeSpecies.printOrder(n, false).map { it.first })
        assertEquals(listOf(1, 0), QeSpecies.printOrder(n, true).map { it.first })
        assertEquals(listOf(0, 1), QeSpecies.printOrder(back, true).map { it.first })
    }

    @Test fun printHeadingOnlyForMultipleOptionsAndOnlyNamedSuffix() {
        // single option: no heading at all (the screenshot's "OPTION 1: Option 1")
        assertNull(QeSpecies.printHeading(QuoteOption(name = "Option 1"), 0, 1))
        assertNull(QeSpecies.printHeading(QuoteOption(name = "Teak set"), 0, 1))
        // several options: "OPTION N", ": name" only when really named
        assertEquals("OPTION 1", QeSpecies.printHeading(QuoteOption(name = "Option 1"), 0, 2))
        assertEquals("OPTION 2", QeSpecies.printHeading(QuoteOption(name = ""), 1, 2))
        assertEquals("OPTION 2", QeSpecies.printHeading(QuoteOption(name = "  option 2 copy "), 1, 3))
        assertEquals("OPTION 3: Premium", QeSpecies.printHeading(QuoteOption(name = " Premium "), 2, 3))
        assertEquals("OPTION 2: Teak", QeSpecies.printHeading(QuoteOption(name = "", species = "Teak"), 1, 2))
        assertEquals("OPTION 2: Premium (Teak)", QeSpecies.printHeading(QuoteOption(name = "Premium", species = "Teak"), 1, 2))
        assertEquals("OPTION 1: Option A", QeSpecies.printHeading(QuoteOption(name = "Option A"), 0, 2))
        // UI keeps "Option N" for unnamed options; new / added / duplicated options get no auto name
        assertEquals("Option 2", QeSpecies.optionLabel(QuoteOption(name = ""), 1))
        val s = QeQuote.ensureQuoteOptions(quotation().copy(quoteOptions = emptyList()))
        assertEquals("", s.quoteOptions.single().name)
        val two = QeQuote.addOption(s)
        assertEquals(listOf("", ""), two.quoteOptions.map { it.name })
        assertEquals("", QeQuote.duplicateOption(two).quoteOptions.last().name)
        assertEquals("Premium Copy", QeQuote.duplicateOption(two.copy(quoteOptions = two.quoteOptions.map { it.copy(name = "Premium") })).quoteOptions.last().name)
    }

    @Test fun speciesAndLinkRoundTripThroughJsonAndStayOptional() {
        val n = QeSpecies.duplicateAsSpecies(quotation(), "Teak", "4000", "150").state.copy(printComparison = true)
        val json = QeJson.encodeToString(WorkspaceState.serializer(), n)
        assertEquals(n, QeJson.decodeFromString(WorkspaceState.serializer(), json))
        val plain = QeJson.encodeToString(QuoteOption.serializer(), QuoteOption(name = "Option 1", items = listOf(QuoteItem(description = "x"))))
        assertFalse(plain.contains("species")); assertFalse(plain.contains("wood"))
        assertNull(QeJson.decodeFromString(QuoteItem.serializer(), """{"description":"V10.1 line","unitPrice":"10"}""").species)
        assertEquals("Sal", QeSpecies.optionLabel(QuoteOption(name = "Option 1", species = "Sal"), 0))
        assertEquals("Teak", QeSpecies.optionLabel(QuoteOption(name = "Teak", species = "Teak"), 0))
    }
}
