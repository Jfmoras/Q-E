package com.moraswood.qande

import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.WorkspaceState
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsJsonTest {
    @Test fun decodesV101AutosaveWithoutNativeFields() {
        val json = """{"tab":"calculator","items":[{"name":"Bed","rows":[{"l":7,"lUnit":"ft","w":"6","wUnit":"in","t":"2","tUnit":"in","q":"4","mode":"CFT"}]}],"unknown":1}"""
        val s = QeJson.decodeFromString(WorkspaceState.serializer(), json)
        assertEquals("Bed", s.items[0].name)
        assertEquals("7", s.items[0].rows[0].l)
        assertEquals("", s.items[0].photo)
        assertEquals("", s.items[0].tag)
    }
}
