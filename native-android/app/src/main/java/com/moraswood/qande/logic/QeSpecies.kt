package com.moraswood.qande.logic

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.QuoteOption
import com.moraswood.qande.data.WoodLink
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeMath.num

/**
 * Feature D — duplicate the whole quotation as a wood-species option (extends V10.1's quotation options).
 *
 * Decisions (documented in PLAN.md):
 * - V10.1 quotation lines carry no timber data, so the CFT/KOL link is captured when an estimation item is sent to the
 *   quotation ([link]). Lines without a link (older quotations, hand-typed lines) get the species label and keep their
 *   price for the owner to edit.
 * - Re-pricing replaces only the wood part: new price = old price − old wood cost + new wood cost, rounded like V10.1's
 *   automatic selling price (`Math.round`). Labour, polish, consumables, other and the owner's profit stay in rupees.
 *   The species rate applies to every CFT (resp. KOL) row of the item; a blank rate leaves that part unchanged.
 * - The active option is the official one (Save Project / Export to Projects / Print use it).
 */
object QeSpecies {
    /** Timber link for estimation item [item] with its synced costing. */
    fun link(item: CalcItem, costing: Costing?): WoodLink {
        val c = costing ?: Costing.blank()
        var cft = 0.0; var kol = 0.0; var woodCft = 0.0; var woodKol = 0.0
        item.rows.forEachIndexed { j, row ->
            val v = QeMath.calcRow(row).value
            val amount = v * num(QeEstimate.effectiveTimberRate(c, row, j))
            if (row.mode == "CFT") { cft += v; woodCft += amount } else { kol += v; woodKol += amount }
        }
        return WoodLink(cft, kol, woodCft, woodKol, c.cftRate, c.kolRate)
    }

    /** Re-price one line for new species rates; returns the line unchanged when it has no link or no rate is given. */
    fun reprice(item: QuoteItem, cftRate: String, kolRate: String): QuoteItem {
        val l = item.wood ?: return item
        if (cftRate.isBlank() && kolRate.isBlank()) return item
        val newCft = if (cftRate.isBlank()) l.woodCft else l.cft * num(cftRate)
        val newKol = if (kolRate.isBlank()) l.woodKol else l.kol * num(kolRate)
        val price = num(item.unitPrice) - (l.woodCft + l.woodKol) + newCft + newKol
        return item.copy(
            unitPrice = QeEstimate.jsRoundString(price),
            wood = l.copy(woodCft = newCft, woodKol = newKol, cftRate = cftRate.ifBlank { l.cftRate }, kolRate = kolRate.ifBlank { l.kolRate }),
        )
    }

    data class Duplicated(val state: WorkspaceState, val repriced: Int, val unlinked: Int)

    /** "Duplicate as wood-species option": copies every line (fields, images, AI text), discount and GST of the active option. */
    fun duplicateAsSpecies(s0: WorkspaceState, species: String, cftRate: String, kolRate: String): Duplicated {
        val s = QeQuote.syncActiveOption(s0)
        val src = s.quoteOptions[s.activeQuoteOption]
        val sp = species.trim()
        var repriced = 0; var unlinked = 0
        val items = src.items.map { x ->
            val y = reprice(x, cftRate, kolRate)
            if (y !== x) repriced++ else if (x.wood == null && (x.description.isNotEmpty() || x.unitPrice.isNotEmpty())) unlinked++
            y.copy(species = sp.ifEmpty { null } ?: y.species)
        }
        val copy = src.copy(name = sp.ifEmpty { src.name + " Copy" }, items = items, species = sp.ifEmpty { src.species })
        val next = QeQuote.activateOption(s.copy(quoteOptions = s.quoteOptions + copy), s.quoteOptions.size)
        return Duplicated(next, repriced, unlinked)
    }

    fun duplicateMessage(d: Duplicated, species: String): String {
        val name = species.trim().ifEmpty { "copy" }
        val parts = mutableListOf("Duplicated as “$name”.")
        if (d.repriced > 0) parts += "${d.repriced} line${if (d.repriced == 1) "" else "s"} re-priced from CFT/KOL."
        if (d.unlinked > 0) parts += "${d.unlinked} line${if (d.unlinked == 1) " has" else "s have"} no CFT/KOL link — check the price."
        return parts.joinToString(" ")
    }

    /** Totals of the official (active) option, after syncing the on-screen edits. */
    fun officialTotals(s: WorkspaceState): QeQuote.Totals {
        val x = QeQuote.syncActiveOption(s)
        return QeQuote.totalsForOption(x.quoteOptions[x.activeQuoteOption])
    }

    /** Options in print order: the active option, then (only when [comparison]) the others as an appendix. */
    fun printOrder(s: WorkspaceState, comparison: Boolean): List<Pair<Int, QuoteOption>> {
        val x = QeQuote.syncActiveOption(s)
        val active = x.activeQuoteOption
        val first = listOf(active to x.quoteOptions[active])
        return if (!comparison) first else first + x.quoteOptions.withIndex().filter { it.index != active }.map { it.index to it.value }
    }

    fun optionLabel(o: QuoteOption, i: Int): String {
        val name = o.name.ifEmpty { "Option " + (i + 1) }
        val sp = o.species.orEmpty()
        return if (sp.isNotEmpty() && !name.contains(sp, ignoreCase = true)) "$name ($sp)" else name
    }
}
