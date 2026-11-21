package com.moraswood.qande

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.HoppusLog
import com.moraswood.qande.data.DwDefaults
import com.moraswood.qande.data.DwStructure
import com.moraswood.qande.data.HoppusState
import com.moraswood.qande.data.JobComponentLogEntity
import com.moraswood.qande.data.KeyValueEntity
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.QuickCalc
import com.moraswood.qande.data.QuickGroup
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.RateGroup
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.StorageKeys
import com.moraswood.qande.data.TemplateEntity
import com.moraswood.qande.data.TemplateRowEntity
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.data.hasProjectWork
import com.moraswood.qande.data.normalizeUnits
import com.moraswood.qande.logic.QeEstimate
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.logic.QeMath.hasData
import com.moraswood.qande.logic.QeQuote
import com.moraswood.qande.logic.QeSpecies
import com.moraswood.qande.logic.QeRevisions
import com.moraswood.qande.logic.QeArea
import com.moraswood.qande.logic.QeDoorWindow
import com.moraswood.qande.logic.QeGroups
import com.moraswood.qande.ai.AiService
import com.moraswood.qande.photos.QuoteImages
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.moraswood.qande.photos.ItemPhotos
import com.moraswood.qande.templates.TemplateMatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.util.UUID

/** Banner shown above the calculator item list (template library). */
data class TemplateBanner(
    /** "calculator" (state.items) or "estimation" (state.estimationItems). */
    val target: String,
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

    private val banners: StateFlow<Pair<TemplateBanner?, TemplateBanner?>> =
        combine(_workspace, _logs, templates, _dismissed, _sessionKey) { ws, logs, tpls, dismissed, session ->
            val keys = setOf(ws.projectId, session).filter { it.isNotEmpty() }.toSet()
            computeBanner("calculator", ws.items, logs, tpls, dismissed, keys) { item -> item.rows.none { it.hasData() } } to
                computeBanner("estimation", ws.estimationItems, logs, tpls, dismissed, keys) { item -> item.rows.isEmpty() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null to null)

    /** Banner above the Timber Calculator item list. */
    val templateBanner: StateFlow<TemplateBanner?> = banners.map { it.first }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** Banner above the Estimation item list (for manually added estimation items without rows). */
    val estimationBanner: StateFlow<TemplateBanner?> = banners.map { it.second }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _estStatus = MutableStateFlow<Map<Int, String>>(emptyMap())
    /** `.send-quote-status` text per estimation card; a present entry also marks the selling price input-error. */
    val estimationStatus: StateFlow<Map<Int, String>> = _estStatus.asStateFlow()


    private var logJob: Job? = null

    private val _dwTemplates = MutableStateFlow<List<DwStructure>>(emptyList())
    /** Feature I: saved custom structures (kv `mwi-native-dw-templates-v1`). */
    val dwTemplates: StateFlow<List<DwStructure>> = _dwTemplates.asStateFlow()
    private val _dwDefaults = MutableStateFlow(DwDefaults())
    /** Feature I: Settings defaults (Juttu extension per end, wastage per cut). */
    val dwDefaults: StateFlow<DwDefaults> = _dwDefaults.asStateFlow()

    init {
        viewModelScope.launch {
            val kv = db.keyValues()
            kv.get(StorageKeys.AUTOSAVE)?.let { json ->
                runCatching { QeQuote.syncActiveOption(QeEstimate.syncCostings(QeJson.decodeFromString(WorkspaceState.serializer(), json).normalizeUnits())) }.onSuccess { _workspace.value = it }
            }
            restore(StorageKeys.QUICK_CALC, QuickCalc.serializer())?.let { _quick.value = it }
            restore(StorageKeys.HOPPUS, HoppusState.serializer())?.let { _hoppus.value = it }
            restore(StorageKeys.DW_TEMPLATES, ListSerializer(DwStructure.serializer()))?.let { _dwTemplates.value = it }
            restore(StorageKeys.DW_DEFAULTS, DwDefaults.serializer())?.let { _dwDefaults.value = it }
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
        loadedReadOnly = false
        _aiStatus.value = emptyMap()
        logJob?.let { it.cancel(); flushLogs(_workspace.value) }
        update { WorkspaceState.default() }
        _dismissed.value = emptySet()
        _templateStatus.value = emptyMap()
        _estStatus.value = emptyMap()
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
                CalcItem(name = it.name.trim().ifEmpty { "Item ${i + 1}" }, rows = it.rows.filter { r -> r.hasData() }, tag = it.tag, photo = it.photo, category = it.category)
            }.filter { it.rows.isNotEmpty() }
            s.copy(estimationItems = x, costings = x.map { Costing.blank() }, tab = "estimation", itemGroups = null)
        }
        _estStatus.value = emptyMap()
        scheduleLog()
    }

    /* ---------- Estimation & Rate Builder (renderEstimation handlers) ---------- */

    private fun updateEstimation(log: Boolean = false, change: (WorkspaceState) -> WorkspaceState) {
        update(change)
        if (log) scheduleLog()
    }

    private fun updateCosting(i: Int, change: (Costing) -> Costing) = updateEstimation { s ->
        val costings = s.costings.toMutableList()
        while (costings.size <= i) costings.add(Costing.blank())
        costings[i] = change(costings[i])
        s.copy(costings = costings)
    }

    /** `est-name` input. */
    fun setEstimationName(i: Int, name: String) = updateEstimation(log = true) { s ->
        s.copy(estimationItems = s.estimationItems.mapIndexed { j, it -> if (j == i) it.copy(name = name) else it })
    }

    /** `est-input` / `est-unit` / `labour-mode` / `polish-mode`. Typing the selling price turns sellingAuto off. */
    fun setCostingField(i: Int, key: String, value: String) {
        updateCosting(i) { c -> c.with(key, value).let { if (key == "sellingPrice") it.copy(sellingAuto = false) else it } }
        if (key == "sellingPrice") _estStatus.value = _estStatus.value - i
    }

    /** Feature G: mark an estimation row as a vertical piece (area = length × thickness) or back to length × width. */
    fun setEstimationRowFace(i: Int, ri: Int, vertical: Boolean) = updateEstimation(log = true) { s ->
        s.copy(estimationItems = s.estimationItems.mapIndexed { j, it ->
            if (j != i) it else it.copy(rows = it.rows.mapIndexed { k, r -> if (k == ri) r.copy(areaFace = if (vertical) "LT" else null) else r })
        })
    }

    /** Feature G: the item area carried to a quotation line (null when n/a). */
    private fun quoteArea(st: WorkspaceState, i: Int): String? {
        val item = st.estimationItems.getOrNull(i) ?: return null
        return QeArea.label(item, st.costings.getOrNull(i)).takeIf { it != "n/a" }
    }

    /** `.group-timber-rows`; returns the alert text or null. */
    fun groupTimberRows(i: Int, rows: List<Int>): String? {
        val s = _workspace.value
        val item = s.estimationItems.getOrNull(i) ?: return null
        if (rows.isEmpty()) return "Select at least one timber row."
        val types = rows.map { item.rows.getOrNull(it)?.mode }.distinct()
        if (types.size != 1 || types[0] !in listOf("CFT", "KOL")) return "CFT and KOL rows must be grouped separately."
        updateCosting(i) { c ->
            val kept = c.rateGroups.map { g -> g.copy(rows = g.rows.filter { it !in rows }) }.filter { it.rows.isNotEmpty() }
            val mode = types[0]!!
            c.copy(
                rateGroups = kept + RateGroup(
                    id = uid(), name = "Group " + (kept.size + 1), mode = mode, color = QeEstimate.GroupColors[kept.size % 5],
                    rate = if (mode == "CFT") c.cftRate else c.kolRate, rows = rows,
                ),
            )
        }
        return null
    }

    /** `.ungroup-timber-rows` */
    fun ungroupTimberRows(i: Int, rows: List<Int>) = updateCosting(i) { c ->
        c.copy(rateGroups = c.rateGroups.map { g -> g.copy(rows = g.rows.filter { it !in rows }) }.filter { it.rows.isNotEmpty() })
    }

    fun setGroupRate(i: Int, id: String, rate: String) = updateCosting(i) { c -> c.copy(rateGroups = c.rateGroups.map { if (it.id == id) it.copy(rate = rate) else it }) }
    fun removeTimberGroup(i: Int, id: String) = updateCosting(i) { c -> c.copy(rateGroups = c.rateGroups.filter { it.id != id }) }

    /** `#newEstimateItem` */
    /* ---------- Feature F: item groups ---------- */
    /** Returns the alert text or null. */
    fun groupItems(selected: List<Int>): String? {
        var err: String? = null
        updateEstimation { s -> QeGroups.group(s, selected) ?: s.also { err = "Select one or more items to group." } }
        return err
    }
    fun ungroupItems(id: String) = updateEstimation { QeGroups.ungroup(it, id) }
    fun setItemGroupField(id: String, key: String, v: String) = updateEstimation { s -> QeGroups.update(s, id) { QeGroups.setField(it, key, v) } }
    fun toggleItemGroup(id: String) = updateEstimation { s -> QeGroups.update(s, id) { it.copy(collapsed = if (it.collapsed == true) null else true) } }

    fun newEstimationItem() = updateEstimation { s -> s.copy(estimationItems = s.estimationItems + CalcItem(name = "", rows = emptyList()), costings = s.costings + Costing.blank()) }

    /** `#sendToQuotation`; returns the index of the first item without a selling price, or -1 on success. */
    fun sendToQuotation(): Int {
        val s = _workspace.value
        val prices = QeGroups.quotePrices(s) // Feature F: grouped items carry their share of the group price
        val missing = s.estimationItems.indices.firstOrNull { QeMath.num(prices.getOrNull(it)) <= 0 } ?: -1
        if (missing >= 0) {
            _estStatus.value = _estStatus.value + (missing to SELLING_MISSING)
            return missing
        }
        update { st ->
            val p = QeGroups.quotePrices(st)
            val q = st.estimationItems.mapIndexed { i, it -> QuoteItem(description = it.name, unitPrice = p[i], wood = QeSpecies.link(it, QeGroups.effectiveCosting(st, i)), area = quoteArea(st, i)) }
            st.copy(quoteItems = q.ifEmpty { listOf(QuoteItem.blank()) }, tab = "quotation")
        }
        return -1
    }

    /** `.send-one-quote`; returns false when the selling price is missing. */
    fun sendOneToQuotation(i: Int): Boolean {
        val s = _workspace.value
        val it = s.estimationItems.getOrNull(i) ?: return true
        val price = QeGroups.quotePrices(s).getOrNull(i).orEmpty()
        if (QeMath.num(price) <= 0) {
            _estStatus.value = _estStatus.value + (i to SELLING_MISSING)
            return false
        }
        _estStatus.value = _estStatus.value - i
        update { st ->
            val q = QuoteItem(description = it.name, unitPrice = price, wood = QeSpecies.link(it, QeGroups.effectiveCosting(st, i)), area = quoteArea(st, i))
            val only = st.quoteItems
            val onlyBlank = only.size == 1 && only[0].description.isEmpty() && only[0].unit.isEmpty() && only[0].qty.isEmpty() && only[0].unitPrice.isEmpty() && only[0].images.isEmpty()
            st.copy(quoteItems = if (onlyBlank) listOf(q) else st.quoteItems + q, tab = "quotation")
        }
        return true
    }

    fun setEstimationPhoto(i: Int, fileName: String) {
        val old = _workspace.value.estimationItems.getOrNull(i)?.photo.orEmpty()
        updateEstimation { s -> s.copy(estimationItems = s.estimationItems.mapIndexed { j, it -> if (j == i) it.copy(photo = fileName) else it }) }
        viewModelScope.launch {
            if (fileName.isNotEmpty()) ItemPhotos.register(getApplication(), db, fileName)
            if (old.isNotEmpty() && old != fileName && !photoReferenced(old)) ItemPhotos.delete(getApplication(), db, old)
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
        fun entries(itemIndex: Int, label: String, type: String, rows: List<Row>) = rows.mapIndexed { ri, r ->
            val c = QeMath.calcRow(r)
            JobComponentLogEntity(
                jobKey = key, itemIndex = itemIndex, rowIndex = ri, furnitureType = type, typeLabel = label,
                l = r.l, lUnit = r.lUnit, w = r.w, wUnit = r.wUnit, t = r.t, tUnit = r.tUnit, q = r.q, mode = r.mode,
                lengthFt = c.lFt, widthIn = c.wIn, thicknessIn = c.tIn, qty = QeMath.num(r.q), loggedAt = now,
            )
        }
        runCatching {
            val calcSignatures = HashSet<Pair<String, List<Row>>>()
            s.items.forEachIndexed { ii, item ->
                val label = typeLabel(item)
                val type = TemplateMatcher.normalizeType(label)
                val rows = if (type.isEmpty()) emptyList() else item.rows.filter { it.hasData() }
                if (rows.isNotEmpty()) calcSignatures.add(type to rows)
                dao.replaceItemLog(keys, ii, entries(ii, label, type, rows))
            }
            dao.deleteLogsRange(keys, s.items.size, ESTIMATION_LOG_OFFSET)
            // Estimation rows are logged too (index offset), unless they are just the copy sent from the calculator.
            s.estimationItems.forEachIndexed { i, item ->
                val label = typeLabel(item)
                val type = TemplateMatcher.normalizeType(label)
                val placeholder = item.tag.isBlank() && Regex("Item \\d+").matches(item.name)
                val rows = if (type.isEmpty() || placeholder) emptyList() else item.rows.filter { it.hasData() }
                val duplicate = (type to rows) in calcSignatures
                dao.replaceItemLog(keys, ESTIMATION_LOG_OFFSET + i, if (duplicate) emptyList() else entries(ESTIMATION_LOG_OFFSET + i, label, type, rows))
            }
            dao.deleteLogsFrom(keys, ESTIMATION_LOG_OFFSET + s.estimationItems.size)
            _logs.value = dao.allLogs()
        }
    }

    private fun computeBanner(
        target: String,
        items: List<CalcItem>,
        logs: List<JobComponentLogEntity>,
        tpls: List<TemplateWithRows>,
        dismissed: Set<String>,
        currentKeys: Set<String>,
        eligible: (CalcItem) -> Boolean,
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
        items.forEachIndexed { ii, item ->
            val label = typeLabel(item)
            val type = TemplateMatcher.normalizeType(label)
            if (type.isEmpty() || !eligible(item)) return@forEachIndexed
            // Saved templates take priority over learned clusters.
            val tpl = tpls.filter { it.rows.isNotEmpty() && TemplateMatcher.typeSimilarity(type, it.template.furnitureType) >= TemplateMatcher.TYPE_THRESHOLD }
                .maxByOrNull { TemplateMatcher.typeSimilarity(type, it.template.furnitureType) }
            if (tpl != null) {
                val key = "$target:tpl:${tpl.template.id}:$ii"
                if (key !in dismissed) {
                    return TemplateBanner(
                        target, ii,
                        "Saved template “${tpl.template.name}” matches this ${label.trim()} — use that template?",
                        tpl.rows.sortedBy { it.position }.map { Row(it.l, it.lUnit, it.w, it.wUnit, it.t, it.tUnit, it.q, it.mode) },
                        key,
                    )
                }
            }
            val suggestion = TemplateMatcher.suggest(type, jobs) ?: return@forEachIndexed
            val key = "$target:cluster:$type:${suggestion.jobCount}:$ii"
            if (key in dismissed) return@forEachIndexed
            return TemplateBanner(
                target, ii,
                "This looks like your last ${suggestion.jobCount} ${TemplateMatcher.plural(label.trim().lowercase())} — use that template?",
                suggestion.rows,
                key,
            )
        }
        return null
    }

    /** Confirm: pre-fill the item rows (still editable). */
    fun applyTemplate(banner: TemplateBanner) {
        if (banner.target == "estimation") {
            updateEstimation(log = true) { s -> s.copy(estimationItems = s.estimationItems.mapIndexed { j, it -> if (j == banner.itemIndex) it.copy(rows = banner.rows) else it }) }
        } else {
            updateItem(banner.itemIndex) { it.copy(rows = banner.rows.ifEmpty { listOf(Row.blank()) }) }
        }
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

    /* ---------- Feature I: door / window builder ---------- */

    private fun updateStructures(change: (List<DwStructure>) -> List<DwStructure>) =
        update { s -> s.copy(doorWindows = change(s.doorWindows.orEmpty()).ifEmpty { null }) }

    fun addStructure(kind: String) = updateStructures { it + DwStructure(id = uid(), kind = kind) }
    fun updateStructure(id: String, change: (DwStructure) -> DwStructure) = updateStructures { l -> l.map { if (it.id == id) change(it) else it } }
    fun removeStructure(id: String) = updateStructures { l -> l.filter { it.id != id } }
    fun duplicateStructure(id: String) = updateStructures { l ->
        l.flatMap { if (it.id == id) listOf(it, it.copy(id = uid(), name = (it.name.ifBlank { QeDoorWindow.kindLabel(it.kind) }) + " (copy)")) else listOf(it) }
    }
    fun setDwWastage(v: String) = update { it.copy(dwWastage = v.ifBlank { null }) }

    private fun persistKv(key: String, json: String) { viewModelScope.launch { runCatching { db.keyValues().put(KeyValueEntity(key, json)) } } }

    fun setDwDefaults(d: DwDefaults) {
        _dwDefaults.value = d
        persistKv(StorageKeys.DW_DEFAULTS, QeJson.encodeToString(DwDefaults.serializer(), d))
    }

    /** Save a structure as a reusable template (by name; same name replaces). */
    fun saveDwTemplate(id: String, name: String): String {
        val st = _workspace.value.doorWindows.orEmpty().firstOrNull { it.id == id } ?: return ""
        val n = name.trim().ifEmpty { st.name.ifBlank { QeDoorWindow.kindLabel(st.kind) } }
        val t = st.copy(id = "", name = n)
        _dwTemplates.value = _dwTemplates.value.filter { it.name != n } + t
        persistKv(StorageKeys.DW_TEMPLATES, QeJson.encodeToString(ListSerializer(DwStructure.serializer()), _dwTemplates.value))
        return "Template saved: $n"
    }
    fun useDwTemplate(i: Int) { _dwTemplates.value.getOrNull(i)?.let { t -> updateStructures { it + t.copy(id = uid()) } } }
    fun deleteDwTemplate(i: Int) {
        _dwTemplates.value = _dwTemplates.value.filterIndexed { j, _ -> j != i }
        persistKv(StorageKeys.DW_TEMPLATES, QeJson.encodeToString(ListSerializer(DwStructure.serializer()), _dwTemplates.value))
    }

    /** "Add to calculator": one editable CFT item per structure, appended after the calculator items (blank only item replaced). */
    fun addStructuresToCalculator(ids: List<String>): Int {
        val s = _workspace.value
        val items = s.doorWindows.orEmpty().filter { it.id in ids }.mapNotNull { st ->
            val r = QeDoorWindow.compute(st, s.dwWastage, _dwDefaults.value)
            if (r.pieces.isEmpty()) null else QeDoorWindow.toCalcItem(st, r).copy(category = com.moraswood.qande.logic.QeCategories.DW)
        }
        if (items.isEmpty()) return 0
        updateItems { cur ->
            val onlyBlank = cur.size == 1 && cur[0].name.isBlank() && cur[0].rows.none { it.hasData() }
            (if (onlyBlank) emptyList() else cur) + items
        }
        setTab("calculator")
        return items.size
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

    /* ---------- Quotation (renderQuotation / renderNotes handlers) ---------- */

    val ai = AiService(app)

    val savedNotes: StateFlow<List<String>> =
        db.savedNotes().observeAll().map { l -> l.map { it.text } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _aiStatus = MutableStateFlow<Map<Int, String>>(emptyMap())
    /** `.ai-status` line per quotation item (DOM-only in V10.1, cleared when the item list re-renders). */
    val aiStatus: StateFlow<Map<Int, String>> = _aiStatus.asStateFlow()
    private val _aiBusy = MutableStateFlow<Set<Int>>(emptySet())
    /** Items whose "✨ Improve with AI" button shows "Improving…" (disabled). */
    val aiBusy: StateFlow<Set<Int>> = _aiBusy.asStateFlow()
    private val _noteStatus = MutableStateFlow("")
    /** `#noteStatus` */
    val noteStatus: StateFlow<String> = _noteStatus.asStateFlow()

    /** renderQuotation() rebuilds the item DOM, which resets the AI status lines. */
    private fun rerenderQuote(change: (WorkspaceState) -> WorkspaceState) { _aiStatus.value = emptyMap(); update(change) }

    private fun updateQuoteItem(i: Int, change: (QuoteItem) -> QuoteItem) =
        update { s -> s.copy(quoteItems = s.quoteItems.mapIndexed { j, it -> if (j == i) change(it) else it }) }

    fun setCustomerName(v: String) = update { it.copy(customer = it.customer.copy(name = v)) }
    fun setCustomerLocation(v: String) = update { it.copy(customer = it.customer.copy(location = v)) }
    fun setQuoteField(i: Int, key: String, v: String) = updateQuoteItem(i) {
        when (key) { "description" -> it.copy(description = v); "unit" -> it.copy(unit = v); "qty" -> it.copy(qty = v); "unitPrice" -> it.copy(unitPrice = v); "species" -> it.copy(species = v.ifEmpty { null }); "area" -> it.copy(area = v.ifEmpty { null }); else -> it }
    }
    fun addQuoteItem() = rerenderQuote { it.copy(quoteItems = it.quoteItems + QuoteItem.blank()) }
    fun removeQuoteItem(i: Int) = rerenderQuote { s ->
        s.copy(quoteItems = if (s.quoteItems.size == 1) listOf(QuoteItem.blank()) else s.quoteItems.filterIndexed { j, _ -> j != i })
    }
    fun setDiscountType(v: String) = update { it.copy(quote = it.quote.copy(discountType = v)) }
    fun setDiscountValue(v: String) = update { it.copy(quote = it.quote.copy(discountValue = v)) }
    fun setGstPercent(v: String) = update { it.copy(quote = it.quote.copy(gstPercent = v)) }

    fun selectQuoteOption(i: Int) = rerenderQuote { QeQuote.activateOption(it, i) }
    fun addQuoteOption() = rerenderQuote(QeQuote::addOption)
    fun duplicateQuoteOption() = rerenderQuote(QeQuote::duplicateOption)
    /** `#removeQuoteOption`: returns the alert text when only one option is left, otherwise null (caller confirms first). */
    fun canRemoveQuoteOption(): String? = if (QeQuote.syncActiveOption(_workspace.value).quoteOptions.size <= 1) "At least one quotation option is required." else null
    fun removeQuoteOption() = rerenderQuote(QeQuote::removeOption)
    /** Feature D: duplicate the whole active option as a wood-species option; returns the status line. */
    fun duplicateAsSpecies(species: String, cftRate: String, kolRate: String): String {
        var msg = ""
        rerenderQuote { s -> QeSpecies.duplicateAsSpecies(s, species, cftRate, kolRate).also { msg = QeSpecies.duplicateMessage(it, species) }.state }
        return msg
    }
    fun setQuoteOptionSpecies(v: String) = update { s0 ->
        val s = QeQuote.ensureQuoteOptions(s0)
        s.copy(quoteOptions = s.quoteOptions.mapIndexed { i, o -> if (i == s.activeQuoteOption) o.copy(species = v.ifEmpty { null }) else o })
    }
    fun setPrintComparison(on: Boolean) = update { it.copy(printComparison = if (on) true else null) }

    /** Feature G: "Show area on print". */
    fun setPrintArea(on: Boolean) = update { it.copy(printArea = if (on) true else null) }

    fun setQuoteOptionName(v: String) = update { s0 ->
        val s = QeQuote.ensureQuoteOptions(s0)
        s.copy(quoteOptions = s.quoteOptions.mapIndexed { i, o -> if (i == s.activeQuoteOption) o.copy(name = v) else o })
    }

    /** `.ai-improve` click. */
    fun improveDescription(i: Int, style: String) {
        val item = _workspace.value.quoteItems.getOrNull(i) ?: return
        fun status(msg: String) { _aiStatus.value = _aiStatus.value + (i to msg) }
        val original = item.description
        if (original.trim().isEmpty()) { status("Write a description first."); return }
        if (!ai.hasApiKey()) { status("Add an AI API key in Settings to use this feature."); return }
        _aiBusy.value = _aiBusy.value + i
        status("AI is rewriting the description.")
        viewModelScope.launch {
            try {
                val improved = ai.improveDescription(original, style)
                updateQuoteItem(i) { it.copy(aiOriginal = original, description = improved) }
                status("Updated. You can edit it further or undo.")
            } catch (e: Exception) {
                status(e.message ?: "Could not improve the description.")
            } finally {
                _aiBusy.value = _aiBusy.value - i
            }
        }
    }

    /** `.ai-undo` click. */
    fun undoAi(i: Int) {
        val original = _workspace.value.quoteItems.getOrNull(i)?.aiOriginal.orEmpty()
        if (original.isEmpty()) return
        updateQuoteItem(i) { it.copy(description = original, aiOriginal = "") }
        _aiStatus.value = _aiStatus.value + (i to "Original description restored.")
    }

    /** `.q-images` change: returns the V10.1 alert text. */
    suspend fun addQuoteImages(i: Int, uris: List<android.net.Uri>): String {
        val ctx = getApplication<Application>()
        if (_workspace.value.quoteItems.getOrNull(i) == null) return "Could not identify quotation item."
        val files = uris.filter { QuoteImages.isImage(ctx, it) }.take(QuoteImages.MAX_IMAGES)
        if (files.isEmpty()) return "Select image files to attach."
        var added = 0
        for (f in files) {
            if ((_workspace.value.quoteItems.getOrNull(i)?.images?.size ?: QuoteImages.MAX_IMAGES) >= QuoteImages.MAX_IMAGES) break
            val src = QuoteImages.resize(ctx, f)
            if (src != null && src.startsWith("data:image/")) { updateQuoteItem(i) { it.copy(images = it.images + src) }; added++ }
        }
        if (added == 0) return "No images were added. Please try a JPG or PNG photo."
        _aiStatus.value = emptyMap()
        return "$added image(s) added and saved to this quotation."
    }

    fun removeQuoteImage(i: Int, j: Int) = rerenderQuote { s ->
        s.copy(quoteItems = s.quoteItems.mapIndexed { k, it -> if (k == i) it.copy(images = it.images.filterIndexed { n, _ -> n != j }) else it })
    }

    /* notes */
    fun addNote() = update { it.copy(quote = it.quote.copy(notes = it.quote.notes + "")) }
    fun setNote(i: Int, v: String) = update { s -> s.copy(quote = s.quote.copy(notes = s.quote.notes.mapIndexed { j, n -> if (j == i) v else n })) }
    fun removeNote(i: Int) = update { s ->
        s.copy(quote = s.quote.copy(notes = if (s.quote.notes.size == 1) listOf("") else s.quote.notes.filterIndexed { j, _ -> j != i }))
    }
    fun saveNote(i: Int) {
        val note = _workspace.value.quote.notes.getOrNull(i).orEmpty().trim()
        if (note.isEmpty()) return
        viewModelScope.launch {
            val list = db.savedNotes().all().map { it.text }
            if (list.none { it.lowercase() == note.lowercase() }) db.savedNotes().replaceAll(list + note)
            _noteStatus.value = "Note saved for future quotations."
        }
    }
    fun addSavedNote(idx: Int) {
        val note = savedNotes.value.getOrNull(idx) ?: return
        update { s ->
            val notes = s.quote.notes
            s.copy(quote = s.quote.copy(notes = if (notes.size == 1 && notes[0].trim().isEmpty()) listOf(note) else notes + note))
        }
    }
    fun deleteSavedNote(idx: Int) {
        viewModelScope.launch {
            val list = db.savedNotes().all().map { it.text }
            if (idx !in list.indices) return@launch
            db.savedNotes().replaceAll(list.filterIndexed { j, _ -> j != idx })
            _noteStatus.value = "Saved note deleted."
        }
    }

    /** Native addition: edit a saved note from the Quotation tab (in place, no history). */
    fun editSavedNote(idx: Int, text0: String) {
        val text = text0.trim()
        if (text.isEmpty()) { _noteStatus.value = "A saved note cannot be empty."; return }
        viewModelScope.launch {
            val list = db.savedNotes().all().map { it.text }
            if (idx !in list.indices) return@launch
            db.savedNotes().replaceAll(list.mapIndexed { j, n -> if (j == idx) text else n })
            _noteStatus.value = "Saved note updated."
        }
    }

    /* Saved Notes screen (`#notesView`, renderNotesManager) */
    private val _notesManagerStatus = MutableStateFlow("")
    /** `#notesManagerStatus` */
    val notesManagerStatus: StateFlow<String> = _notesManagerStatus.asStateFlow()
    fun clearNotesManagerStatus() { _notesManagerStatus.value = "" }

    /** `#createSavedNote`; returns true when saved (the caller clears `#newSavedNote`). */
    suspend fun createSavedNote(text0: String): Boolean {
        val text = text0.trim()
        if (text.isEmpty()) { _notesManagerStatus.value = "Enter a note before saving."; return false }
        val list = db.savedNotes().all().map { it.text }
        if (list.any { it.lowercase() == text.lowercase() }) { _notesManagerStatus.value = "That note is already saved."; return false }
        return try {
            db.savedNotes().replaceAll(list + text); _notesManagerStatus.value = "Note saved."; true
        } catch (e: Exception) { _notesManagerStatus.value = e.message ?: "Could not save the note."; false }
    }

    /** `.update-saved-note` */
    fun updateSavedNote(idx: Int, text0: String) {
        val text = text0.trim()
        if (text.isEmpty()) { _notesManagerStatus.value = "A saved note cannot be empty."; return }
        viewModelScope.launch {
            val list = db.savedNotes().all().map { it.text }
            if (idx !in list.indices) return@launch
            db.savedNotes().replaceAll(list.mapIndexed { j, n -> if (j == idx) text else n })
            _notesManagerStatus.value = "Note updated."
        }
    }

    /** `.delete-saved-note` (the native screen asks to confirm first). */
    fun deleteSavedNoteFromManager(idx: Int) {
        viewModelScope.launch {
            val list = db.savedNotes().all().map { it.text }
            if (idx !in list.indices) return@launch
            db.savedNotes().replaceAll(list.filterIndexed { j, _ -> j != idx })
            _notesManagerStatus.value = "Note deleted."
        }
    }

    /** `#exportToProjects`: stores the pending import for Operations; returns an error alert or null. */
    suspend fun exportToProjects(): String? = try {
        update(QeQuote::syncActiveOption)
        val s = _workspace.value
        val q = s.quoteOptions[s.activeQuoteOption]
        val total = QeQuote.totalsForOption(q).grand
        val payload = buildJsonObject {
            put("name", s.projectName.ifEmpty { "Quotation " + QeQuote.shortDate() })
            put("customer", s.customer.name.ifEmpty { "Customer not specified" })
            put("amount", if (total == Math.floor(total) && Math.abs(total) < 1e15) JsonPrimitive(total.toLong()) else JsonPrimitive(total))
            put("notes", "Imported from Q&E quotation option: " + q.name.ifEmpty { "Option 1" })
            put("quotation", buildJsonObject {
                put("items", JsonArray(q.items.map { buildJsonObject { put("description", it.description); put("unit", it.unit); put("qty", it.qty); put("unitPrice", it.unitPrice) } }))
                put("option", q.name); put("discountType", q.discountType); put("discountValue", q.discountValue); put("gstPercent", q.gstPercent)
            })
            // Feature A (native addition, ignored by V10.1): item photos travel with the quotation into the project.
            put("photos", JsonArray(exportPhotos(s).map { (label, file) -> buildJsonObject { put("label", label); put("file", file) } }))
        }
        db.keyValues().put(KeyValueEntity(StorageKeys.PROJECT_IMPORT_PENDING, payload.toString()))
        null
    } catch (e: Exception) {
        "Could not export quotation: " + e.message
    }

    /** Photos of the estimation items (falling back to calculator items not sent to estimation), labelled by item name. */
    private fun exportPhotos(s: WorkspaceState): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()
        fun add(items: List<CalcItem>) = items.forEachIndexed { i, it ->
            if (it.photo.isNotEmpty() && it.photo !in out) out[it.photo] = it.name.trim().ifEmpty { "Item " + (i + 1) }
        }
        add(s.estimationItems); add(s.items)
        return out.map { (file, label) -> label to file }
    }

    /* ---------- Job Review ---------- */
    fun setActualMaterials(v: String) = update { it.copy(actual = it.actual.copy(materials = v)) }
    fun setActualLabourHours(v: String) = update { it.copy(actual = it.actual.copy(labourHours = v)) }

    /* ---------- Save Project / Saved Projects & Revisions (saveProject, renderSavedProjects) ---------- */

    private val _projectStatus = MutableStateFlow("")
    /** `#projectStatus` */
    val projectStatus: StateFlow<String> = _projectStatus.asStateFlow()
    /** app.js `loadedReadOnly` (not persisted, like V10.1). */
    private var loadedReadOnly = false

    fun saveProject() {
        val s0 = QeQuote.syncActiveOption(_workspace.value)
        val name = s0.projectName.ifEmpty { s0.customer.name }.trim()
        if (name.isEmpty()) { _projectStatus.value = "Enter a project name before saving."; return }
        if (loadedReadOnly) { _projectStatus.value = "Loaded revision is protected. Use Revise in Saved Projects to create a new revision."; return }
        val rootId = s0.projectId.ifEmpty { uid() }
        val s = s0.copy(projectName = name, projectId = rootId)
        update { s }
        // Template library: session logs now belong to the saved project id.
        val session = _sessionKey.value
        logJob?.cancel(); logJob = null
        viewModelScope.launch {
            runCatching { if (session.isNotEmpty() && session != rootId) db.templates().rekeyLogs(session, rootId) }
            writeLogs(_workspace.value)
        }
        val now = jsIsoNow()
        val basedOn = basedOnRevision
        viewModelScope.launch {
            try {
                // Feature E: R0 on the first save, then every save appends R(latest + 1); nothing is overwritten.
                val dao = db.savedProjects()
                val list = dao.all()
                val rev = QeRevisions.nextRevision(list, rootId, s.revision)
                val id = if (rev == 0 && list.none { it.id == rootId }) rootId else uid()
                update { it.copy(revision = rev) }
                val snapshot = QeJson.encodeToString(WorkspaceState.serializer(), _workspace.value)
                dao.insert(SavedProjectEntity(id, rootId, rev, name, now, snapshot, dao.minPosition() - 1, basedOn?.takeIf { it != rev }))
                if (dao.all().none { it.id == id && it.snapshotJson.isNotEmpty() }) error("Save verification failed")
                basedOnRevision = rev
                _projectStatus.value = "Saved $name · R$rev on this device."
            } catch (e: Exception) {
                _projectStatus.value = "Could not save project. Try fewer/lower-resolution images."
            }
        }
    }

    /** Feature E: revision the workspace was loaded from / last saved as (the next save records it as `basedOn`). */
    private var basedOnRevision: Int? = null

    private fun loadSnapshot(x: SavedProjectEntity, revision: Int, readOnly: Boolean): Boolean {
        val snap = runCatching { QeJson.decodeFromString(WorkspaceState.serializer(), x.snapshotJson).normalizeUnits() }.getOrNull() ?: return false
        logJob?.let { it.cancel(); flushLogs(_workspace.value) }
        _dismissed.value = emptySet(); _templateStatus.value = emptyMap(); _estStatus.value = emptyMap(); _aiStatus.value = emptyMap()
        update { snap.copy(projectId = x.rootId.ifEmpty { x.id }, revision = revision) }
        // A loaded project logs under its own id; unsaved work after this starts a fresh session.
        viewModelScope.launch { _sessionKey.value = newSessionKey() }
        loadedReadOnly = readOnly
        basedOnRevision = x.revision
        return true
    }

    /** Feature E: opening a job loads its latest revision, editable. */
    fun openLatestRevision(rootId: String): Boolean {
        val job = QeRevisions.jobs(savedProjects.value).firstOrNull { it.rootId == rootId } ?: return false
        val x = job.latest
        if (!loadSnapshot(x, x.revision, readOnly = false)) return false
        _projectStatus.value = "Editing ${QeRevisions.label(x.name, x.revision)} (latest). Saving creates R${x.revision + 1}."
        return true
    }

    /** Snapshot of a saved revision for printing without loading it. */
    fun savedSnapshot(id: String): WorkspaceState? = savedProjects.value.firstOrNull { it.id == id }?.let { x ->
        runCatching { QeJson.decodeFromString(WorkspaceState.serializer(), x.snapshotJson).normalizeUnits().copy(projectId = QeRevisions.root(x), revision = x.revision) }.getOrNull()
    }

    /** `.load-project` (View / Print). Returns true when the workspace should open. */
    fun viewSavedProject(id: String): Boolean {
        val x = savedProjects.value.firstOrNull { it.id == id } ?: return false
        val latest = QeRevisions.isLatest(savedProjects.value, x)
        // Feature E: only the latest revision is editable; older ones stay read-only (V10.1 View / Print was always read-only).
        if (!loadSnapshot(x, x.revision, readOnly = true)) return false
        _projectStatus.value = "Viewing R${x.revision}. Use Revise to make changes without altering this revision." +
            (if (latest) "" else " Only the latest revision can be edited.")
        return true
    }

    /** `.revise-project`. */
    fun reviseSavedProject(id: String): Boolean {
        val list = savedProjects.value
        val x = list.firstOrNull { it.id == id } ?: return false
        val root = x.rootId.ifEmpty { x.id }
        val latest = list.filter { it.rootId.ifEmpty { it.id } == root }.maxOf { it.revision }
        if (!loadSnapshot(x, latest + 1, readOnly = false)) return false
        _projectStatus.value = "Editing R${latest + 1} based on R${x.revision}. Save to preserve the new revision."
        return true
    }

    /** `.delete-project` after `confirm('Delete this saved revision?')`. */
    fun deleteSavedProject(id: String) {
        viewModelScope.launch {
            val dao = db.savedProjects()
            val all = dao.all()
            val gone = all.firstOrNull { it.id == id } ?: return@launch
            dao.deleteById(id)
            _projectStatus.value = "Saved revision deleted."
            // Feature E: revisions share photo files by path; delete only files nothing else references.
            val assetPhotos = runCatching { db.assets().assets().mapNotNull { it.photoFile }.toSet() }.getOrDefault(emptySet())
            QeRevisions.orphanedPhotos(gone, all, _workspace.value, assetPhotos).forEach { ItemPhotos.delete(getApplication(), db, it) }
        }
    }

    /* ---------- persistence ---------- */

    private fun update(change: (WorkspaceState) -> WorkspaceState) {
        // renderEstimation() side effect: missing costings are created and auto selling prices refreshed.
        // renderQuotation()/renderPrintSheet() side effects: ensureQuoteOptions() + syncActiveOption().
        _workspace.value = QeQuote.syncActiveOption(QeGroups.syncCostings(change(_workspace.value)))
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
        /** job_component_log itemIndex offset for Estimation-tab items. */
        const val ESTIMATION_LOG_OFFSET = 1000
        const val SELLING_MISSING = "Enter the estimated selling price before sending to Quotation."
        /** V10.1 quick-group colours (CSS named colours). */
        val QUICK_COLORS = listOf("red", "blue", "green", "orange", "purple")

        /** app.js `uid()`: 7 random base-36 characters. */
        /** JS `new Date().toISOString()`. */
        fun jsIsoNow(): String = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.now())

        fun uid(): String = (1..7).map { "0123456789abcdefghijklmnopqrstuvwxyz"[(Math.random() * 36).toInt()] }.joinToString("")
    }
}

/** `state.costings[i][key] = value` for the V10.1 est-input / est-unit / mode keys. */
fun Costing.with(key: String, value: String): Costing = when (key) {
    "cftRate" -> copy(cftRate = value); "kolRate" -> copy(kolRate = value)
    "labourMode" -> copy(labourMode = value); "labourLength" -> copy(labourLength = value)
    "labourLengthUnit" -> copy(labourLengthUnit = value); "labourWidth" -> copy(labourWidth = value)
    "labourWidthUnit" -> copy(labourWidthUnit = value); "labourQty" -> copy(labourQty = value)
    "labourRate" -> copy(labourRate = value); "labourLumpsum" -> copy(labourLumpsum = value)
    "polishMode" -> copy(polishMode = value); "polishRate" -> copy(polishRate = value)
    "polishLumpsum" -> copy(polishLumpsum = value); "polishAreaOverride" -> copy(polishAreaOverride = value)
    "consumables" -> copy(consumables = value); "other" -> copy(other = value)
    "sellingPrice" -> copy(sellingPrice = value)
    "areaOverride" -> copy(areaOverride = value.ifEmpty { null })
    else -> this
}

/** `row[key] = value` for the calculator keys used by V10.1. */
fun Row.with(key: String, value: String): Row = when (key) {
    "l" -> copy(l = value); "lUnit" -> copy(lUnit = value)
    "w" -> copy(w = value); "wUnit" -> copy(wUnit = value)
    "t" -> copy(t = value); "tUnit" -> copy(tUnit = value)
    "q" -> copy(q = value); "mode" -> copy(mode = value)
    else -> this
}
