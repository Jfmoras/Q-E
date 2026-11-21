package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.logic.QeStandalone
import org.junit.Assert.assertEquals
import org.junit.Test

class StandaloneCalcTest {
    private fun build(): List<CalcItem> {
        var s = QeStandalone.initial()
        s = QeStandalone.setName(s, 0, "Cot")
        s = QeStandalone.setRowField(s, 0, 0, "l", "7"); s = QeStandalone.setRowField(s, 0, 0, "w", "4")
        s = QeStandalone.setRowField(s, 0, 0, "t", "3"); s = QeStandalone.setRowField(s, 0, 0, "q", "4")
        s = QeStandalone.addRow(s, 0)
        s = QeStandalone.setRowField(s, 0, 1, "l", "2100"); s = QeStandalone.setRowField(s, 0, 1, "lUnit", "mm")
        s = QeStandalone.setRowField(s, 0, 1, "w", "6"); s = QeStandalone.setRowField(s, 0, 1, "t", "1"); s = QeStandalone.setRowField(s, 0, 1, "q", "2")
        s = QeStandalone.setRowField(s, 0, 1, "mode", "KOL")
        s = QeStandalone.addItem(s)
        s = QeStandalone.setRowField(s, 1, 0, "l", "5"); s = QeStandalone.setRowField(s, 1, 0, "w", "3"); s = QeStandalone.setRowField(s, 1, 0, "t", "2")
        s = QeStandalone.setRowField(s, 1, 0, "q", "3")
        return s
    }

    @Test fun totalsMatchQeMathExactly() {
        val s = build()
        assertEquals(2, s.size); assertEquals(2, s[0].rows.size)
        val t = QeStandalone.totals(s)
        val q = QeMath.grandTotals(s)
        assertEquals(q, t)
        val byRow = s.flatMap { it.rows }.map { QeMath.calcRow(it) }
        assertEquals(byRow.filter { it.mode == "CFT" }.sumOf { it.value }, t.cft, 1e-9)
        assertEquals(byRow.filter { it.mode == "KOL" }.sumOf { it.value }, t.kol, 1e-9)
        assertEquals(t.cft, QeMath.itemTotals(s[0]).cft + QeMath.itemTotals(s[1]).cft, 1e-9)
    }

    @Test fun initialIsOneBlankItem() {
        val s = QeStandalone.initial()
        assertEquals(1, s.size); assertEquals(1, s[0].rows.size)
        assertEquals(QeMath.Totals(0.0, 0.0), QeStandalone.totals(s))
    }
}
