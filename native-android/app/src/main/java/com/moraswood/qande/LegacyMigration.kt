package com.moraswood.qande

import org.json.JSONObject
import java.util.UUID

/** Imports project metadata from the old app's JSON export without deleting native records.
 *  Does not import IndexedDB attachments or quotation images; those need a separate migration.
 */
class LegacyMigration(private val dao: MwiDao) {
    suspend fun importProjects(json: String): Int {
        val root = JSONObject(json)
        require(root.optString("format") == "moras-qe-backup") { "Not an MWI JSON backup" }
        val storage = root.getJSONObject("storage")
        val operationsText = storage.optString("mwi-operations-v1", "")
        if (operationsText.isBlank()) return 0
        val projects = JSONObject(operationsText).optJSONArray("projects") ?: return 0
        var imported = 0
        for (i in 0 until projects.length()) {
            val old = projects.getJSONObject(i)
            val id = old.optString("id").ifBlank { UUID.randomUUID().toString() }
            dao.putProject(ProjectEntity(id, old.optString("name"), old.optString("customer"), old.optDouble("original", 0.0), old.optString("status", "Saved"), old.optString("notes")))
            imported++
        }
        return imported
    }
}
