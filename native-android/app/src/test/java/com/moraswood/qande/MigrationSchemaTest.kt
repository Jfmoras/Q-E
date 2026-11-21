package com.moraswood.qande

import com.moraswood.qande.data.QeMigrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** MIGRATION_1_2 must create exactly what Room expects in the exported v2 schema (no destructive wipe). */
class MigrationSchemaTest {
    private fun schema(v: Int) = Json.parseToJsonElement(File("schemas/com.moraswood.qande.data.QeDatabase/$v.json").readText()).jsonObject["database"]!!.jsonObject

    @Test fun migration1to2MatchesExportedSchema() {
        val v1 = schema(1)["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()
        val expected = schema(2)["entities"]!!.jsonArray.map { it.jsonObject }.filter { it["tableName"]!!.jsonPrimitive.content !in v1 }.flatMap { e ->
            val table = e["tableName"]!!.jsonPrimitive.content
            listOf(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) +
                (e["indices"]?.jsonArray ?: emptyList()).map { it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table) }
        }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected.toSet(), QeMigrations.MIGRATION_1_2_SQL.toSet())
        assertTrue(QeMigrations.MIGRATION_1_2_SQL.none { it.contains("DROP", ignoreCase = true) })
    }
}
