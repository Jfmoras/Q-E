package com.moraswood.qande.logic

import com.moraswood.qande.data.HoppusState
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.YieldEntryEntity

/**
 * Feature H — Yield Tracker maths (pure Kotlin).
 * Yield % = output CFT ÷ input CFT × 100; loss % = 100 − yield %. Waste breakdown (offcuts/breakage/other) is
 * recorded separately; whatever is neither output nor recorded waste is "unaccounted".
 * Averages per species / job type are volume-weighted (Σ output ÷ Σ input), with the simple mean shown alongside.
 */
object QeYield {
    const val M3_TO_CFT = 35.314666721488590 // 1 m³ = (1/0.3048)³ ft³
    val UNITS = listOf("CFT" to "CFT", "m3" to "m³")
    val JOB_TYPES = listOf("Doors", "Windows", "Door frames", "Furniture", "Planks", "Beams / rafters", "Sawing only")

    fun toCft(volume: Double, unit: String): Double = if (unit == "m3") volume * M3_TO_CFT else volume
    fun inputCft(e: YieldEntryEntity): Double = toCft(e.inputVolume, e.inputUnit)

    /** Finished output from rows: every row computed as CFT (mode forced) with the V10.1 calcRow. */
    fun rowsCft(rows: List<Row>): Double = rows.sumOf { QeMath.calcRow(it.copy(mode = "CFT")).value.takeIf { v -> v.isFinite() } ?: 0.0 }

    fun outputCft(e: YieldEntryEntity, rows: List<Row> = decodeRows(e.outputRowsJson)): Double =
        if (e.outputMode == "rows") rowsCft(rows) else e.outputVolume

    fun yieldPct(input: Double, output: Double): Double? = if (input > 0) output / input * 100 else null
    fun lossPct(input: Double, output: Double): Double? = yieldPct(input, output)?.let { 100 - it }
    fun waste(e: YieldEntryEntity): Double = e.offcuts + e.breakage + e.other
    fun unaccounted(e: YieldEntryEntity): Double = inputCft(e) - outputCft(e) - waste(e)

    data class Aggregate(val key: String, val count: Int, val input: Double, val output: Double, val weightedYield: Double?, val meanYield: Double?)

    fun aggregate(entries: List<YieldEntryEntity>, key: (YieldEntryEntity) -> String): List<Aggregate> =
        entries.groupBy { key(it).trim().ifEmpty { "—" } }.map { (k, list) ->
            val input = list.sumOf { inputCft(it) }
            val output = list.sumOf { outputCft(it) }
            val ys = list.mapNotNull { yieldPct(inputCft(it), outputCft(it)) }
            Aggregate(k, list.size, input, output, yieldPct(input, output), if (ys.isEmpty()) null else ys.average())
        }.sortedBy { it.key.lowercase() }

    fun bySpecies(entries: List<YieldEntryEntity>) = aggregate(entries) { it.species }
    fun byJobType(entries: List<YieldEntryEntity>) = aggregate(entries) { it.jobType }

    /** Trend points (oldest first) — only entries with a yield; the chart is drawn when there are 2+. */
    fun trend(entries: List<YieldEntryEntity>): List<Pair<String, Double>> =
        entries.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id }))
            .mapNotNull { e -> yieldPct(inputCft(e), outputCft(e))?.let { e.date to it } }

    /** Log details text from the Hoppus sheet. */
    fun hoppusDetails(h: HoppusState): String = h.rows.mapIndexedNotNull { i, r ->
        if (r.length.isBlank() && r.girth.isBlank()) null
        else "Log ${i + 1}: ${r.length.ifBlank { "0" }} ft × ${r.girth.ifBlank { "0" }} in girth = ${QeFormat.toFixed(QeMath.hoppusVolume(r.length, r.girth), 2)} CFT"
    }.joinToString("\n")

    fun hoppusTotal(h: HoppusState): Double = h.rows.sumOf { QeMath.hoppusVolume(it.length, it.girth) }

    fun decodeRows(json: String): List<Row> =
        runCatching { com.moraswood.qande.data.QeJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Row.serializer()), json.ifBlank { "[]" }) }.getOrDefault(emptyList())
    fun encodeRows(rows: List<Row>): String =
        com.moraswood.qande.data.QeJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Row.serializer()), rows)

    fun pct(x: Double?): String = x?.let { QeFormat.toFixed(it, 1) + "%" } ?: "n/a"
}
