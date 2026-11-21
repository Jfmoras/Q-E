package com.moraswood.qande.logic

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Row

/**
 * Exact ports of the V10.1 app.js calculation functions (V10.1-inventory.md §4).
 * Do not "improve" these: they must return the same numbers as the WebView app.
 */
object QeMath {
    /** `const num = v => Number(v) || 0` */
    fun num(v: String?): Double {
        val s = v?.trim() ?: return 0.0
        if (s.isEmpty()) return 0.0
        val d = when {
            s.startsWith("0x") || s.startsWith("0X") -> s.substring(2).toLongOrNull(16)?.toDouble()
            s.startsWith("0b") || s.startsWith("0B") -> s.substring(2).toLongOrNull(2)?.toDouble()
            s.startsWith("0o") || s.startsWith("0O") -> s.substring(2).toLongOrNull(8)?.toDouble()
            s.any { it == 'f' || it == 'F' || it == 'd' || it == 'D' } && !s.contains("Infinity") -> null
            else -> s.toDoubleOrNull()
        } ?: return 0.0
        return if (d.isNaN() || d == 0.0) 0.0 else d
    }

    /** `toFeet(value, unit='ft')` */
    fun toFeet(value: String?, unit: String = "ft"): Double {
        val v = num(value)
        return when (unit) {
            "in" -> v / 12
            "cm" -> v / 30.48
            "mm" -> v / 304.8
            "m" -> v * 3.280839895
            else -> v
        }
    }

    /** `toInches(value, unit='in')` */
    fun toInches(value: String?, unit: String = "in"): Double {
        val v = num(value)
        return when (unit) {
            "ft" -> v * 12
            "cm" -> v / 2.54
            "mm" -> v / 25.4
            "m" -> v * 39.37007874
            else -> v
        }
    }

    /** Calculator unit options, in V10.1 order. */
    val lengthUnits = listOf("ft", "in", "cm", "mm", "m")

    data class RowCalc(val mode: String, val value: Double, val lFt: Double, val wIn: Double, val tIn: Double)

    /** `calcRow(r)`: L(ft) × W(in) × T(in) × pieces ÷ (CFT ? 144 : 37.5). */
    fun calcRow(r: Row): RowCalc {
        val lFt = toFeet(r.l, r.lUnit.ifEmpty { "ft" })
        val wIn = toInches(r.w, r.wUnit.ifEmpty { "in" })
        val tIn = toInches(r.t, r.tUnit.ifEmpty { "in" })
        val raw = lFt * wIn * tIn * num(r.q)
        return RowCalc(r.mode, raw / (if (r.mode == "CFT") 144.0 else 37.5), lFt, wIn, tIn)
    }

    /** `surfaceSqft(r)`: all six faces × pieces, in sq.ft. */
    fun surfaceSqft(r: Row): Double {
        val l = toFeet(r.l, r.lUnit.ifEmpty { "ft" })
        val w = toInches(r.w, r.wUnit.ifEmpty { "in" }) / 12
        val t = toInches(r.t, r.tUnit.ifEmpty { "in" }) / 12
        val q = num(r.q)
        return 2 * (l * w + l * t + w * t) * q
    }

    data class Totals(val cft: Double, val kol: Double)

    /** `itemTotals(item)` — only the CFT and KOL buckets are reported (other modes are ignored like in JS). */
    fun itemTotals(item: CalcItem): Totals {
        var cft = 0.0
        var kol = 0.0
        item.rows.forEach { r ->
            val x = calcRow(r)
            when (x.mode) { "CFT" -> cft += x.value; "KOL" -> kol += x.value }
        }
        return Totals(cft, kol)
    }

    fun grandTotals(items: List<CalcItem>): Totals =
        items.fold(Totals(0.0, 0.0)) { a, it -> val t = itemTotals(it); Totals(a.cft + t.cft, a.kol + t.kol) }

    fun itemSurface(item: CalcItem): Double = item.rows.sumOf { surfaceSqft(it) }

    /** Hoppus quarter-girth: length(ft) × girth(in)² ÷ 2304. */
    fun hoppusVolume(length: String, girth: String): Double = num(length) * num(girth) * num(girth) / 2304

    /** Length converter units (Settings), metres per unit. */
    val converterUnits = linkedMapOf(
        "mm" to ("Millimetres (mm)" to 0.001),
        "cm" to ("Centimetres (cm)" to 0.01),
        "m" to ("Metres (m)" to 1.0),
        "in" to ("Inches (in)" to 0.0254),
        "ft" to ("Feet (ft)" to 0.3048),
        "yd" to ("Yards (yd)" to 0.9144),
        "mi" to ("Miles (mi)" to 1609.344),
    )

    /** V10.1 `rowHasData` test used by Send to Estimation and hasProjectWork: any of l/w/t/q typed. */
    fun Row.hasData(): Boolean = l.isNotEmpty() || w.isNotEmpty() || t.isNotEmpty() || q.isNotEmpty()
}
