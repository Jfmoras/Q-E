package com.moraswood.qande

import com.moraswood.qande.data.DwDefaults
import com.moraswood.qande.data.DwStructure
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeCutList
import com.moraswood.qande.logic.QeDoorWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutListTest {
    private val kannada = Regex("[\\u0C80-\\u0CFF]")

    @Test fun groupsTotalsAndEntrySummary() {
        val ws = WorkspaceState(projectName = "Villa", doorWindows = listOf(
            DwStructure(id = "a", name = "", kind = "bedroom_door", width = "3", height = "7", quantity = "4", species = "Teak", planking = true),
            DwStructure(id = "b", kind = "window", width = "3", height = "4", architrave = true, archW = "3", archT = "1"),
        ))
        val sheet = QeCutList.build(ws, DwDefaults(), "21-11-2026")
        assertEquals(listOf(QeDoorWindow.GROUP_FRAME, QeDoorWindow.GROUP_PLANK, QeDoorWindow.GROUP_ARCH, QeDoorWindow.GROUP_WINDOW), sheet.sections.map { it.group })
        val expected = ws.doorWindows!!.sumOf { QeDoorWindow.compute(it, null, DwDefaults()).totalPieces }
        assertEquals(expected, sheet.totalPieces)
        assertEquals(sheet.sections.sumOf { it.rows.sumOf { r -> r.qty } }, sheet.totalPieces)
        assertTrue(sheet.entries[0].startsWith("Bedroom door / ಮಲಗುವ ಕೋಣೆಯ ಬಾಗಿಲು 3ft × 7ft — Qty / ಸಂಖ್ಯೆ 4 (identical sets"))
        assertEquals("Frame Support / ಚೌಕಟ್ಟು ಬೆಂಬಲ", sheet.sections[0].title)
        assertTrue(sheet.sections[0].rows.all { it.species == "Teak" || it.source.startsWith("Window") })
    }

    @Test fun everyLabelBilingualAndTermsRight() {
        val g = QeCutList.glossary()
        g.filter { it != "CFT" }.forEach { assertTrue(it, kannada.containsMatchIn(it) && it.contains(" / ")) }
        assertTrue(g.contains("Juttu / ಜುಟ್ಟು"))
        assertEquals("Architrave / ವೊಟ್ಟುಪಟ್ಟಿ", QeCutList.GROUP_TITLES[QeDoorWindow.GROUP_ARCH])
        g.forEach { assertFalse(it, it.contains("ಉಥುಪಟ್ಟಿ") || it.contains("uthupatti", ignoreCase = true)) }
        assertTrue(QeCutList.GROUP_TITLES[QeDoorWindow.GROUP_FRAME]!!.contains("ಚೌಕಟ್ಟು"))
        // every geometry piece label has a bilingual cut-list label
        val labels = QeDoorWindow.compute(DwStructure(kind = "window", height = "4", width = "3", architrave = true, archW = "3", archT = "1", archBottom = true), null, DwDefaults()).pieces.map { it.label } +
            QeDoorWindow.compute(DwStructure(height = "7", width = "4", planking = true, plankWidth = "8"), null, DwDefaults()).pieces.map { it.label }
        labels.forEach { assertTrue(it, QeCutList.PIECE_LABELS.containsKey(it)) }
    }
}
