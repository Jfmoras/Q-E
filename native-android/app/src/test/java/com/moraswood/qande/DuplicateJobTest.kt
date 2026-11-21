package com.moraswood.qande

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Customer
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeDuplicate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class DuplicateJobTest {
    private val src = WorkspaceState(
        projectName = "Rao Villa", projectId = "abc1234", revision = 3, jobUuid = "11111111-1111-4111-8111-111111111111",
        items = listOf(CalcItem.blank().copy(name = "Door", photo = "item-a.jpg"), CalcItem.blank().copy(name = "Cot")),
        estimationItems = listOf(CalcItem.blank().copy(name = "Door", photo = "item-a.jpg")),
        customer = Customer(name = "Mr Rao"), deliveryDate = "2026-12-01",
    )

    @Test fun copyGetsNewIdentityAndR0() {
        val c = QeDuplicate.duplicate(src, "new0001", "22222222-2222-4222-8222-222222222222", mapOf("item-a.jpg" to "item-b.jpg"))
        assertEquals("Rao Villa (copy)", c.projectName)
        assertEquals("new0001", c.projectId)
        assertEquals(0, c.revision)
        assertEquals("22222222-2222-4222-8222-222222222222", c.jobUuid)
        assertEquals(null, c.deliveryDate)
        assertEquals(listOf("item-b.jpg", ""), c.items.map { it.photo })
        assertEquals(listOf("item-b.jpg"), c.estimationItems.map { it.photo })
        // everything else is identical, and the original is untouched
        assertEquals(src.customer, c.customer)
        assertEquals(src.items.map { it.rows }, c.items.map { it.rows })
        assertEquals("item-a.jpg", src.items[0].photo)
        assertNotSame(src, c)
    }

    @Test fun fallsBackToCustomerNameAndRoundTrips() {
        val c = QeDuplicate.duplicate(src.copy(projectName = ""), "n", "u", emptyMap())
        assertEquals("Mr Rao (copy)", c.projectName)
        assertEquals("item-a.jpg", c.items[0].photo) // missing source file → reference kept
        val back = QeJson.decodeFromString(WorkspaceState.serializer(), QeJson.encodeToString(WorkspaceState.serializer(), c))
        assertEquals(c, back)
        assertEquals(setOf("item-a.jpg"), QeDuplicate.photos(src))
    }
}
