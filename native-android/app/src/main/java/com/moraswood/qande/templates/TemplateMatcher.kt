package com.moraswood.qande.templates

import com.moraswood.qande.data.Row
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline template matching (plain Kotlin, no AI / network).
 *
 * 1. Furniture types are normalised (lowercase, punctuation stripped, simple de-pluralising) and compared
 *    with a token-sorted Levenshtein ratio, so "King Bed", "king beds" and "king-bed" are the same type.
 * 2. Each past job (one calculator item) becomes a list of components converted to common units with the
 *    V10.1 conversions (length → feet, width/thickness → inches).
 * 3. Two jobs are compared by greedily pairing their most similar components; the pair score combines
 *    relative length / width / thickness / quantity differences and CFT/KOL mode. Unpaired components count as 0,
 *    so different row counts lower the score.
 * 4. Jobs of the same type are clustered around the job with the most close neighbours (score ≥ [JOB_THRESHOLD]).
 *    A cluster of 2+ jobs yields a suggestion whose rows are the per-component medians, written back in the
 *    units the representative job used.
 */
object TemplateMatcher {
    const val TYPE_THRESHOLD = 0.8
    const val JOB_THRESHOLD = 0.75

    data class Component(
        val lengthFt: Double,
        val widthIn: Double,
        val thicknessIn: Double,
        val qty: Double,
        val mode: String,
        /** Original row (units as entered) for writing suggestions back. */
        val row: Row,
    )

    data class Job(val key: String, val type: String, val components: List<Component>, val recency: String = "")

    data class Suggestion(val type: String, val jobCount: Int, val rows: List<Row>)

    fun componentOf(row: Row): Component {
        val c = QeMath.calcRow(row)
        return Component(c.lFt, c.wIn, c.tIn, QeMath.num(row.q), row.mode, row)
    }

    private fun singular(token: String): String = when {
        token.length > 4 && token.endsWith("ies") -> token.dropLast(3) + "y"
        token.length > 3 && (token.endsWith("ches") || token.endsWith("shes") || token.endsWith("xes") || token.endsWith("sses")) -> token.dropLast(2)
        token.length > 3 && token.endsWith("s") && !token.endsWith("ss") -> token.dropLast(1)
        else -> token
    }

