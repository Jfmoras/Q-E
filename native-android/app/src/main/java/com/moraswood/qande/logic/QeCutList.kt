package com.moraswood.qande.logic

import com.moraswood.qande.data.DwDefaults
import com.moraswood.qande.data.DwStructure
import com.moraswood.qande.data.WorkspaceState

/**
 * Feature K — cut list (printed only). Every label is bilingual "English / ಕನ್ನಡ" — Kannada appears ONLY here, never in
 * the app UI or on quotations. "Juttu / ಜುಟ್ಟು" is a trade word (transliterated, not translated); architrave is
 * "ವೊಟ್ಟುಪಟ್ಟಿ" (vottupatti). Terms are listed in PLAN.md for a fluent speaker to check.
 */
object QeCutList {
    fun bi(en: String, kn: String) = "$en / $kn"

    val TITLE = bi("Cut List", "ಕತ್ತರಿಸುವ ಪಟ್ಟಿ")
    val JOB = bi("Job", "ಕೆಲಸ")
    val DATE = bi("Date", "ದಿನಾಂಕ")
    val TOTAL_PIECES = bi("Total pieces", "ಒಟ್ಟು ತುಂಡುಗಳು")
    val TOTAL_CFT = bi("Total CFT", "ಒಟ್ಟು CFT")
    val ENTRIES = bi("Entries", "ನಮೂದುಗಳು")
    val SECTION_TOTAL = bi("Section total", "ವಿಭಾಗದ ಒಟ್ಟು")
    val PIECES = bi("pieces", "ತುಂಡುಗಳು")
    val IDENTICAL = bi("identical sets", "ಒಂದೇ ರೀತಿಯ ಸೆಟ್‌ಗಳು")
    val QTY_WORD = bi("Qty", "ಸಂಖ್ಯೆ")
    val NONE = bi("No door or window structures in this project.", "ಈ ಕೆಲಸದಲ್ಲಿ ಬಾಗಿಲು ಅಥವಾ ಕಿಟಕಿ ಇಲ್ಲ.")

    val COLUMNS = listOf(
        bi("Piece", "ತುಂಡು"),
        bi("Finished length (after wastage)", "ಅಂತಿಮ ಉದ್ದ (ವ್ಯರ್ಥ ಸೇರಿ)"),
        bi("Width", "ಅಗಲ"),
        bi("Thickness", "ದಪ್ಪ"),
        bi("Qty", "ಸಂಖ್ಯೆ"),
        bi("Species", "ಮರದ ಜಾತಿ"),
        "CFT",
    )

    val GROUP_TITLES = mapOf(
        QeDoorWindow.GROUP_FRAME to bi("Frame Support", "ಚೌಕಟ್ಟು ಬೆಂಬಲ"),
        QeDoorWindow.GROUP_PLANK to bi("Planks", "ಹಲಗೆಗಳು"),
        QeDoorWindow.GROUP_ARCH to bi("Architrave", "ವೊಟ್ಟುಪಟ್ಟಿ"),
        QeDoorWindow.GROUP_WINDOW to bi("Window Supports", "ಕಿಟಕಿ ಬೆಂಬಲ"),
    )

    val PIECE_LABELS = mapOf(
        "Long (side)" to bi("Long (side)", "ಉದ್ದ ತುಂಡು (ಬದಿ)"),
        "Short (top/bottom)" to bi("Short (top/bottom)", "ಸಣ್ಣ ತುಂಡು (ಮೇಲೆ/ಕೆಳಗೆ)"),
        "Centre support" to bi("Centre support", "ಮಧ್ಯ ಬೆಂಬಲ"),
        "Plank" to bi("Plank", "ಹಲಗೆ"),
        "Plank (trimmed)" to bi("Plank (trimmed)", "ಹಲಗೆ (ಕತ್ತರಿಸಿದ)"),
        "Architrave header" to bi("Architrave header", "ವೊಟ್ಟುಪಟ್ಟಿ ಮೇಲ್ಭಾಗ"),
        "Architrave jamb" to bi("Architrave jamb", "ವೊಟ್ಟುಪಟ್ಟಿ ಬದಿ"),
        "Architrave bottom" to bi("Architrave bottom", "ವೊಟ್ಟುಪಟ್ಟಿ ಕೆಳಭಾಗ"),
    )

