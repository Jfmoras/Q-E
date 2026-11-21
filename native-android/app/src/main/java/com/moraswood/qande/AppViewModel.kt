package com.moraswood.qande

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.HoppusLog
import com.moraswood.qande.data.HoppusState
import com.moraswood.qande.data.JobComponentLogEntity
import com.moraswood.qande.data.KeyValueEntity
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.QuickCalc
import com.moraswood.qande.data.QuickGroup
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.StorageKeys
import com.moraswood.qande.data.TemplateEntity
import com.moraswood.qande.data.TemplateRowEntity
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.data.hasProjectWork
import com.moraswood.qande.data.normalizeUnits
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.logic.QeMath.hasData
import com.moraswood.qande.photos.ItemPhotos
import com.moraswood.qande.templates.TemplateMatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import java.time.Instant
import java.util.UUID

/** Banner shown above the calculator item list (template library). */
data class TemplateBanner(
    val itemIndex: Int,
    val message: String,
    val rows: List<Row>,
    /** Stable id so Dismiss hides exactly this suggestion. */
    val key: String,
)

/** One saved template with its rows, for the Settings › Templates list. */
data class TemplateWithRows(val template: TemplateEntity, val rows: List<TemplateRowEntity>)

/** App-wide state holder, mirroring app.js `state`, `quick` and `logs`. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val db = QeDatabase.get(app)

    private val _workspace = MutableStateFlow(WorkspaceState.default())
    /** app.js `state`, loaded from `mwi-autosave-v1` (loadAutosave). */
    val workspace: StateFlow<WorkspaceState> = _workspace.asStateFlow()

    private val _quick = MutableStateFlow(QuickCalc())
    /** app.js `quick` (`mwi-quick-calc-v1`). */
    val quick: StateFlow<QuickCalc> = _quick.asStateFlow()

    private val _hoppus = MutableStateFlow(HoppusState())
    /** app.js `logs` (`mwi-hoppus-v1`). */
    val hoppus: StateFlow<HoppusState> = _hoppus.asStateFlow()

    val savedProjects: StateFlow<List<SavedProjectEntity>> =
        db.savedProjects().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /* ---------- template library state ---------- */
    private val _logs = MutableStateFlow<List<JobComponentLogEntity>>(emptyList())
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    private val _sessionKey = MutableStateFlow("")
    private val _templateStatus = MutableStateFlow<Map<Int, String>>(emptyMap())
    /** Per-item "Save as template" status line. */
    val templateStatus: StateFlow<Map<Int, String>> = _templateStatus.asStateFlow()

    val templates: StateFlow<List<TemplateWithRows>> =
        combine(db.templates().observeTemplates(), db.templates().observeTemplateRows()) { ts, rows ->
            val byId = rows.groupBy { it.templateId }
            ts.map { TemplateWithRows(it, byId[it.id].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val templateBanner: StateFlow<TemplateBanner?> =
        combine(_workspace, _logs, templates, _dismissed, _sessionKey) { ws, logs, tpls, dismissed, session ->
            computeBanner(ws, logs, tpls, dismissed, setOf(ws.projectId, session).filter { it.isNotEmpty() }.toSet())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var logJob: Job? = null

    init {
        viewModelScope.launch {
            val kv = db.keyValues()
            kv.get(StorageKeys.AUTOSAVE)?.let { json ->
                runCatching { QeJson.decodeFromString(WorkspaceState.serializer(), json).normalizeUnits() }.onSuccess { _workspace.value = it }
            }
            restore(StorageKeys.QUICK_CALC, QuickCalc.serializer())?.let { _quick.value = it }
            restore(StorageKeys.HOPPUS, HoppusState.serializer())?.let { _hoppus.value = it }
            _sessionKey.value = kv.get(SESSION_KEY) ?: newSessionKey()
            _logs.value = runCatching { db.templates().allLogs() }.getOrDefault(emptyList())
        }
    }

    private suspend fun <T> restore(key: String, serializer: KSerializer<T>): T? {
        val json = db.keyValues().get(key) ?: return null
        return runCatching { QeJson.decodeFromString(serializer, json) }.getOrNull()
    }

    private suspend fun newSessionKey(): String {
        val key = "session-" + UUID.randomUUID().toString()
        db.keyValues().put(KeyValueEntity(SESSION_KEY, key))
        return key
    }

    fun hasProjectWork(): Boolean = _workspace.value.hasProjectWork()

    fun setTab(tab: String) = update { it.copy(tab = tab) }
    fun setProjectName(name: String) = update { it.copy(projectName = name) }

    /** app.js `startNewProject()` after the confirm: `state = defaultState()`. */
    fun startNewProject() {
        logJob?.let { it.cancel(); flushLogs(_workspace.value) }
        update { WorkspaceState.default() }
        _dismissed.value = emptySet()
        _templateStatus.value = emptyMap()
        viewModelScope.launch { _sessionKey.value = newSessionKey() }
    }

    /* ---------- Timber Calculator (renderCalc handlers) ---------- */

    private fun updateItems(log: Boolean = true, change: (List<CalcItem>) -> List<CalcItem>) {
        update { it.copy(items = change(it.items)) }
        if (log) scheduleLog()
    }

    private fun updateItem(ii: Int, log: Boolean = true, change: (CalcItem) -> CalcItem) =
        updateItems(log) { items -> items.mapIndexed { i, it -> if (i == ii) change(it) else it } }

    fun setItemName(ii: Int, name: String) = updateItem(ii) { it.copy(name = name) }
    fun setItemTag(ii: Int, tag: String) = updateItem(ii) { it.copy(tag = tag) }

    /** `calc-cell` input / `row-mode` / `measure-unit` change. */
    fun setRowField(ii: Int, ri: Int, key: String, value: String) = updateItem(ii) { item ->
        item.copy(rows = item.rows.mapIndexed { i, r -> if (i == ri) r.with(key, value) else r })
    }

    fun addRow(ii: Int) = updateItem(ii) { it.copy(rows = it.rows + Row.blank()) }
    fun addItem() = updateItems { it + CalcItem.blank() }

    /** `#sendToEstimation` — exact V10.1 copy; the native item photo is carried along. */
    fun sendToEstimation() {
        update { s ->
            val x = s.items.mapIndexed { i, it ->
                CalcItem(name = it.name.trim().ifEmpty { "Item ${i + 1}" }, rows = it.rows.filter { r -> r.hasData() }, photo = it.photo)
            }.filter { it.rows.isNotEmpty() }
            s.copy(estimationItems = x, costings = x.map { Costing.blank() }, tab = "estimation")
        }
    }

    /* ---------- item photos (Feature A) ---------- */

    fun setItemPhoto(ii: Int, fileName: String) {
        val old = _workspace.value.items.getOrNull(ii)?.photo.orEmpty()
        updateItem(ii, log = false) { it.copy(photo = fileName) }
        viewModelScope.launch {
            if (fileName.isNotEmpty()) ItemPhotos.register(getApplication(), db, fileName)
            if (old.isNotEmpty() && old != fileName && !photoReferenced(old)) ItemPhotos.delete(getApplication(), db, old)
        }
    }

    private suspend fun photoReferenced(name: String): Boolean {
        val s = _workspace.value
        if (s.items.any { it.photo == name } || s.estimationItems.any { it.photo == name }) return true
        return db.savedProjects().all().any { it.snapshotJson.contains(name) }
    }

    /* ---------- template library ---------- */

    /** Furniture type of an item: its tag if set, otherwise its name. */
    fun typeLabel(item: CalcItem): String = item.tag.trim().ifEmpty { item.name.trim() }

    private fun jobKeys(s: WorkspaceState): List<String> = listOf(s.projectId, _sessionKey.value).filter { it.isNotEmpty() }.distinct()
    private fun primaryJobKey(s: WorkspaceState): String = s.projectId.ifEmpty { _sessionKey.value }

    /** Silent, debounced logging of the calculator items into job_component_log. */
    private fun scheduleLog() {
        logJob?.cancel()
        logJob = viewModelScope.launch {
            delay(1500)
            writeLogs(_workspace.value)
            logJob = null
        }
    }

    private fun flushLogs(s: WorkspaceState) { viewModelScope.launch { writeLogs(s) } }

    private suspend fun writeLogs(s: WorkspaceState) {
        val keys = jobKeys(s)
        val key = primaryJobKey(s)
        if (key.isEmpty()) return
        val dao = db.templates()
        val now = Instant.now().toString()
        runCatching {
            s.items.forEachIndexed { ii, item ->
                val label = typeLabel(item)
                val type = TemplateMatcher.normalizeType(label)
                val rows = if (type.isEmpty()) emptyList() else item.rows.filter { it.hasData() }.mapIndexed { ri, r ->
                    val c = QeMath.calcRow(r)
                    JobComponentLogEntity(
                        jobKey = key, itemIndex = ii, rowIndex = ri, furnitureType = type, typeLabel = label,
                        l = r.l, lUnit = r.lUnit, w = r.w, wUnit = r.wUnit, t = r.t, tUnit = r.tUnit, q = r.q, mode = r.mode,
                        lengthFt = c.lFt, widthIn = c.wIn, thicknessIn = c.tIn, qty = QeMath.num(r.q), loggedAt = now,
                    )
                }
                dao.replaceItemLog(keys, ii, rows)
            }
            dao.deleteLogsFrom(keys, s.items.size)
            _logs.value = dao.allLogs()
        }
    }

    private fun computeBanner(
        s: WorkspaceState,
        logs: List<JobComponentLogEntity>,
        tpls: List<TemplateWithRows>,
        dismissed: Set<String>,
        currentKeys: Set<String>,
    ): TemplateBanner? {
        val jobs by lazy {
            logs.filter { it.jobKey !in currentKeys }.groupBy { it.jobKey to it.itemIndex }.map { (k, rows) ->
                TemplateMatcher.Job(
                    key = "${k.first}#${k.second}",
                    type = rows.first().furnitureType,
                    components = rows.sortedBy { it.rowIndex }.map { e ->
                        TemplateMatcher.Component(
                            e.lengthFt, e.widthIn, e.thicknessIn, e.qty, e.mode,
                            Row(e.l, e.lUnit, e.w, e.wUnit, e.t, e.tUnit, e.q, e.mode),
                        )
                    },
                    recency = rows.maxOf { it.loggedAt },
                )
            }
        }
        s.items.forEachIndexed { ii, item ->
            val label = typeLabel(item)
            val type = TemplateMatcher.normalizeType(label)
            if (type.isEmpty() || item.rows.any { it.hasData() }) return@forEachIndexed
            // Saved templates take priority over learned clusters.
            val tpl = tpls.filter { it.rows.isNotEmpty() && TemplateMatcher.typeSimilarity(type, it.template.furnitureType) >= TemplateMatcher.TYPE_THRESHOLD }
                .maxByOrNull { TemplateMatcher.typeSimilarity(type, it.template.furnitureType) }
            if (tpl != null) {
                val key = "tpl:${tpl.template.id}:$ii"
                if (key !in dismissed) {
                    return TemplateBanner(
                        ii,
                        "Saved template “${tpl.template.name}” matches this ${label.trim()} — use that template?",
                        tpl.rows.sortedBy { it.position }.map { Row(it.l, it.lUnit, it.w, it.wUnit, it.t, it.tUnit, it.q, it.mode) },
                        key,
                    )
                }
            }
            val suggestion = TemplateMatcher.suggest(type, jobs) ?: return@forEachIndexed
            val key = "cluster:$type:${suggestion.jobCount}:$ii"
            if (key in dismissed) return@forEachIndexed
            return TemplateBanner(
                ii,
                "This looks like your last ${suggestion.jobCount} ${TemplateMatcher.plural(label.trim().lowercase())} — use that template?",
                suggestion.rows,
                key,
            )
        }
        return null
    }

    /** Confirm: pre-fill the item rows (still editable). */
    fun applyTemplate(banner: TemplateBanner) {
        updateItem(banner.itemIndex) { it.copy(rows = banner.rows.ifEmpty { listOf(Row.blank()) }) }
        _dismissed.value = _dismissed.value + banner.key
    }

    fun dismissTemplate(banner: TemplateBanner) { _dismissed.value = _dismissed.value + banner.key }

    /** "Save as template" for one calculator item. */
    fun saveItemAsTemplate(ii: Int) {
        val s = _workspace.value
        val item = s.items.getOrNull(ii) ?: return
        val label = typeLabel(item)
        val type = TemplateMatcher.normalizeType(label)
        val rows = item.rows.filter { it.hasData() }
        fun status(msg: String) { _templateStatus.value = _templateStatus.value + (ii to msg) }
        if (type.isEmpty()) return status("Enter an item name or furniture type first.")
        if (rows.isEmpty()) return status("Enter at least one row first.")
        viewModelScope.launch {
            runCatching {
                db.templates().saveTemplate(
                    TemplateEntity(name = label, furnitureType = type, createdAt = Instant.now().toString(), sourceJobKey = primaryJobKey(s)),
                    rows.mapIndexed { i, r -> TemplateRowEntity(templateId = 0, position = i, l = r.l, lUnit = r.lUnit, w = r.w, wUnit = r.wUnit, t = r.t, tUnit = r.tUnit, q = r.q, mode = r.mode) },
                )
            }.onSuccess { status("Saved as template “$label”.") }.onFailure { status("Could not save the template.") }
        }
    }

    fun deleteTemplate(id: Long) { viewModelScope.launch { runCatching { db.templates().deleteTemplate(id) } } }

    /* ---------- General CFT / KOL calculator ---------- */

    private fun updateQuick(change: (QuickCalc) -> QuickCalc) {
        _quick.value = change(_quick.value)
        val snapshot = _quick.value
        viewModelScope.launch { runCatching { db.keyValues().put(KeyValueEntity(StorageKeys.QUICK_CALC, QeJson.encodeToString(QuickCalc.serializer(), snapshot))) } }
    }

    fun setQuickRate(cft: Boolean, value: String) = updateQuick { if (cft) it.copy(cftRate = value) else it.copy(kolRate = value) }
    fun setQuickField(i: Int, key: String, value: String) = updateQuick { q -> q.copy(rows = q.rows.mapIndexed { idx, r -> if (idx == i) r.with(key, value) else r }) }
    fun addQuickRow() = updateQuick { it.copy(rows = it.rows + Row.blank()) }
    fun setQuickGroupRate(id: String, value: String) = updateQuick { q -> q.copy(groups = q.groups.map { if (it.id == id) it.copy(rate = value) else it }) }
    fun ungroupQuick(id: String) = updateQuick { q -> q.copy(groups = q.groups.filter { it.id != id }) }

    /** `#quickMakeGroup`; returns the alert text or null. */
    fun groupQuickRows(rows: List<Int>): String? {
        if (rows.isEmpty()) return "Select rows first."
        val q = _quick.value
        val modes = rows.map { q.rows.getOrNull(it)?.mode.orEmpty() }.distinct()
        if (modes.size != 1) return "Separate CFT and KOL groups."
        updateQuick { cur ->
            val kept = cur.groups.map { g -> g.copy(rows = g.rows.filter { it !in rows }) }.filter { it.rows.isNotEmpty() }
            val mode = modes[0]
            cur.copy(
                groups = kept + QuickGroup(
                    id = uid(), rows = rows, mode = mode, rate = if (mode == "CFT") cur.cftRate else cur.kolRate,
                    color = QUICK_COLORS[kept.size % 5],
                ),
            )
        }
        return null
    }

    /* ---------- Hoppus ---------- */

    private fun updateHoppus(change: (HoppusState) -> HoppusState) {
        _hoppus.value = change(_hoppus.value)
        val snapshot = _hoppus.value
        viewModelScope.launch { runCatching { db.keyValues().put(KeyValueEntity(StorageKeys.HOPPUS, QeJson.encodeToString(HoppusState.serializer(), snapshot))) } }
    }

    fun setHoppusField(i: Int, key: String, value: String) = updateHoppus { h ->
        h.copy(rows = h.rows.mapIndexed { idx, r -> if (idx != i) r else if (key == "length") r.copy(length = value) else r.copy(girth = value) })
    }
    fun addHoppusLog() = updateHoppus { it.copy(rows = it.rows + HoppusLog()) }
    fun removeHoppusLog(i: Int) = updateHoppus { h -> h.copy(rows = h.rows.filterIndexed { idx, _ -> idx != i }) }
    fun setSawRate(v: String) = updateHoppus { it.copy(sawRate = v) }

    /* ---------- persistence ---------- */

    private fun update(change: (WorkspaceState) -> WorkspaceState) {
        _workspace.value = change(_workspace.value)
        saveAutosave()
    }

    /** app.js `saveAutosave()` — every edit persists the full state. */
    private fun saveAutosave() {
        val snapshot = _workspace.value
        viewModelScope.launch {
            runCatching { db.keyValues().put(KeyValueEntity(StorageKeys.AUTOSAVE, QeJson.encodeToString(WorkspaceState.serializer(), snapshot))) }
        }
    }

    override fun onCleared() {
        logJob?.let { it.cancel(); kotlinx.coroutines.runBlocking { writeLogs(_workspace.value) } }
        super.onCleared()
    }

    companion object {
        const val SESSION_KEY = "native-job-session-v1"
        /** V10.1 quick-group colours (CSS named colours). */
        val QUICK_COLORS = listOf("red", "blue", "green", "orange", "purple")

        /** app.js `uid()`: 7 random base-36 characters. */
        fun uid(): String = (1..7).map { "0123456789abcdefghijklmnopqrstuvwxyz"[(Math.random() * 36).toInt()] }.joinToString("")
    }
}

/** `row[key] = value` for the calculator keys used by V10.1. */
fun Row.with(key: String, value: String): Row = when (key) {
    "l" -> copy(l = value); "lUnit" -> copy(lUnit = value)
    "w" -> copy(w = value); "wUnit" -> copy(wUnit = value)
    "t" -> copy(t = value); "tUnit" -> copy(tUnit = value)
    "q" -> copy(q = value); "mode" -> copy(mode = value)
    else -> this
}
