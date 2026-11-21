package com.moraswood.qande

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.moraswood.qande.data.QeMigrations
import com.moraswood.qande.logic.QeQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

class JobQrTest {
    @Test fun payloadAndParse() {
        val u = "3F2504E0-4F89-41D3-9A0C-0305E82C3301"
        assertEquals("qe://job/3f2504e0-4f89-41d3-9a0c-0305e82c3301", QeQr.payload(u))
        assertEquals(u.lowercase(), QeQr.parse(QeQr.payload(u)))
        assertEquals(u.lowercase(), QeQr.parse(u))
        assertNull(QeQr.parse("https://example.com"))
        assertNull(QeQr.parse("qe://job/not-a-uuid"))
        assertNull(QeQr.parse(null))
    }

    @Test fun generatedQrDecodesBack() {
        val text = QeQr.payload(java.util.UUID.randomUUID().toString())
        val m = QeQr.matrix(text)
        val scale = 4
        val n = m.size * scale
        val px = IntArray(n * n) { i -> if (m[(i / n) / scale][(i % n) / scale]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(n, n, px)))).text
        assertEquals(text, decoded)
    }

    @Test fun backfillGivesOneUuidPerJobAndKeepsRevisionsReadOnly() {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        c.createStatement().use { st ->
            st.execute("CREATE TABLE saved_projects (id TEXT NOT NULL PRIMARY KEY, rootId TEXT NOT NULL, revision INTEGER NOT NULL, jobUuid TEXT NOT NULL DEFAULT '')")
            st.execute("INSERT INTO saved_projects (id, rootId, revision) VALUES ('a','a',0),('a1','a',1),('a2','a',2),('b','b',0),('c1','c',1)")
            (QeMigrations.JOB_UUID_BACKFILL + QeMigrations.REVISION_TRIGGERS).forEach(st::execute)
            val rows = st.executeQuery("SELECT rootId, jobUuid FROM saved_projects").let { rs -> buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) } }
            val byRoot = rows.groupBy({ it.first }, { it.second })
            byRoot.values.forEach { assertEquals(1, it.distinct().size); assertTrue(QeQr.parse(it[0]) != null) }
            assertNotEquals(byRoot["a"]!![0], byRoot["b"]!![0])
            // idempotent: running again changes nothing
            (QeMigrations.JOB_UUID_BACKFILL + QeMigrations.REVISION_TRIGGERS).forEach(st::execute)
            val again = st.executeQuery("SELECT rootId, jobUuid FROM saved_projects").let { rs -> buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) } }
            assertEquals(rows.toSet(), again.toSet())
            // a new revision without uuid joins its job's uuid
            st.execute("INSERT INTO saved_projects (id, rootId, revision) VALUES ('b1','b',1)")
            (QeMigrations.JOB_UUID_BACKFILL + QeMigrations.REVISION_TRIGGERS).forEach(st::execute)
            val b1 = st.executeQuery("SELECT jobUuid FROM saved_projects WHERE id='b1'").let { it.next(); it.getString(1) }
            assertEquals(byRoot["b"]!![0], b1)
            val blocked = runCatching { st.execute("UPDATE saved_projects SET revision = 9 WHERE id = 'a'") }.isFailure
            assertTrue("trigger re-created", blocked)
        }
    }
}
