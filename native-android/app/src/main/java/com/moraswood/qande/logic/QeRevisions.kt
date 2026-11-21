package com.moraswood.qande.logic

import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.WorkspaceState

/**
 * Feature E — revisions.
 *
 * V10.1 (app.js saveProject / renderSavedProjects): a job has a rootId; each saved record is `{id, rootId, revision, ...}`.
 * Save Project overwrote the record with the same (rootId, revision); "Revise" loaded a revision as R(latest+1);
 * "View / Print" loaded a revision read-only (Save refused); the print header reads "QUOTATION · R{n}"; the list showed
 * one row per revision "Name · R{n}".
 *
 * Native: the first save is R0 and every later save appends R(latest+1) — nothing is overwritten (a SQLite trigger
 * refuses UPDATE on saved revisions). The list groups revisions per job; opening a job loads the latest revision
 * (editable); older revisions open read-only.
 */
object QeRevisions {
    data class Job(val rootId: String, val name: String, val revisions: List<SavedProjectEntity>) {
        val latest: SavedProjectEntity get() = revisions.first()
    }

    fun root(x: SavedProjectEntity): String = x.rootId.ifEmpty { x.id }

    /** Jobs in list order (newest activity first), revisions newest first. */
    fun jobs(list: List<SavedProjectEntity>): List<Job> {
        val groups = LinkedHashMap<String, MutableList<SavedProjectEntity>>()
        list.forEach { groups.getOrPut(root(it)) { mutableListOf() }.add(it) }
        return groups.map { (r, xs) ->
            val revs = xs.sortedWith(compareByDescending<SavedProjectEntity> { it.revision }.thenByDescending { it.updatedAt })
            Job(r, revs.first().name, revs)
        }
    }

    fun label(name: String, revision: Int): String = "$name · R$revision"

    fun latestRevision(list: List<SavedProjectEntity>, rootId: String): Int? = list.filter { root(it) == rootId }.maxOfOrNull { it.revision }

    /** Revision number the next Save Project writes: the workspace number for a job's first save, otherwise latest + 1. */
    fun nextRevision(list: List<SavedProjectEntity>, rootId: String, workspaceRevision: Int): Int =
        latestRevision(list, rootId)?.let { maxOf(it + 1, workspaceRevision) } ?: workspaceRevision

    /** Only the latest revision of a job may be edited and saved onward. */
    fun isLatest(list: List<SavedProjectEntity>, x: SavedProjectEntity): Boolean = latestRevision(list, root(x)) == x.revision

    /** Item photo files referenced by a snapshot (photos are shared by path between revisions, never copied). */
    fun photoRefs(snapshotJson: String): Set<String> = runCatching {
        val s = QeJson.decodeFromString(WorkspaceState.serializer(), snapshotJson)
        photoRefs(s)
    }.getOrDefault(emptySet())

    fun photoRefs(s: WorkspaceState): Set<String> = (s.items + s.estimationItems).map { it.photo }.filter { it.isNotEmpty() }.toSet()

    /** Photos of a deleted revision that nothing else (other revisions, the workspace, assets) still uses. */
    fun orphanedPhotos(deleted: SavedProjectEntity, remaining: List<SavedProjectEntity>, workspace: WorkspaceState, otherRefs: Set<String>): Set<String> {
        val mine = photoRefs(deleted.snapshotJson)
        if (mine.isEmpty()) return emptySet()
        val used = remaining.filter { it.id != deleted.id }.flatMap { photoRefs(it.snapshotJson) }.toSet() + photoRefs(workspace) + otherRefs
        return mine - used
    }
}
