package com.moraswood.qande

import com.moraswood.qande.data.ClientEntity
import com.moraswood.qande.data.JobMetaEntity
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.Quote
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeClients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientsTest {
    private fun ws(price: String, clientId: String? = null) = WorkspaceState(
        quoteItems = listOf(QuoteItem.blank().copy(description = "Door", qty = "2", unitPrice = price)),
        quote = Quote(gstPercent = "18"), clientId = clientId,
    )
    private fun row(id: String, root: String, rev: Int, uuid: String, s: WorkspaceState, pos: Int) =
        SavedProjectEntity(id, root, rev, "Job $root", "2026-11-2${rev}T00:00:00.000Z", QeJson.encodeToString(WorkspaceState.serializer(), s), pos, null, uuid)

    private val rao = ClientEntity("c1", "Mr Rao", "98450 12345", "Mysuru", "", "")
    private val shetty = ClientEntity("c2", "Shetty", "", "Udupi", "", "")

    @Test fun linkedJobsUseLatestRevisionAndJobsTableWins() {
        val saved = listOf(
            row("a1", "a", 1, "ua", ws("15000"), 0), row("a0", "a", 0, "ua", ws("10000"), 1),
            row("b0", "b", 0, "ub", ws("5000", clientId = "c1"), 2), // snapshot-only link
            row("c0", "c", 0, "uc", ws("7000", clientId = "c1"), 3), // jobs row says no client
        )
        val meta = listOf(JobMetaEntity("ua", "c1"), JobMetaEntity("uc", null))
        val s = QeClients.summaries(listOf(rao, shetty), saved, meta)
        val r = s.first { it.client.id == "c1" }
        assertEquals(listOf("a", "b"), r.jobs.map { it.rootId })
        assertEquals(1, r.jobs.first().revision)
        // 2×15000 + 18% GST = 35400; 2×5000 + 18% = 11800
        assertEquals(35400.0 + 11800.0, r.totalSpend, 1e-6)
        assertTrue(s.first { it.client.id == "c2" }.jobs.isEmpty())
    }

    @Test fun customerSearchAndValidation() {
        assertEquals("Mr Rao", QeClients.customerFor(rao).name)
        assertEquals("Mysuru", QeClients.customerFor(rao).location)
        assertTrue(QeClients.matches(rao, "9845012345"))
        assertTrue(QeClients.matches(rao, "mysu"))
        assertTrue(!QeClients.matches(shetty, "rao"))
        assertNotNull(QeClients.validate(rao.copy(name = " ")))
        assertNotNull(QeClients.validate(rao.copy(phone = "12")))
        assertNull(QeClients.validate(rao))
    }
}
