package com.moraswood.qande.logic

import java.math.BigDecimal
import java.math.RoundingMode

/** Number formatting identical to the V10.1 JavaScript output. */
object QeFormat {
    /** JS `Number.prototype.toFixed(digits)` (exact binary value, ties away from zero). */
    fun toFixed(x: Double, digits: Int): String {
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
        val bd = BigDecimal(x).setScale(digits, RoundingMode.HALF_UP)
        val text = bd.abs().toPlainString()
        return if (x < 0) "-$text" else text // JS: x < 0 keeps the sign even when it rounds to zero
    }

    /** `fmtMeasure(v)`: 0 -> "0", else Number(v.toFixed(3)).toString(). */
    fun fmtMeasure(v: Double): String {
        if (v == 0.0 || v.isNaN()) return "0"
        val bd = BigDecimal(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        if (bd.signum() == 0) return "0"
        return bd.toPlainString()
    }

    /** Indian digit grouping of a non-negative integer string: 1234567 -> 12,34,567. */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /** `money(v)` = Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}). */
    fun money(v: Double): String {
        val x = if (v.isNaN() || v == 0.0) 0.0 else v // Number(v)||0 turns -0 and NaN into 0
        val rounded = BigDecimal(x).setScale(0, RoundingMode.HALF_UP).abs().toPlainString()
        return (if (x < 0) "-" else "") + "₹" + groupIndian(rounded)
    }

    /** `rs(v)` = "Rs. " + Math.round(v).toLocaleString('en-IN') (print sheet). */
    fun rs(v: Double): String {
        val x = if (v.isNaN()) 0.0 else v
        val r = Math.floor(x + 0.5) // JS Math.round: ties toward +Infinity
        val text = groupIndian(BigDecimal(r).abs().setScale(0, RoundingMode.UNNECESSARY).toPlainString())
        return "Rs. " + (if (r < 0) "-" else "") + text
    }
}
