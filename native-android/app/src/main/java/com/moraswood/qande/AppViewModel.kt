package com.moraswood.qande

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.StorageKeys
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.data.hasProjectWork
import com.moraswood.qande.data.normalizeUnits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state holder. Later milestones add calculator/estimation/quotation logic here. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val db = QeDatabase.get(app)

    private val _workspace = MutableStateFlow(WorkspaceState.default())
    /** Mirrors app.js `state`, loaded from `mwi-autosave-v1` (loadAutosave). */
    val workspace: StateFlow<WorkspaceState> = _workspace.asStateFlow()

    val savedProjects: StateFlow<List<SavedProjectEntity>> =
        db.savedProjects().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val json = db.keyValues().get(StorageKeys.AUTOSAVE)
            if (json != null) {
                runCatching { QeJson.decodeFromString(WorkspaceState.serializer(), json).normalizeUnits() }
                    .onSuccess { _workspace.value = it }
            }
        }
    }

    fun hasProjectWork(): Boolean = _workspace.value.hasProjectWork()

    fun setTab(tab: String) = update { it.copy(tab = tab) }
    fun setProjectName(name: String) = update { it.copy(projectName = name) }

    private fun update(change: (WorkspaceState) -> WorkspaceState) {
        _workspace.value = change(_workspace.value)
        saveAutosave()
    }

    /** app.js `saveAutosave()` — every edit persists the full state. */
    private fun saveAutosave() {
        val snapshot = _workspace.value
        viewModelScope.launch {
            runCatching {
                db.keyValues().put(com.moraswood.qande.data.KeyValueEntity(StorageKeys.AUTOSAVE, QeJson.encodeToString(WorkspaceState.serializer(), snapshot)))
            }
        }
    }
}
