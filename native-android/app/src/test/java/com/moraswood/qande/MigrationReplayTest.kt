package com.moraswood.qande

import com.moraswood.qande.data.QeMigrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Every migration from v3 on, replayed on the exported vN schema, must give exactly the exported vN+1 tables/columns/indices. */
class MigrationReplayTest {
    private fun schema(v: Int) = Json.parseToJsonElement(File("schemas/com.moraswood.qande.data.QeDatabase/$v.json").readText()).jsonObject["database"]!!.jsonObject

    private fun create(v: Int): Connection {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        c.createStatement().use { st ->
            schema(v)["entities"]!!.jsonArray.map { it.jsonObject }.forEach { e ->
                val t = e["tableName"]!!.jsonPrimitive.content
                st.execute(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
                (e["indices"]?.jsonArray ?: emptyList()).forEach { st.execute(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
            }
        }
        return c
    }

    private fun shape(c: Connection): Map<String, Any> {
        val out = sortedMapOf<String, Any>()
        val tables = c.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").let { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        for (t in tables) {
            val cols = c.createStatement().executeQuery("PRAGMA table_info(`$t`)").let { rs -> buildList { while (rs.next()) add(listOf(rs.getString("name"), rs.getString("type"), rs.getInt("notnull"), rs.getInt("pk"))) } }
            out["t:$t"] = cols.sortedBy { it[0] as String }
        }
        val idx = c.createStatement().executeQuery("SELECT name, tbl_name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'").let { rs -> buildList { while (rs.next()) add(rs.getString(1) + "@" + rs.getString(2)) } }
        out["indices"] = idx.sorted()
        return out
    }

    @Test fun everyStepMatchesNextExportedSchema() {
        val latest = File("schemas/com.moraswood.qande.data.QeDatabase").listFiles()!!.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.max()
        for (v in 3 until latest) {
            val sql = QeMigrations.STEP_SQL[v] ?: error("no SQL for $v → ${v + 1}")
            val migrated = create(v).also { c -> c.createStatement().use { st -> sql.forEach(st::execute) } }
            assertEquals("migration $v → ${v + 1}", shape(create(v + 1)), shape(migrated))
        }
    }
}
