package com.moraswood.qande.logic

import com.moraswood.qande.data.ClientEntity
import com.moraswood.qande.data.Customer
import com.moraswood.qande.data.JobMetaEntity
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.WorkspaceState

/** Feature O — client database: linked past jobs and total spend. */
object QeClients {
    data class LinkedJob(val rootId: String, val name: String, val revision: Int, val jobUuid: String, val total: Double, val updatedAt: String)
    data class Summary(val client: ClientEntity, val jobs: List<LinkedJob>) {
        /** Sum of the quotation grand totals (latest revision of each linked job). */
        val totalSpend: Double get() = jobs.sumOf { it.total }
    }

    fun decode(x: SavedProjectEntity): WorkspaceState? = runCatching { QeJson.decodeFromString(WorkspaceState.serializer(), x.snapshotJson) }.getOrNull()

    /**
     * Client of each job (rootId → clientId). The `jobs` row wins (it can also say "no client");
     * without one, the client id stored in the latest snapshot is used (e.g. a job saved before linking existed).
     */
    fun jobLinks(saved: List<SavedProjectEntity>, meta: List<JobMetaEntity>): List<Pair<LinkedJob, String?>> {
        val byUuid = meta.associateBy { it.jobUuid }
        return QeRevisions.jobs(saved).map { job ->
            val x = job.latest
            val uuid = job.revisions.firstOrNull { it.jobUuid.isNotBlank() }?.jobUuid.orEmpty()
            val snap = decode(x)
            val cid = byUuid[uuid]?.let { it.clientId } ?: if (uuid.isNotEmpty() && byUuid.containsKey(uuid)) null else snap?.clientId
            val total = snap?.let { QeQuote.quoteTotals(it).grand } ?: 0.0
            LinkedJob(job.rootId, x.name, x.revision, uuid, total, x.updatedAt) to cid
        }
    }

    fun summaries(clients: List<ClientEntity>, saved: List<SavedProjectEntity>, meta: List<JobMetaEntity>): List<Summary> {
        val links = jobLinks(saved, meta)
        return clients.map { c -> Summary(c, links.filter { it.second == c.id }.map { it.first }) }
    }

    /** Quotation customer block filled from a client (V10.1 has name + location). */
    fun customerFor(c: ClientEntity): Customer = Customer(name = c.name, location = c.address)

    fun matches(c: ClientEntity, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(c.name, c.phone, c.address, c.notes).any { it.lowercase().contains(q) } ||
            (q.any { it.isDigit() } && c.phone.filter { it.isDigit() }.contains(q.filter { it.isDigit() }))
    }

    fun validate(c: ClientEntity): String? = when {
        c.name.isBlank() -> "Enter the client's name."
        c.phone.isNotBlank() && c.phone.count { it.isDigit() } < 6 -> "Check the phone number."
        else -> null
    }
}
