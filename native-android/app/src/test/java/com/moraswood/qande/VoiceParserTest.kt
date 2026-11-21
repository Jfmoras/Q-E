package com.moraswood.qande

import com.moraswood.qande.logic.QeVoice
import com.moraswood.qande.logic.QeVoice.Measure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private fun m(s: String) = QeVoice.parse(s).measures

    @Test fun byAndWordNumbers() {
        assertEquals(listOf(Measure(7.0, null), Measure(4.0, null)), m("seven by four"))
        assertEquals(listOf(Measure(11.0, null), Measure(3.0, null)), m("Eleven by three"))
        assertEquals(listOf(Measure(7.0, null), Measure(4.0, null)), m("7 x 4"))
        assertEquals(listOf(Measure(25.0, null), Measure(120.0, null)), m("twenty five by one hundred twenty"))
    }

    @Test fun feetInchesAndFractions() {
        assertEquals(listOf(Measure(88.0, "in")), m("7 feet 4 inches"))
        assertEquals(listOf(Measure(88.0, "in")), m("seven feet four"))
        assertEquals(listOf(Measure(4.5, null)), m("four and a half"))
        assertEquals(listOf(Measure(2.75, "in")), m("two and three quarters inches"))
        assertEquals(listOf(Measure(7.5, "ft")), m("7 point 5 feet"))
        assertEquals(listOf(Measure(7.0, "ft"), Measure(4.5, "in")), m("7 ft by 4½ in"))
        assertEquals(listOf(Measure(2100.0, "mm")), m("2100 millimetres"))
    }

    @Test fun quantity() {
        val p = QeVoice.parse("seven by four by two quantity 4")
        assertEquals(listOf(7.0, 4.0, 2.0), p.measures.map { it.value })
        assertEquals(4.0, p.qty!!, 0.0)
        assertEquals(6.0, QeVoice.parse("6 pieces").qty!!, 0.0)
        assertTrue(QeVoice.parse("6 pieces").measures.isEmpty())
    }

    @Test fun assignFillsConsecutiveFieldsAndUnits() {
        assertEquals(listOf("l" to "7", "w" to "4"), QeVoice.assign("l", QeVoice.parse("seven by four")))
        assertEquals(listOf("w" to "11", "t" to "3"), QeVoice.assign("w", QeVoice.parse("eleven by three")))
        assertEquals(listOf("l" to "88", "lUnit" to "in", "q" to "4"), QeVoice.assign("l", QeVoice.parse("7 feet 4 inches quantity 4")))
        assertEquals(listOf("q" to "4"), QeVoice.assign("q", QeVoice.parse("four")))
        // overflow past thickness goes to pieces
        assertEquals(listOf("t" to "2", "q" to "3"), QeVoice.assign("t", QeVoice.parse("2 by 3")))
    }

    @Test fun gracefulOnNonsense() {
        assertTrue(QeVoice.parse("hello there").isEmpty)
        assertTrue(QeVoice.parse("").isEmpty)
        assertNull(QeVoice.parse("by by").qty)
        assertTrue(QeVoice.assign("l", QeVoice.parse("nothing")).isEmpty())
    }
}
