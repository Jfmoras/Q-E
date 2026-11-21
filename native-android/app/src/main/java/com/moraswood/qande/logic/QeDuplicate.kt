package com.moraswood.qande.logic

import com.moraswood.qande.data.WorkspaceState

/**
 * Feature N: duplicate a whole job. Pure part — the snapshot is deep-copied (it is immutable data, re-serialised on save),
 * gets a fresh project id + job UUID (so a new QR), revision R0 and "<name> (copy)"; item photo file names are remapped
 * to the freshly copied files so the copy never shares a file with the original.
 */
object QeDuplicate {
    const val SUFFIX = " (copy)"

    fun copyName(name: String): String = name.trim() + SUFFIX

    /** Photo files referenced by the snapshot (calculator + estimation items). */
    fun photos(s: WorkspaceState): Set<String> = QeRevisions.photoRefs(s)

    fun duplicate(s: WorkspaceState, newProjectId: String, newJobUuid: String, photoMap: Map<String, String>): WorkspaceState {
        fun p(name: String) = if (name.isEmpty()) name else photoMap[name] ?: name
        val base = s.projectName.ifEmpty { s.customer.name }
        return s.copy(
            projectName = copyName(base),
            projectId = newProjectId,
            revision = 0,
            jobUuid = newJobUuid,
            deliveryDate = null, // a copy is a new job: not scheduled yet (Feature P)
            signatureId = null, // ...and not signed by the client yet (Feature Q)
            items = s.items.map { it.copy(photo = p(it.photo)) },
            estimationItems = s.estimationItems.map { it.copy(photo = p(it.photo)) },
        )
    }
}