    /** "  King-Beds " -> "king bed" */
    fun normalizeType(raw: String): String =
        raw.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { singular(it) }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 0..1 similarity of two already-normalised types. */
    fun typeSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val sa = a.split(' ').sorted().joinToString(" ")
        val sb = b.split(' ').sorted().joinToString(" ")
        if (sa == sb) return 1.0
        return 1.0 - levenshtein(sa, sb).toDouble() / max(sa.length, sb.length)
    }

    /** 1 when equal, falling linearly with the relative difference. */
    fun rel(a: Double, b: Double): Double {
        val m = max(abs(a), abs(b))
        if (m == 0.0) return 1.0
        return (1.0 - abs(a - b) / m).coerceIn(0.0, 1.0)
    }

    fun componentScore(x: Component, y: Component): Double {
        val dims = 0.3 * rel(x.lengthFt, y.lengthFt) + 0.3 * rel(x.widthIn, y.widthIn) +
            0.25 * rel(x.thicknessIn, y.thicknessIn) + 0.15 * rel(x.qty, y.qty)
        return if (x.mode == y.mode) dims else dims * 0.7
    }

    /** Greedy best-first pairing of components: list of (indexInA, indexInB, score). */
    fun pairComponents(a: List<Component>, b: List<Component>): List<Triple<Int, Int, Double>> {
        val all = ArrayList<Triple<Int, Int, Double>>()
        for (i in a.indices) for (j in b.indices) all.add(Triple(i, j, componentScore(a[i], b[j])))
        all.sortWith(compareByDescending<Triple<Int, Int, Double>> { it.third }.thenBy { it.first }.thenBy { it.second })
        val usedA = HashSet<Int>()
        val usedB = HashSet<Int>()
        val pairs = ArrayList<Triple<Int, Int, Double>>()
        for (p in all) if (p.first !in usedA && p.second !in usedB) { usedA.add(p.first); usedB.add(p.second); pairs.add(p) }
        return pairs
    }

    /** 0..1 pattern similarity of two jobs (row count + dimensions + thickness + qty + mode). */
    fun jobSimilarity(a: List<Component>, b: List<Component>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val pairs = pairComponents(a, b)
        return pairs.sumOf { it.third } / max(a.size, b.size)
    }

    fun median(values: List<Double>): Double {
        val s = values.sorted()
        if (s.isEmpty()) return 0.0
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun fromFeet(ft: Double, unit: String) = when (unit) {
        "in" -> ft * 12; "cm" -> ft * 30.48; "mm" -> ft * 304.8; "m" -> ft / 3.280839895; else -> ft
    }

    private fun fromInches(inches: Double, unit: String) = when (unit) {
        "ft" -> inches / 12; "cm" -> inches * 2.54; "mm" -> inches * 25.4; "m" -> inches / 39.37007874; else -> inches
    }

    private fun text(v: Double): String = if (v == 0.0) "" else QeFormat.fmtMeasure(v)

    /**
     * Finds the best cluster for [type] among [jobs]. Returns null unless 2+ jobs of that type have a
     * closely matching component pattern.
     */
    fun suggest(type: String, jobs: List<Job>): Suggestion? {
        val target = normalizeType(type)
        if (target.isEmpty()) return null
        val candidates = jobs.filter { it.components.isNotEmpty() && typeSimilarity(target, it.type) >= TYPE_THRESHOLD }
        if (candidates.size < 2) return null
        data class Scored(val job: Job, val neighbours: List<Job>, val avg: Double)
        val scored = candidates.map { j ->
            val sims = candidates.filter { it !== j }.map { it to jobSimilarity(j.components, it.components) }
            val close = sims.filter { it.second >= JOB_THRESHOLD }
            Scored(j, close.map { it.first }, if (close.isEmpty()) 0.0 else close.sumOf { it.second } / close.size)
        }
        val best = scored.sortedWith(
            compareByDescending<Scored> { it.neighbours.size }.thenByDescending { it.avg }.thenByDescending { it.job.recency },
        ).first()
        if (best.neighbours.isEmpty()) return null
        val medoid = best.job
        val members = best.neighbours
        val rows = medoid.components.mapIndexed { i, base ->
            val matched = ArrayList<Component>().apply { add(base) }
            members.forEach { m -> pairComponents(medoid.components, m.components).firstOrNull { it.first == i }?.let { matched.add(m.components[it.second]) } }
            val r = base.row
            val lUnit = r.lUnit.ifEmpty { "ft" }; val wUnit = r.wUnit.ifEmpty { "in" }; val tUnit = r.tUnit.ifEmpty { "in" }
            val q = median(matched.map { it.qty })
            Row(
                l = text(fromFeet(median(matched.map { it.lengthFt }), lUnit)), lUnit = lUnit,
                w = text(fromInches(median(matched.map { it.widthIn }), wUnit)), wUnit = wUnit,
                t = text(fromInches(median(matched.map { it.thicknessIn }), tUnit)), tUnit = tUnit,
                q = text(if (matched.all { it.qty % 1.0 == 0.0 }) Math.round(q).toDouble() else q),
                mode = r.mode.ifEmpty { "CFT" },
            )
        }
        return Suggestion(target, members.size + 1, rows)
    }

    /** "king bed" -> "king beds" for the banner text. */
    fun plural(type: String): String = when {
        type.endsWith("s") || type.endsWith("x") || type.endsWith("ch") || type.endsWith("sh") -> type + "es"
        type.endsWith("y") && type.length > 1 && type[type.length - 2] !in "aeiou" -> type.dropLast(1) + "ies"
        else -> type + "s"
    }
}
