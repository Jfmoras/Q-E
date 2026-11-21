package com.moraswood.qande.yieldtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.data.YieldEntryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Feature H — Yield Tracker entries (Room `yield_entries`). */
class YieldViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = QeDatabase.get(app).yields()
    val entries: StateFlow<List<YieldEntryEntity>> = dao.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Draft opened in the Yield Tracker editor (set by "Save & send to Yield Tracker" or Edit). */
    private val _editing = MutableStateFlow<YieldDraft?>(null)
    val editing: StateFlow<YieldDraft?> = _editing.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun edit(d: YieldDraft?) { _editing.value = d }

    /** Hoppus → Yield Tracker: saved straight away (output can be filled in later), then opened for editing. */
    fun sendFromHoppus(d: YieldDraft) {
        viewModelScope.launch {
            val id = runCatching { dao.insert(d.toEntity(System.currentTimeMillis())) }.getOrNull()
            if (id == null) { _status.value = "Could not save the yield entry."; return@launch }
            _editing.value = d.copy(id = id)
            _status.value = "Saved to Yield Tracker. Add the finished output when the job is done."
        }
    }

    fun save(d: YieldDraft) {
        viewModelScope.launch {
            runCatching {
                if (d.id == 0L) dao.insert(d.toEntity(System.currentTimeMillis()))
                else dao.update(d.toEntity(d.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
            }.onSuccess { _editing.value = null; _status.value = "Yield entry saved." }
                .onFailure { _status.value = "Could not save the yield entry." }
        }
    }

    fun delete(id: Long) { viewModelScope.launch { runCatching { dao.delete(id) }; if (_editing.value?.id == id) _editing.value = null } }
}
