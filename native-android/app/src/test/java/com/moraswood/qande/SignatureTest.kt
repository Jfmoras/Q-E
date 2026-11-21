package com.moraswood.qande

import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeDuplicate
import com.moraswood.qande.logic.QeSignature
import com.moraswood.qande.logic.QeSignature.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {
    @Test fun tapIsNotASignature() {
        assertFalse(QeSignature.isSigned(emptyList()))
        assertFalse(QeSignature.isSigned(listOf(listOf(P(10f, 10f)))))
        assertFalse(QeSignature.isSigned(listOf(listOf(P(0f, 0f), P(3f, 4f))))) // 5 px
        assertTrue(QeSignature.isSigned(listOf(listOf(P(0f, 0f), P(30f, 40f)), listOf(P(0f, 0f), P(0f, 10f))))) // 50 + 10
        assertEquals(60f, QeSignature.inkLength(listOf(listOf(P(0f, 0f), P(30f, 40f)), listOf(P(0f, 0f), P(0f, 10f)))), 1e-4f)
    }

    @Test fun fitCentresInkWithMargin() {
        val b = QeSignature.bounds(listOf(listOf(P(100f, 50f), P(300f, 150f))))!!
        val f = QeSignature.fit(b, 900, 300, 16f)
        // height-limited: (300-32)/100 = 2.68
        assertEquals(2.68f, f.scale, 1e-4f)
        assertEquals(16f, 50f * f.scale + f.dy, 1e-3f)
        assertEquals(284f, 150f * f.scale + f.dy, 1e-3f)
        val cx = (100f * f.scale + f.dx + 300f * f.scale + f.dx) / 2
        assertEquals(450f, cx, 1e-3f)
    }

    @Test fun flatLineNeverDividesByZero() {
        val b = QeSignature.bounds(listOf(listOf(P(10f, 20f), P(110f, 20f))))!!
        val f = QeSignature.fit(b)
        assertTrue(f.scale.isFinite() && f.dx.isFinite() && f.dy.isFinite())
        assertNull(QeSignature.bounds(emptyList()))
    }

    @Test fun captionAndDuplicate() {
        assertEquals("Ravi · 2026-11-21", QeSignature.caption(" Ravi ", "2026-11-21T09:30:00.000Z"))
        assertEquals("2026-11-21", QeSignature.caption("", "2026-11-21T09:30:00.000Z"))
        val copy = QeDuplicate.duplicate(WorkspaceState(signatureId = "sabc"), "p2", "u2", emptyMap())
        assertNull(copy.signatureId)
    }
}
