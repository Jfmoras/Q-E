package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Row
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values computed with the V10.1 app.js formulas. */
class QeMathTest {
    private fun row(l: String, w: String, t: String, q: String, mode: String = "CFT", lu: String = "ft", wu: String = "in", tu: String = "in") =
        Row(l, lu, w, wu, t, tu, q, mode)

    @Test fun numMatchesJsNumber() {
        assertEquals(0.0, QeMath.num(""), 0.0)
        assertEquals(0.0, QeMath.num("abc"), 0.0)
        assertEquals(12.5, QeMath.num(" 12.5 "), 0.0)
        assertEquals(16.0, QeMath.num("0x10"), 0.0)
        assertEquals(0.0, QeMath.num("5f"), 0.0)
        assertEquals(1000.0, QeMath.num("1e3"), 0.0)
    }

    @Test fun cftRow() {
        // 7 ft x 6 in x 2 in x 4 / 144 = 2.333…
        val x = QeMath.calcRow(row("7", "6", "2", "4"))
        assertEquals("2.333", QeFormat.toFixed(x.value, 3))
    }

    @Test fun kolRowDividesBy37_5() {
        val x = QeMath.calcRow(row("6", "4", "3", "2", "KOL"))
        assertEquals(6 * 4 * 3 * 2 / 37.5, x.value, 1e-12)
    }

    @Test fun unitConversions() {
        val x = QeMath.calcRow(row("2", "15.24", "50.8", "1", lu = "m", wu = "cm", tu = "mm"))
        assertEquals(2 * 3.280839895, x.lFt, 1e-12)
        assertEquals(6.0, x.wIn, 1e-9)
        assertEquals(2.0, x.tIn, 1e-9)
    }

    @Test fun itemTotalsSplitCftKol() {
        val t = QeMath.itemTotals(CalcItem("x", listOf(row("7", "6", "2", "4"), row("6", "4", "3", "2", "KOL"))))
        assertEquals("2.333", QeFormat.toFixed(t.cft, 3))
        assertEquals("3.840", QeFormat.toFixed(t.kol, 3))
    }

    @Test fun hoppus() {
        assertEquals("15.63", QeFormat.toFixed(QeMath.hoppusVolume("10", "60"), 2))
    }

    @Test fun formatting() {
        assertEquals("1.005", QeFormat.toFixed(1.0049999, 3))
        assertEquals("₹1,23,457", QeFormat.money(123456.7))
        assertEquals("Rs. 1,235", QeFormat.rs(1234.5))
    }
}
