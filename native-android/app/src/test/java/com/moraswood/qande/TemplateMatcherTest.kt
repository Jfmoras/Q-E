package com.moraswood.qande

import com.moraswood.qande.data.Row
import com.moraswood.qande.templates.TemplateMatcher
import com.moraswood.qande.templates.TemplateMatcher.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateMatcherTest {
    private fun r(l: String, w: String, t: String, q: String, mode: String = "CFT", lu: String = "ft") = Row(l, lu, w, "in", t, "in", q, mode)
    private fun job(key: String, type: String, vararg rows: Row) =
        Job(key, TemplateMatcher.normalizeType(type), rows.map { TemplateMatcher.componentOf(it) })

    private val bedA = job("a", "King Bed", r("7", "6", "2", "2"), r("6.5", "4", "1", "12"), r("2", "3", "3", "4"))
    private val bedB = job("b", "king beds", r("7", "6", "2", "2"), r("6.5", "4", "1", "14"), r("2", "3", "3", "4"))
    private val bedC = job("c", "king-bed", r("7.2", "6", "2", "2"), r("6.5", "4", "1", "12"), r("2.2", "3", "3", "4"))
    private val table = job("t", "King Bed", r("3", "30", "1", "1"))

    @Test fun normalizeType() {
        assertEquals("king bed", TemplateMatcher.normalizeType("  King-Beds "))
        assertEquals("dressing table", TemplateMatcher.normalizeType("Dressing  Tables"))
        assertEquals("shelf unit", TemplateMatcher.normalizeType("shelf units"))
        assertEquals("library", TemplateMatcher.normalizeType("Libraries"))
    }

    @Test fun typeSimilarityIsFuzzy() {
        assertEquals(1.0, TemplateMatcher.typeSimilarity("bed king", "king bed"), 0.0)
        assertTrue(TemplateMatcher.typeSimilarity("king bed", "kin bed") >= TemplateMatcher.TYPE_THRESHOLD)
        assertTrue(TemplateMatcher.typeSimilarity("king bed", "sofa") < TemplateMatcher.TYPE_THRESHOLD)
    }

    @Test fun identicalJobsScoreOne() {
        assertEquals(1.0, TemplateMatcher.jobSimilarity(bedA.components, bedA.components), 1e-9)
    }

    @Test fun differentRowCountLowersScore() {
        val two = bedA.components.take(2)
        assertEquals(2.0 / 3.0, TemplateMatcher.jobSimilarity(bedA.components, two), 1e-9)
    }

    @Test fun commonUnitsMakeCmAndFeetEqual() {
        val ft = TemplateMatcher.componentOf(r("6", "4", "1", "2"))
        val m = TemplateMatcher.componentOf(r("1.8288", "4", "1", "2", lu = "m"))
        assertEquals(1.0, TemplateMatcher.componentScore(ft, m), 1e-6)
    }

    @Test fun modeMismatchPenalised() {
        val a = TemplateMatcher.componentOf(r("6", "4", "1", "2"))
        val b = TemplateMatcher.componentOf(r("6", "4", "1", "2", "KOL"))
        assertEquals(0.7, TemplateMatcher.componentScore(a, b), 1e-9)
    }

    @Test fun needsTwoMatchingJobs() {
        assertNull(TemplateMatcher.suggest("king bed", listOf(bedA)))
        assertNull(TemplateMatcher.suggest("king bed", listOf(bedA, table)))
        assertNull(TemplateMatcher.suggest("sofa", listOf(bedA, bedB)))
        assertNull(TemplateMatcher.suggest("", listOf(bedA, bedB)))
    }

    @Test fun clusterGivesMedianRows() {
        val s = TemplateMatcher.suggest("King Bed", listOf(bedA, bedB, bedC, table))
        assertNotNull(s)
        s!!
        assertEquals(3, s.jobCount)
        assertEquals(3, s.rows.size)
        assertEquals("7", s.rows[0].l)
        assertEquals("12", s.rows[1].q)
        assertEquals("2", s.rows[2].l)
        assertEquals("ft", s.rows[0].lUnit)
    }

    @Test fun pluralForBanner() {
        assertEquals("king beds", TemplateMatcher.plural("king bed"))
        assertEquals("benches", TemplateMatcher.plural("bench"))
        assertEquals("libraries", TemplateMatcher.plural("library"))
    }
}