    val KIND_LABELS = mapOf(
        "main_door" to bi("Main door", "ಮುಖ್ಯ ಬಾಗಿಲು"),
        "bedroom_door" to bi("Bedroom door", "ಮಲಗುವ ಕೋಣೆಯ ಬಾಗಿಲು"),
        "other_door" to bi("Other door", "ಇತರ ಬಾಗಿಲು"),
        "window" to bi("Window", "ಕಿಟಕಿ"),
    )

    val MOUNTING = mapOf("juttu" to "Juttu / ಜುಟ್ಟು", "screw" to bi("Screw", "ಸ್ಕ್ರೂ"))

    data class CutRow(val piece: String, val source: String, val lengthIn: Double, val widthIn: Double, val thicknessIn: Double, val qty: Int, val species: String, val cft: Double)
    data class Section(val group: String, val title: String, val rows: List<CutRow>, val pieces: Int, val cft: Double)
    data class Sheet(val job: String, val date: String, val totalPieces: Int, val totalCft: Double, val entries: List<String>, val sections: List<Section>)

    private fun size(s: DwStructure): String =
        "${s.width.ifBlank { "?" }}${unit(s.widthUnit)} × ${s.height.ifBlank { "?" }}${unit(s.heightUnit)}"
    private fun unit(u: String) = when (u.ifEmpty { "ft" }) { "ft" -> "ft"; "in" -> "in"; else -> " $u" }

    /** "Bedroom door / … 3ft × 7ft — Qty / ಸಂಖ್ಯೆ 4 (identical sets / …)" plus the user's own name when given. */
    fun entrySummary(s: DwStructure): String {
        val kind = KIND_LABELS[s.kind] ?: KIND_LABELS.getValue("other_door")
        val name = s.name.trim().takeIf { it.isNotEmpty() }?.let { "$it — " }.orEmpty()
        val q = QeDoorWindow.quantity(s)
        return "$name$kind ${size(s)} — $QTY_WORD $q ($IDENTICAL) · ${MOUNTING[if (s.mounting == "screw") "screw" else "juttu"]}"
    }

    fun build(ws: WorkspaceState, defaults: DwDefaults, date: String): Sheet {
        val list = ws.doorWindows.orEmpty()
        val rows = mutableMapOf<String, MutableList<CutRow>>()
        for (s in list) {
            val r = QeDoorWindow.compute(s, ws.dwWastage, defaults)
            val src = (s.name.trim().ifEmpty { QeDoorWindow.kindLabel(s.kind) }) + " " + size(s)
            for (p in r.pieces) rows.getOrPut(p.group) { mutableListOf() } +=
                CutRow(PIECE_LABELS[p.label] ?: p.label, src, p.cutIn, p.widthIn, p.thicknessIn, p.qty, s.species.trim(), p.cft)
        }
        val sections = QeDoorWindow.GROUP_ORDER.mapNotNull { g ->
            rows[g]?.let { rs -> Section(g, GROUP_TITLES.getValue(g), rs, rs.sumOf { it.qty }, rs.sumOf { it.cft }) }
        }
        return Sheet(
            job = ws.projectName.trim().ifEmpty { ws.customer.name.trim() }.ifEmpty { "—" },
            date = date,
            totalPieces = sections.sumOf { it.pieces },
            totalCft = sections.sumOf { it.cft },
            entries = list.map { entrySummary(it) },
            sections = sections,
        )
    }

    /** Every Kannada term used, for review by a fluent speaker (PLAN.md). */
    fun glossary(): List<String> = (listOf(TITLE, JOB, DATE, TOTAL_PIECES, TOTAL_CFT, ENTRIES, SECTION_TOTAL, PIECES, IDENTICAL, QTY_WORD, NONE) +
        COLUMNS + GROUP_TITLES.values + PIECE_LABELS.values + KIND_LABELS.values + MOUNTING.values).distinct()
}
