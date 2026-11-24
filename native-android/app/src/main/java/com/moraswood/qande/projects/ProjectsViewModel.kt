package com.moraswood.qande.projects

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.moraswood.qande.AppViewModel
import com.moraswood.qande.data.OpsAttachmentEntity
import com.moraswood.qande.data.OpsProjectEntity
import com.moraswood.qande.data.OpsProjectEntryEntity
import com.moraswood.qande.data.PpCommentEntity
import com.moraswood.qande.data.PpHardwareEntity
import com.moraswood.qande.data.PpItemEntity
import com.moraswood.qande.data.PpProjectEntity
import com.moraswood.qande.data.PpRevisionEntity
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.OpsEntry
import com.moraswood.qande.logic.OpsProject
import com.moraswood.qande.logic.OpsProjectForm
import com.moraswood.qande.logic.QeOps
import com.moraswood.qande.logic.QeProjects
import com.moraswood.qande.logic.QeRevisions
import com.moraswood.qande.ops.Attachments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One dashboard project: the V10.1 operations project + the dashboard's own rows. */
data class PpView(
    val project: OpsProject,
    val meta: PpProjectEntity,
    val items: List<PpItemEntity>,
    val hardware: List<PpHardwareEntity>,
    val comments: List<PpCommentEntity>,
    val revisions: List<PpRevisionEntity> = emptyList(),
    /** Payment rows (with their row ids, for delete), oldest first. */
    val paymentRows: List<OpsProjectEntryEntity> = emptyList(),
) {
    val revision get() = meta.revision
    val revisionLabel get() = QeProjects.label(meta.revision)
    val status get() = QeProjects.status(meta.status)
    val money get() = QeProjects.money(project)
    val startDate get() = meta.startDate.ifBlank { project.created.take(10) }
}

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    companion object { const val NOT_SAVED = "Save the project first (Save Project), then Confirm as Project." }
    private val db = QeDatabase.get(app)
    private val ops = db.operations()
    private val pp = db.projects()
    private val ctx get() = getApplication<Application>()
    private fun now() = AppViewModel.jsIsoNow()

    private val opsProjects = combine(ops.observeProjects(), ops.observeEntries()) { ps, es ->
        val by = es.groupBy { it.projectId }
        fun list(id: String, k: String) = by[id].orEmpty().filter { it.kind == k }.map { OpsEntry(it.amount, it.note, it.at) }
        ps.map { p -> OpsProject(p.id, p.name, p.customer, p.original, p.status, list(p.id, "payment"), list(p.id, "revision"), list(p.id, "expense"), p.notes, p.created) } to
            es.filter { it.kind == "payment" }.sortedWith(compareBy({ it.at }, { it.rowId })).groupBy { it.projectId }
    }
    private val extras = combine(pp.observeMeta(), pp.observeItems(), pp.observeHardware(), pp.observeComments(), pp.observeRevisions()) { m, i, h, c, r -> Extras(m, i, h, c, r) }
    private data class Extras(val meta: List<PpProjectEntity>, val items: List<PpItemEntity>, val hardware: List<PpHardwareEntity>, val comments: List<PpCommentEntity>, val revisions: List<PpRevisionEntity>)

    val projects: StateFlow<List<PpView>> = combine(opsProjects, extras) { (ps, pay), x ->
        val meta = x.meta.associateBy { it.projectId }
        val items = x.items.groupBy { it.projectId }; val hw = x.hardware.groupBy { it.projectId }; val cm = x.comments.groupBy { it.projectId }
        val rv = x.revisions.groupBy { it.projectId }
        ps.map { p -> PpView(p, meta[p.id] ?: PpProjectEntity(p.id), items[p.id].orEmpty(), hw[p.id].orEmpty(), cm[p.id].orEmpty(), rv[p.id].orEmpty(), pay[p.id].orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()
    fun msg(s: String) { _message.value = s }

    private suspend fun meta(id: String) = pp.meta(id) ?: PpProjectEntity(id)
    private fun editMeta(id: String, change: (PpProjectEntity) -> PpProjectEntity) {
        viewModelScope.launch { pp.putMeta(change(meta(id)).copy(updatedAt = now())) }
    }

    /** Bottom-bar "+": a new project (same validation as V10.1 Create project). Calls [done] with the new id. */
    fun create(customer: String, name: String, description: String, value: String, startDate: String, done: (String) -> Unit) {
        viewModelScope.launch {
            val id = QeOps.uid(); val at = now()
            val all = projects.value.map { it.project }
            val out = QeOps.createProject(com.moraswood.qande.logic.OpsDb(projects = all), OpsProjectForm(customer, name, value, "", ""), id, at)
            val p = out.project ?: return@launch msg(out.message)
            ops.putProject(OpsProjectEntity(p.id, p.name, p.customer, p.original, "Running", p.notes, p.created, all.size))
            pp.putMeta(PpProjectEntity(p.id, description = description.trim(), startDate = startDate.ifBlank { at.take(10) }, updatedAt = at))
            msg("Project created."); done(p.id)
        }
    }

    fun setStatus(id: String, status: String) = editMeta(id) { it.copy(status = QeProjects.status(status)) }
    fun setStartDate(id: String, date: String) = editMeta(id) { it.copy(startDate = date) }
    fun setDescription(id: String, text: String) = editMeta(id) { it.copy(description = text) }
    fun setNotes(id: String, notes: String) {
        viewModelScope.launch { ops.projects().firstOrNull { it.id == id }?.let { ops.putProject(it.copy(notes = notes)) } }
    }

    /** Payments tab: amount + manually entered date (yyyy-MM-dd). */
    fun addPayment(id: String, amount: String, date: String, note: String): Boolean {
        val a = QeProjects.validAmount(amount) ?: run { msg("Enter a positive amount."); return false }
        val at = QeProjects.paymentAt(date) ?: run { msg("Enter the payment date as YYYY-MM-DD."); return false }
        viewModelScope.launch { ops.addEntry(OpsProjectEntryEntity(projectId = id, kind = "payment", amount = a, note = note.trim(), at = at)); msg("Payment recorded.") }
        return true
    }

    /** Payments tab › Delete (after confirm). Remaining amount and the summary card follow through the flows. */
    fun deletePayment(rowId: Long) {
        viewModelScope.launch { ops.deleteEntry(rowId); msg("Payment deleted.") }
    }

    /** Start date "Reset": back to the project's creation date. */
    fun clearStartDate(id: String) = editMeta(id) { it.copy(startDate = "") }

    /** Current-revision items: add / edit / delete (older revisions are frozen snapshots). */
    fun saveItem(i: PpItemEntity) {
        if (i.name.isBlank()) return msg("Enter the item name.")
        viewModelScope.launch { pp.putItems(listOf(if (i.id.isEmpty()) i.copy(id = "i" + QeOps.uid()) else i)) }
    }
    fun deleteItem(id: String) { viewModelScope.launch { pp.deleteItem(id) } }

    /**
     * "Revise project": freezes the current state as the current R (read-only snapshot), then starts the next R with
     * [newTotal] as the project value (V10.1 revision entry of the difference, so every total follows the latest revision).
     */
    fun revise(v: PpView, newTotal: String, reason: String): Boolean {
        val r = QeProjects.revise(v.revision, v.money.total, newTotal, reason) ?: run { msg("Enter the revised total project value."); return false }
        viewModelScope.launch {
            val at = now(); val id = v.project.id
            val snap = QeProjects.Snapshot(
                v.revision, v.money.total, v.money.paid, v.meta.description, v.project.notes, v.startDate,
                v.items.map { QeProjects.SnapItem(it.name, it.qty, it.unit, it.price, it.sizes, it.wood) },
                v.hardware.map { QeProjects.SnapHardware(it.name, it.qty, it.note) }, at,
            )
            db.withTransaction {
                pp.addRevision(PpRevisionEntity("r" + QeOps.uid(), id, v.revision, v.money.total, reason.trim(), QeProjects.encode(snap), at))
                if (r.delta != 0.0) ops.addEntry(OpsProjectEntryEntity(projectId = id, kind = "revision", amount = r.delta, note = r.note, at = at))
                pp.putMeta(meta(id).copy(revision = r.next, updatedAt = at))
            }
            msg("${QeProjects.label(v.revision)} saved. Now editing ${QeProjects.label(r.next)}.")
        }
        return true
    }

    fun renameAttachment(a: OpsAttachmentEntity, name: String) {
        if (name.isBlank()) return msg("Enter a file name.")
        viewModelScope.launch { ops.putAttachment(a.copy(name = name.trim())); msg("File renamed.") }
    }

    fun saveHardware(h: PpHardwareEntity) {
        if (h.name.isBlank()) return msg("Enter the hardware item.")
        viewModelScope.launch { pp.putHardware(if (h.id.isEmpty()) h.copy(id = "h" + QeOps.uid(), createdAt = now()) else h) }
    }
    fun deleteHardware(id: String) { viewModelScope.launch { pp.deleteHardware(id) } }

    /** Comments board: add / edit (stamps editedAt) / delete. */
    fun addComment(id: String, author: String, text: String): Boolean {
        val at = now()
        val ok = QeProjects.append(emptyList(), "c" + QeOps.uid(), author, text, at) ?: run { msg("Write a comment first."); return false }
        viewModelScope.launch { ok.last().let { c -> pp.addComment(PpCommentEntity(c.id, id, c.author, c.text, at)) } }
        return true
    }
    fun editComment(c: PpCommentEntity, text: String): Boolean {
        val at = now()
        val ok = QeProjects.edit(listOf(QeProjects.Comment(c.id, c.author, c.text, c.at, c.editedAt)), c.id, text, at) ?: run { msg("Write a comment first."); return false }
        viewModelScope.launch { pp.editComment(c.id, ok.single().text, at) }
        return true
    }
    fun deleteComment(id: String) { viewModelScope.launch { pp.deleteComment(id) } }

    /** Documents & pictures go into the project's V10.1 attachments (same files as the Operations screen). */
    fun upload(id: String, uris: List<Uri>, asQuotation: Boolean = false) {
        viewModelScope.launch {
            var last: OpsAttachmentEntity? = null
            for (u in uris) {
                val f = Attachments.describe(ctx, u)
                try {
                    last = Attachments.store(ctx, f, id, QeOps.uid(), now()).let { attachment ->
                        (if (asQuotation) attachment.copy(name = "Quotation - " + attachment.name) else attachment).also { ops.putAttachment(it) }
                    }
                }
                catch (_: Attachments.TooLarge) { msg("Skipped " + f.name + ": maximum 15 MB per file.") }
                catch (_: Exception) { msg("File storage failed. Check available device space."); break }
            }
            val a = last ?: return@launch
            if (asQuotation) { pp.putMeta(meta(id).copy(quotePdfId = a.id, updatedAt = now())); msg("Quotation file attached.") } else msg("File(s) attached.")
        }
    }

    /** Save text received through WhatsApp/SMS as a backed-up local quotation attachment. */
    fun savePastedQuotation(projectId: String, text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) { msg("Paste quotation text first."); return }
        if (cleaned.length > 500_000) { msg("Quotation text is too long."); return }
        viewModelScope.launch {
            try {
                val previous = ops.attachments(projectId).firstOrNull { it.name == "Pasted quotation.txt" }
                val id = previous?.id ?: "qt" + QeOps.uid()
                val at = now()
                val file = java.io.File(Attachments.dir(ctx), id)
                withContext(Dispatchers.IO) { file.writeText(cleaned, Charsets.UTF_8) }
                val attachment = OpsAttachmentEntity(id, projectId, "Pasted quotation.txt", "text/plain", file.length(), at, id)
                ops.putAttachment(attachment)
                pp.putMeta(meta(projectId).copy(quotePdfId = id, updatedAt = at))
                msg("Pasted quotation saved locally.")
            } catch (e: Exception) { msg("Unable to save quotation text: ${e.message}") }
        }
    }

    fun deleteQuotationAttachment(a: OpsAttachmentEntity) {
        viewModelScope.launch {
            ops.deleteAttachment(a.id)
            Attachments.delete(ctx, a)
            if (meta(a.projectId).quotePdfId == a.id) pp.putMeta(meta(a.projectId).copy(quotePdfId = ""))
            msg("Quotation attachment removed.")
        }
    }

    /**
     * Quotation tab › Confirm as Project: the open quotation must be saved (it links to that saved job and revision).
     * Items, prices, sizes and wood come from [QeProjects.fromQuotation]; confirming the same job again just reports it.
     */
    fun confirmFromQuotation(state: WorkspaceState, saved: List<SavedProjectEntity>, onError: (String) -> Unit, done: (String) -> Unit) {
        val uuid = state.jobUuid.orEmpty()
        val job = QeRevisions.jobs(saved).firstOrNull { j -> uuid.isNotEmpty() && j.revisions.any { it.jobUuid == uuid } }
            ?: QeRevisions.jobs(saved).firstOrNull { state.projectId.isNotEmpty() && it.rootId == state.projectId }
        if (job == null) return onError(NOT_SAVED)
        viewModelScope.launch {
            val jobUuid = job.revisions.firstOrNull { it.jobUuid.isNotBlank() }?.jobUuid.orEmpty()
            val existing = jobUuid.takeIf { it.isNotEmpty() }?.let { pp.metaByJob(it) }
            if (existing != null && projects.value.any { it.project.id == existing.projectId }) { msg("This quotation is already a project."); done(existing.projectId); return@launch }
            val d = QeProjects.fromQuotation(state)
            val id = QeOps.uid(); val at = now()
            ops.putProject(OpsProjectEntity(id, d.name.ifBlank { "Project" }, d.customer, d.total, "Running", d.notes, at, projects.value.size))
            pp.putMeta(PpProjectEntity(id, d.description, QeProjects.RUNNING, at.take(10), jobUuid, job.rootId, state.revision, "", at))
            pp.putItems(d.items.mapIndexed { i, x -> PpItemEntity("i" + QeOps.uid() + i, id, i, x.name, x.qty, x.unit, x.price, x.sizes, x.wood) })
            msg("Project created from quotation R${state.revision}."); done(id)
        }
    }
}
