package com.moraswood.qande.logic

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.Row
import com.moraswood.qande.logic.QeMath.num

/**
 * Feature G — automatic square feet.
 *
 * V10.1 (app.js): labour per sq.ft = toFeet(labour length) × toFeet(labour width) × labour qty × rate, from its own
 * L/W fields on the Labour card; polish per sq.ft = (override, else the item's timber surface — all six faces of every
 * row × pieces) × rate.
 *
 * Native: item area = Σ rows length(ft) × width(ft) × pieces using the V10.1 unit helpers; a row marked vertical
 * ("LT") uses length × thickness. Shown read-only with an override. When the labour card is per sq.ft and its own
 * length AND width are both blank, labour uses this area (× labour qty when filled). Filled labour L/W — every V10.1 job
 * that priced labour per sq.ft — keeps the exact V10.1 formula. Polish is unchanged (V10.1 surface + override).
 */
object QeArea {
    fun rowArea(r: Row): Double {
        val l = QeMath.toFeet(r.l, r.lUnit.ifEmpty { "ft" })
        val side = if (r.areaFace == "LT") QeMath.toInches(r.t, r.tUnit.ifEmpty { "in" }) else QeMath.toInches(r.w, r.wUnit.ifEmpty { "in" })
        val a = l * (side / 12) * num(r.q)
        return if (a.isFinite()) a else 0.0
    }

    fun itemArea(item: CalcItem): Double = item.rows.sumOf { rowArea(it) }

    /** False when no row has a usable length and width/thickness (shown as "n/a"). */
    fun hasArea(item: CalcItem): Boolean = item.rows.any { rowArea(it) > 0 }

    fun areaUsed(item: CalcItem, c: Costing?): Double {
        val o = num(c?.areaOverride.orEmpty())
        return if (o != 0.0) o else itemArea(item)
    }

    /** Labour per sq.ft with its own L/W blank → automatic area. */
    fun autoLabour(c: Costing): Boolean =
        c.labourMode.ifEmpty { "sqft" } != "lumpsum" && c.labourLength.isBlank() && c.labourWidth.isBlank()

    fun labourArea(item: CalcItem, c: Costing): Double =
        if (autoLabour(c)) areaUsed(item, c) * (if (c.labourQty.isBlank()) 1.0 else num(c.labourQty)) else QeEstimate.labourAreaSqft(c)

    /** "12.34" or "n/a" for display and for quotation lines. */
    fun label(item: CalcItem, c: Costing?): String =
        if (num(c?.areaOverride.orEmpty()) == 0.0 && !hasArea(item)) "n/a" else QeFormat.toFixed(areaUsed(item, c), 2)
}
