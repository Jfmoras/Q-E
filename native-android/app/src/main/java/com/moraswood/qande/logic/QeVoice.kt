package com.moraswood.qande.logic

/**
 * Feature M — spoken measurements → calculator fields (pure Kotlin).
 * "seven by four" → 7, 4 into consecutive fields; "7 feet 4 inches" → 88 in; "four and a half" → 4.5;
 * "quantity 4" / "4 pieces" → the Pieces field; units (feet, inches, cm, mm, metres) set the field's unit where it has one.
 */
object QeVoice {
    data class Measure(val value: Double, val unit: String?)
    data class Parsed(val measures: List<Measure>, val qty: Double?) { val isEmpty get() = measures.isEmpty() && qty == null }

    private val ONES = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val FRACTIONS = mapOf("half" to 0.5, "quarter" to 0.25, "quarters" to 0.25)
    private val UNITS = mapOf(
        "feet" to "ft", "foot" to "ft", "ft" to "ft", "'" to "ft",
        "inch" to "in", "inches" to "in", "in" to "in", "\"" to "in",
        "cm" to "cm", "centimeter" to "cm", "centimeters" to "cm", "centimetre" to "cm", "centimetres" to "cm",
        "mm" to "mm", "millimeter" to "mm", "millimeters" to "mm", "millimetre" to "mm", "millimetres" to "mm",
        "m" to "m", "meter" to "m", "meters" to "m", "metre" to "m", "metres" to "m",
    )
    private val SPLIT = setOf("by", "x", "×", "into", "cross", "buy", "bye")
    private val QTY_BEFORE = setOf("quantity", "qty", "number")
    private val QTY_AFTER = setOf("pieces", "piece", "pcs", "nos", "numbers")

    private fun tokens(text: String): List<String> = text.lowercase()
        .replace("½", " and a half")
        .replace(Regex("(\\d)\\s*[x×*]\\s*(\\d)"), "$1 x $2")
        .replace(Regex("(\\d)([a-z'\"])"), "$1 $2")
        .replace(Regex("[-,;]"), " ")
        .split(Regex("\\s+")).filter { it.isNotBlank() }

    /** One number starting at [i0] → (value, next index), or null. */
    private fun number(t: List<String>, i0: Int): Pair<Double, Int>? {
        var i = i0
        var v: Double
        val digit = t.getOrNull(i)?.toDoubleOrNull()
        if (digit != null) { v = digit; i++ }
        else {
            var cur = 0.0; var found = false
            while (i < t.size) {
                val w = t[i]
                when {
                    w in TENS -> { cur += TENS.getValue(w); found = true; i++ }
                    w in ONES -> { cur += ONES.getValue(w); found = true; i++ }
                    w == "hundred" && found -> { cur *= 100; i++ }
                    else -> break
                }
            }
            if (!found) {
                if (t.getOrNull(i) == "half") return 0.5 to i + 1
                if (t.getOrNull(i) in setOf("a", "an") && t.getOrNull(i + 1) == "half") return 0.5 to i + 2
                return null
            }
            v = cur
        }
        if (t.getOrNull(i) == "point") {
            var j = i + 1; val sb = StringBuilder()
            while (j < t.size) { val d = t[j].toIntOrNull()?.takeIf { it in 0..9 } ?: ONES[t[j]]?.takeIf { it in 0..9 } ?: break; sb.append(d); j++ }
            if (sb.isNotEmpty()) { v += "0.$sb".toDouble(); i = j }
        }
        if (t.getOrNull(i) == "and") {
            var j = i + 1; var mult = 1.0
            when (t.getOrNull(j)) { "a", "an", "one" -> j++; "three" -> { mult = 3.0; j++ } }
            FRACTIONS[t.getOrNull(j)]?.let { f -> v += f * mult; i = j + 1 }
        } else if (t.getOrNull(i) == "half") { v += 0.5; i++ }
        return v to i
    }

    fun parse(text: String): Parsed {
        val t = tokens(text)
        val measures = mutableListOf<Measure>()
        var qty: Double? = null
        var seg = mutableListOf<Measure>()
        fun flush() {
            if (seg.size == 2 && seg[0].unit == "ft" && (seg[1].unit == "in" || seg[1].unit == null)) measures += Measure(seg[0].value * 12 + seg[1].value, "in")
            else measures += seg
            seg = mutableListOf()
        }
        var i = 0
        while (i < t.size) {
            val w = t[i]
            if (w in SPLIT) { flush(); i++; continue }
            if (w in QTY_BEFORE) {
                val r = number(t, i + 1)
                if (r != null) { qty = r.first; i = r.second; continue }
                i++; continue
            }
            val r = number(t, i)
            if (r == null) { i++; continue }
            var (v, n) = r
            val next = t.getOrNull(n)
            if (next != null && next in QTY_AFTER) { qty = v; i = n + 1; continue }
            val unit = next?.let { UNITS[it] }
            if (unit != null) n++
            seg += Measure(v, unit)
            i = n
        }
        flush()
        return Parsed(measures, qty)
    }

    private val FIELDS = listOf("l", "w", "t")

    /** Field assignments (key → value, unit key → unit) starting at the tapped field. */
    fun assign(startKey: String, p: Parsed): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        if (startKey == "q") {
            (p.qty ?: p.measures.firstOrNull()?.value)?.let { out += "q" to fmt(it) }
            return out
        }
        var k = FIELDS.indexOf(startKey).coerceAtLeast(0)
        for (m in p.measures) {
            if (k >= FIELDS.size) { if (p.qty == null) out += "q" to fmt(m.value); break }
            out += FIELDS[k] to fmt(m.value)
            m.unit?.let { out += "${FIELDS[k]}Unit" to it }
            k++
        }
        p.qty?.let { out += "q" to fmt(it) }
        return out
    }

    fun fmt(x: Double): String = QeFormat.toFixed(x, 4).trimEnd('0').trimEnd('.')

    const val PROMPT = "Say a size, e.g. “seven by four”, “7 feet 4 inches”, “four and a half”, “quantity 4”"
    const val NO_MATCH = "Didn't catch a measurement. Try “seven by four” or “quantity 4”."
    const val UNAVAILABLE = "Voice input isn't available on this phone."
}
