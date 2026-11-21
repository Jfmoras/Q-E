package com.moraswood.qande.ops

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moraswood.qande.data.KeyValueEntity
import com.moraswood.qande.data.OpsAttachmentEntity
import com.moraswood.qande.data.OpsAuditEntity
import com.moraswood.qande.data.OpsMaterialEntity
import com.moraswood.qande.data.OpsMovementEntity
import com.moraswood.qande.data.OpsProjectEntity
import com.moraswood.qande.data.OpsProjectEntryEntity
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.StorageKeys
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.OpsDb
import com.moraswood.qande.logic.OpsEntry
import com.moraswood.qande.logic.OpsMaterial
import com.moraswood.qande.logic.OpsMovement
import com.moraswood.qande.logic.OpsProject
import com.moraswood.qande.logic.OpsProjectForm
import com.moraswood.qande.logic.QeOps
import com.moraswood.qande.photos.ItemPhotos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Feature A: an item photo waiting to be attached when the imported quotation becomes a project. */
data class PendingPhoto(val label: String, val file: String)

/** operations.js state + handlers for the Projects and Expenses & Inventory sections. */
class OperationsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = QeDatabase.get(app)
    private val dao = db.operations()
    private val ctx get() = getApplication<Application>()

    /** `db` (projects/materials/movements) rebuilt from Room. */
    val ops: StateFlow<OpsDb> = combine(dao.observeProjects(), dao.observeEntries(), dao.observeMaterials(), dao.observeMovements()) { ps, es, ms, mv ->
        val byProject = es.groupBy { it.projectId }
        fun list(id: String, kind: String) = byProject[id].orEmpty().filter { it.kind == kind }.map { OpsEntry(it.amount, it.note, it.at) }
        OpsDb(
            projects = ps.map { p -> OpsProject(p.id, p.name, p.customer, p.original, p.status, list(p.id, "payment"), list(p.id, "revision"), list(p.id, "expense"), p.notes, p.created) },
            materials = ms.map { OpsMaterial(it.id, it.name, it.unit, it.category, it.rate, it.min) },
            movements = mv.map { OpsMovement(it.id, it.materialId, it.qty, it.type, it.worker, it.projectId, it.rate, it.at) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OpsDb())

    val attachments: StateFlow<Map<String, List<OpsAttachmentEntity>>> =
        dao.observeAllAttachments().map { l -> l.groupBy { it.projectId } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _message = MutableStateFlow("")
    /** `#message` */
    val message: StateFlow<String> = _message.asStateFlow()
    fun msg(s: String) { _message.value = s }

    private val _form = MutableStateFlow(OpsProjectForm())
    val form: StateFlow<OpsProjectForm> = _form.asStateFlow()
    fun setForm(f: OpsProjectForm) { _form.value = f }

    /** `#pFiles` selection. */
    private val _formFiles = MutableStateFlow<List<Attachments.Picked>>(emptyList())
    val formFiles: StateFlow<List<Attachments.Picked>> = _formFiles.asStateFlow()
    fun setFormFiles(uris: List<Uri>) { _formFiles.value = uris.map { Attachments.describe(ctx, it) } }

    private val _pendingPhotos = MutableStateFlow<List<PendingPhoto>>(emptyList())
    /** Feature A: item photos from the exported quotation, attached on "Create project & attach files". */
    val pendingPhotos: StateFlow<List<PendingPhoto>> = _pendingPhotos.asStateFlow()

    private val _viewer = MutableStateFlow<OpsAttachmentEntity?>(null)
    /** `#attachmentViewer` */
    val viewer: StateFlow<OpsAttachmentEntity?> = _viewer.asStateFlow()
    fun closeViewer() { _viewer.value = null }

    private fun now() = com.moraswood.qande.AppViewModel.jsIsoNow()

    /** operations.html is a fresh page load on every visit: empty message and form, no preview. */
    fun resetPage() {
        _message.value = ""; _form.value = OpsProjectForm(); _formFiles.value = emptyList(); _pendingPhotos.value = emptyList(); _viewer.value = null
    }

    /** nav `[data-tab]` click: msg(""). */
    fun onTab() = msg("")

    /**
     * operations.html opened with `?import=quotation`: fill the form from `mwi-project-import-pending`, then remove it.
     * Called when Export to Projects opens Operations (like V10.1, opening Operations from Home leaves it pending).
     */
    fun consumePendingImport() {
        viewModelScope.launch {
            try {
                val raw = db.keyValues().get(StorageKeys.PROJECT_IMPORT_PENDING) ?: return@launch
                val el = Json.parseToJsonElement(raw)
                if (el !is JsonObject) return@launch // JSON.parse(...) → null / non-object: nothing to import
                fun s(o: JsonObject, k: String) = o[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
                val items = runCatching { el["quotation"]!!.jsonObject["items"]!!.jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())
                    .map { QeOps.PendingItem(s(it, "description"), s(it, "qty"), s(it, "unit"), s(it, "unitPrice")) }
                val amount = runCatching { el["amount"]!!.jsonPrimitive.doubleOrNull }.getOrNull() ?: 0.0
                _form.value = _form.value.copy(
                    customer = s(el, "customer"), name = s(el, "name"), value = QeOps.pendingValue(amount),
                    notes = QeOps.pendingNotes(s(el, "notes"), items),
                )
                _pendingPhotos.value = runCatching { el["photos"]!!.jsonArray.map { PendingPhoto(s(it.jsonObject, "label"), s(it.jsonObject, "file")) } }
                    .getOrDefault(emptyList()).filter { it.file.isNotEmpty() }
                db.keyValues().remove(StorageKeys.PROJECT_IMPORT_PENDING)
                msg(QeOps.PENDING_IMPORT_MESSAGE)
            } catch (e: Exception) {
                msg("Could not import quotation: " + e.message)
            }
        }
    }

    /** `#importQuote`: latest autosaved quotation totals into the form. */
    fun importQuote() {
        viewModelScope.launch {
            val q = db.keyValues().get(StorageKeys.AUTOSAVE)?.let { runCatching { QeJson.decodeFromString(WorkspaceState.serializer(), it) }.getOrNull() }
                ?: return@launch msg("No autosaved quotation found.")
            _form.value = _form.value.copy(customer = q.customer.name, name = q.projectName, value = QeOps.importQuoteValue(q))
            msg(QeOps.IMPORT_QUOTE_MESSAGE)
        }
    }

    /** `#createProject` */
    fun createProject() {
        viewModelScope.launch {
            val id = QeOps.uid(); val at = now()
            val out = QeOps.createProject(ops.value, _form.value, id, at)
            val p = out.project ?: return@launch msg(out.message)
            dao.putProject(OpsProjectEntity(p.id, p.name, p.customer, p.original, p.status, p.notes, p.created, ops.value.projects.size))
            p.payments.forEach { dao.addEntry(OpsProjectEntryEntity(projectId = p.id, kind = "payment", amount = it.amount, note = it.note, at = it.at)) }
            var uploaded = 0
            for (f in _formFiles.value) {
                try {
                    dao.putAttachment(Attachments.store(ctx, f, p.id, QeOps.uid(), now())); uploaded++
                } catch (_: Attachments.TooLarge) {
                    msg("Skipped " + f.name + ": file exceeds 15 MB.")
                } catch (e: Exception) {
                    msg("Project created, but an attachment could not be stored: " + e.message); break
                }
            }
            // Feature A: carry the quotation's item photos into the project.
            for (ph in _pendingPhotos.value) {
                runCatching {
                    Attachments.storePhoto(ctx, ItemPhotos.file(ctx, ph.file), ph.label + " photo.jpg", p.id, QeOps.uid(), now())?.let { dao.putAttachment(it); uploaded++ }
                }
            }
            _pendingPhotos.value = emptyList()
            _formFiles.value = emptyList() // $("pFiles").value = ""
            msg(QeOps.createdMessage(uploaded))
        }
    }

    /** `[data-upload]` change on a project card. */
    fun upload(projectId: String, uris: List<Uri>) {
        viewModelScope.launch {
            var added = 0
            for (u in uris) {
                val f = Attachments.describe(ctx, u)
                try {
                    dao.putAttachment(Attachments.store(ctx, f, projectId, QeOps.uid(), now())); added++
                } catch (_: Attachments.TooLarge) {
                    msg("Skipped " + f.name + ": maximum 15 MB per file.")
                } catch (_: Exception) {
                    msg("File storage failed. Check available device space."); break
                }
            }
            if (added > 0) msg("$added file(s) attached. Stored locally, not included in JSON backup.")
        }
    }

    fun open(a: OpsAttachmentEntity) { _viewer.value = a }
    fun openExternally(a: OpsAttachmentEntity) { if (!Attachments.openExternally(ctx, a)) msg("No app on this device can open " + a.name + ".") }

    /** `[data-removefile]` after confirm('Remove this attachment?'). */
    fun removeAttachment(a: OpsAttachmentEntity) {
        viewModelScope.launch {
            dao.deleteAttachment(a.id); Attachments.delete(ctx, a)
            if (_viewer.value?.id == a.id) _viewer.value = null
            msg("Attachment removed.")
        }
    }

    /** `[data-projectnotes]` change. */
    fun setNotes(id: String, notes: String) {
        viewModelScope.launch {
            val e = dao.projects().firstOrNull { it.id == id } ?: return@launch
            dao.putProject(e.copy(notes = notes)); msg("Project notes saved.")
        }
    }

    /** `[data-status]` change (audited). */
    fun setStatus(id: String, status: String) {
        viewModelScope.launch {
            val e = dao.projects().firstOrNull { it.id == id } ?: return@launch
            dao.putProject(e.copy(status = status))
            dao.addAudit(OpsAuditEntity(at = now(), type = "status", refId = id, value = status))
        }
    }

    /** `[data-add]`: returns true when the entry was recorded (the card inputs are then re-rendered empty). */
    fun addEntry(id: String, amount: String, kind: String, note: String, done: () -> Unit) {
        viewModelScope.launch {
            val out = QeOps.addEntry(ops.value, id, amount, kind, note, now())
            if (out.db != null) {
                val e = (when (kind) { "payment" -> out.project!!.payments; "revision" -> out.project!!.revisions; else -> out.project!!.expenses }).last()
                dao.addEntry(OpsProjectEntryEntity(projectId = id, kind = if (kind == "payment" || kind == "revision") kind else "expense", amount = e.amount, note = e.note, at = e.at))
                done()
            }
            msg(out.message)
        }
    }

    /** `[data-delete]` after confirm("Permanently delete NAME and its payment history?"). */
    fun deleteProject(id: String) {
        viewModelScope.launch {
            val e = dao.projects().firstOrNull { it.id == id } ?: return@launch
            val files = dao.attachments(id)
            dao.deleteProjectById(id) // entries and attachment rows cascade
            dao.addAudit(OpsAuditEntity(at = now(), type = "delete-project", refId = id, name = e.name))
            files.forEach { runCatching { Attachments.delete(ctx, it) } }
            if (_viewer.value?.projectId == id) _viewer.value = null
            msg("Project deleted.")
        }
    }

    /** `#addMaterial` */
    fun addMaterial(name: String, unit: String, category: String, rate: String, min: String) {
        viewModelScope.launch {
            val out = QeOps.addMaterial(ops.value, name, unit, category, rate, min, QeOps.uid())
            out.db?.materials?.last()?.let { m -> dao.putMaterial(OpsMaterialEntity(m.id, m.name, m.unit, m.category, m.rate, m.min, ops.value.materials.size)) }
            msg(out.message)
        }
    }

    /** `#recordMovement` (append-only). */
    fun recordMovement(materialId: String, qty: String, type: String, worker: String, projectId: String) {
        viewModelScope.launch {
            val out = QeOps.recordMovement(ops.value, materialId, qty, type, worker, projectId, QeOps.uid(), now())
            out.db?.movements?.last()?.let { m ->
                dao.addMovement(OpsMovementEntity(m.id, m.materialId, m.qty, m.type, m.worker, m.projectId, m.rate, m.at, ops.value.movements.size))
            }
            msg(out.message)
        }
    }
}
