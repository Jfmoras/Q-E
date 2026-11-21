package com.moraswood.qande.projects

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moraswood.qande.AppViewModel
import com.moraswood.qande.data.OpsAttachmentEntity
import com.moraswood.qande.data.OpsProjectEntity
import com.moraswood.qande.data.OpsProjectEntryEntity
import com.moraswood.qande.data.PpCommentEntity
import com.moraswood.qande.data.PpHardwareEntity
import com.moraswood.qande.data.PpItemEntity
import com.moraswood.qande.data.PpProjectEntity
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

/** One dashboard project: the V10.1 operations project + the dashboard's own rows. */
data class PpView(
    val project: OpsProject,
    val meta: PpProjectEntity,
    val items: List<PpItemEntity>,
    val hardware: List<PpHardwareEntity>,
    val comments: List<PpCommentEntity>,
) {
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
        ps.map { p -> OpsProject(p.id, p.name, p.customer, p.original, p.status, list(p.id, "payment"), list(p.id, "revision"), list(p.id, "expense"), p.notes, p.created) }
    }
    private val extras = combine(pp.observeMeta(), pp.observeItems(), pp.observeHardware(), pp.observeComments()) { m, i, h, c -> Extras(m, i, h, c) }
    private data class Extras(val meta: List<PpProjectEntity>, val items: List<PpItemEntity>, val hardware: List<PpHardwareEntity>, val comments: List<PpCommentEntity>)

    val projects: StateFlow<List<PpView>> = combine(opsProjects, extras) { ps, x ->
        val meta = x.meta.associateBy { it.projectId }
        val items = x.items.groupBy { it.projectId }; val hw = x.hardware.groupBy { it.projectId }; val cm = x.comments.groupBy { it.projectId }
        ps.map { p -> PpView(p, meta[p.id] ?: PpProjectEntity(p.id), items[p.id].orEmpty(), hw[p.id].orEmpty(), cm[p.id].orEmpty()) }
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

    fun saveHardware(h: PpHardwareEntity) {
        if (h.name.isBlank()) return msg("Enter the hardware item.")
        viewModelScope.launch { pp.putHardware(if (h.id.isEmpty()) h.copy(id = "h" + QeOps.uid(), createdAt = now()) else h) }
    }
    fun deleteHardware(id: String) { viewModelScope.launch { pp.deleteHardware(id) } }

    /** Comments board: append only. */
    fun addComment(id: String, author: String, text: String): Boolean {
        val at = now()
        val ok = QeProjects.append(emptyList(), author, text, at) ?: run { msg("Write a comment first."); return false }
        viewModelScope.launch { pp.addComment(PpCommentEntity("c" + QeOps.uid(), id, ok.last().author, ok.last().text, at)) }
        return true
    }

    /** Documents & pictures go into the project's V10.1 attachments (same files as the Operations screen). */
    fun upload(id: String, uris: List<Uri>, asQuotation: Boolean = false) {
        viewModelScope.launch {
            var last: OpsAttachmentEntity? = null
            for (u in uris) {
                val f = Attachments.describe(ctx, u)
                try { last = Attachments.store(ctx, f, id, QeOps.uid(), now()).also { ops.putAttachment(it) } }
                catch (_: Attachments.TooLarge) { msg("Skipped " + f.name + ": maximum 15 MB per file.") }
                catch (_: Exception) { msg("File storage failed. Check available device space."); break }
            }
            val a = last ?: return@launch
            if (asQuotation) { pp.putMeta(meta(id).copy(quotePdfId = a.id, updatedAt = now())); msg("Quotation PDF attached.") } else msg("File(s) attached.")
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
