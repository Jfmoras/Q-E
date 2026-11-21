package com.moraswood.qande.yieldtracker

import com.moraswood.qande.data.Row
import com.moraswood.qande.data.YieldEntryEntity
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath.num
import com.moraswood.qande.logic.QeYield

/** Editor state for one yield entry (text fields as typed). */
data class YieldDraft(
    val id: Long = 0,
    val createdAt: Long = 0,
    val date: String = java.time.LocalDate.now().toString(),
    val species: String = "",
    val jobType: String = "",
    val inputVolume: String = "",
    val inputUnit: String = "CFT",
    val outputMode: String = "direct",
    val outputVolume: String = "",
    val outputRows: List<Row> = listOf(Row.blank()),
    val offcuts: String = "",
    val breakage: String = "",
    val other: String = "",
    val logDetails: String = "",
    val notes: String = "",
) {
    fun toEntity(created: Long) = YieldEntryEntity(
        id = id, date = date.trim(), species = species.trim(), jobType = jobType.trim(),
        inputVolume = num(inputVolume), inputUnit = inputUnit, outputMode = outputMode, outputVolume = num(outputVolume),
        outputRowsJson = QeYield.encodeRows(outputRows.filter { it.l.isNotBlank() || it.w.isNotBlank() || it.t.isNotBlank() || it.q.isNotBlank() }),
        offcuts = num(offcuts), breakage = num(breakage), other = num(other),
        logDetails = logDetails, notes = notes, createdAt = created,
    )

    companion object {
        private fun s(x: Double) = if (x == 0.0) "" else QeFormat.toFixed(x, 3).trimEnd('0').trimEnd('.')
        fun of(e: YieldEntryEntity) = YieldDraft(
            id = e.id, createdAt = e.createdAt, date = e.date, species = e.species, jobType = e.jobType,
            inputVolume = s(e.inputVolume), inputUnit = e.inputUnit, outputMode = e.outputMode, outputVolume = s(e.outputVolume),
            outputRows = QeYield.decodeRows(e.outputRowsJson).ifEmpty { listOf(Row.blank()) },
            offcuts = s(e.offcuts), breakage = s(e.breakage), other = s(e.other), logDetails = e.logDetails, notes = e.notes,
        )
    }
}
