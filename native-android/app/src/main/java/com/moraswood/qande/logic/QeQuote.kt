package com.moraswood.qande.logic

import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.QuoteOption
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeMath.num
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Exact ports of the V10.1 app.js quotation functions (totalsForOption, quoteTotals, quote options). */
object QeQuote {
    data class Totals(val subtotal: Double, val discount: Double, val taxable: Double, val gst: Double, val grand: Double)

    private fun totals(items: List<QuoteItem>, discountType: String, discountValue: String, gstPercent: String): Totals {
        val subtotal = items.fold(0.0) { s, x -> s + num(x.qty) * num(x.unitPrice) }
        val raw = if (discountType == "percent") subtotal * num(discountValue) / 100 else num(discountValue)
        val discount = minOf(subtotal, maxOf(0.0, raw))
        val taxable = maxOf(0.0, subtotal - discount)
        val gst = taxable * num(gstPercent) / 100
        return Totals(subtotal, discount, taxable, gst, taxable + gst)
    }

    /** `totalsForOption(opt)` */
    fun totalsForOption(opt: QuoteOption): Totals = totals(opt.items, opt.discountType, opt.discountValue, opt.gstPercent)

    /** `quoteTotals()` — the active option as edited on screen. */
    fun quoteTotals(s: WorkspaceState): Totals = totals(s.quoteItems, s.quote.discountType, s.quote.discountValue, s.quote.gstPercent)

    /** `ensureQuoteOptions()` */
    fun ensureQuoteOptions(s: WorkspaceState): WorkspaceState {
        val options = s.quoteOptions.ifEmpty {
            listOf(QuoteOption("Option 1", s.quoteItems, s.quote.discountType, s.quote.discountValue, s.quote.gstPercent))
        }
        val active = maxOf(0, minOf(s.activeQuoteOption, options.size - 1))
        return if (options === s.quoteOptions && active == s.activeQuoteOption) s else s.copy(quoteOptions = options, activeQuoteOption = active)
    }

    /** `syncActiveOption()`: the on-screen items/discount/GST are written into the active option. */
    fun syncActiveOption(s0: WorkspaceState): WorkspaceState {
        val s = ensureQuoteOptions(s0)
        val opt = s.quoteOptions[s.activeQuoteOption]
        val synced = opt.copy(items = s.quoteItems, discountType = s.quote.discountType, discountValue = s.quote.discountValue, gstPercent = s.quote.gstPercent)
        if (synced == opt) return s
        return s.copy(quoteOptions = s.quoteOptions.mapIndexed { i, o -> if (i == s.activeQuoteOption) synced else o })
    }

    /** `activateOption(i)` */
    fun activateOption(s0: WorkspaceState, i: Int): WorkspaceState {
        val s = syncActiveOption(s0)
        val opt = s.quoteOptions.getOrNull(i) ?: return s
        return s.copy(
            activeQuoteOption = i,
            quoteItems = opt.items, // JS `opt.items||[blankQuote()]`: an empty array is kept
            quote = s.quote.copy(discountType = opt.discountType.ifEmpty { "amount" }, discountValue = opt.discountValue, gstPercent = opt.gstPercent),
        )
    }

    /** `#addQuoteOption` */
    fun addOption(s0: WorkspaceState): WorkspaceState {
        val s = syncActiveOption(s0)
        val opt = QuoteOption("Option " + (s.quoteOptions.size + 1), listOf(QuoteItem.blank()), s.quote.discountType, "", s.quote.gstPercent)
        return activateOption(s.copy(quoteOptions = s.quoteOptions + opt), s.quoteOptions.size)
    }

    /** `#duplicateQuoteOption` */
    fun duplicateOption(s0: WorkspaceState): WorkspaceState {
        val s = syncActiveOption(s0)
        val src = s.quoteOptions[s.activeQuoteOption]
        val copy = src.copy(name = src.name + " Copy")
        return activateOption(s.copy(quoteOptions = s.quoteOptions + copy), s.quoteOptions.size)
    }

    /** `#removeQuoteOption` after its checks (≥2 options, confirmed). */
    fun removeOption(s0: WorkspaceState): WorkspaceState {
        val s = syncActiveOption(s0)
        if (s.quoteOptions.size <= 1) return s
        val options = s.quoteOptions.filterIndexed { i, _ -> i != s.activeQuoteOption }
        val opt = options[0]
        return s.copy(
            quoteOptions = options, activeQuoteOption = 0, quoteItems = opt.items,
            quote = s.quote.copy(discountType = opt.discountType, discountValue = opt.discountValue, gstPercent = opt.gstPercent),
        )
    }

    /** JS `String(Number(x))` for the small numbers shown in labels (10 -> "10", 12.5 -> "12.5"). */
    fun jsNumber(d: Double): String = when {
        d.isNaN() -> "NaN"
        d == Math.floor(d) && Math.abs(d) < 1e21 -> java.math.BigDecimal(d).toPlainString().let { if (it == "-0") "0" else it }
        else -> d.toString()
    }

    /** `Discount` label on screen: " (n%)" only for a non-zero percent discount. */
    fun discountLabel(discountType: String, discountValue: String): String =
        "Discount" + if (discountType == "percent" && num(discountValue) != 0.0) " (${jsNumber(num(discountValue))}%)" else ""

    /** `new Date().toLocaleDateString('en-IN',{day:'2-digit',month:'long',year:'numeric'})` → "21 November 2026". */
    fun longDate(date: LocalDate = LocalDate.now()): String = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH))

    /** `new Date().toLocaleDateString('en-IN')` → "21/11/2026". */
    fun shortDate(date: LocalDate = LocalDate.now()): String = "${date.dayOfMonth}/${date.monthValue}/${date.year}"

    /** Print sheet "Units" column: `[qty,unit].filter(Boolean).join(' ')||'-'`. */
    fun unitsText(x: QuoteItem): String = listOf(x.qty, x.unit).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "-" }

    /** First 4 images of an option, as on the print sheet. */
    fun optionImages(opt: QuoteOption): List<String> = opt.items.flatMap { it.images }.take(4)
}
