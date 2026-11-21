package com.moraswood.qande.assets

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moraswood.qande.data.AssetCorrectionEntity
import com.moraswood.qande.data.AssetDecisionEntity
import com.moraswood.qande.data.AssetEntity
import com.moraswood.qande.data.AssetIssueEntity
import com.moraswood.qande.data.AssetReturnEntity
import com.moraswood.qande.data.KeyValueEntity
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.logic.AssetLogic
import com.moraswood.qande.logic.AssetLogs
import com.moraswood.qande.photos.ItemPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** Feature C state: logs from Room (insert-only), owner PIN gate for the review queue. */
class AssetsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = QeDatabase.get(app)
    private val dao = db.assets()
    private val ctx get() = getApplication<Application>()

    val logs: StateFlow<AssetLogs> = combine(dao.observeAssets(), dao.observeIssues(), dao.observeReturns(), dao.observeCorrections(), dao.observeDecisions()) { a, i, r, c, d ->
        AssetLogs(a, i, r, c, d)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetLogs())

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()
    fun msg(s: String) { _message.value = s }

    fun today(): LocalDate = LocalDate.now()
    private fun now() = Instant.now().toString()

    /* ---------- registry ---------- */
    private val _photo = MutableStateFlow<String?>(null)
    /** Photo picked for the asset being registered (file in the shared item photo store). */
    val photo: StateFlow<String?> = _photo.asStateFlow()
    fun newCaptureTarget(): Pair<String, Uri> = ItemPhotos.newCaptureTarget(ctx)
    fun photoCaptured(name: String, ok: Boolean) {
        if (ok && ItemPhotos.file(ctx, name).length() > 0) setPhoto(name) else ItemPhotos.file(ctx, name).delete()
    }
    fun photoPicked(uri: Uri) { viewModelScope.launch { ItemPhotos.importFrom(ctx, uri)?.let(::setPhoto) ?: msg("Could not read that photo.") } }
    private fun setPhoto(name: String) {
        val old = _photo.value
        _photo.value = name
        viewModelScope.launch { ItemPhotos.register(ctx, db, name); if (old != null && old != name) ItemPhotos.delete(ctx, db, old) }
    }
    fun clearPhoto() { _photo.value?.let { n -> viewModelScope.launch { ItemPhotos.delete(ctx, db, n) } }; _photo.value = null }

    /** Returns true when registered (the form clears). */
    suspend fun register(name: String, category: String, purchaseDate: String, cost: String, serial: String): Boolean {
        AssetLogic.validateAsset(name, category, purchaseDate, cost)?.let { msg(it); return false }
        dao.addAsset(AssetEntity(name = name.trim(), category = category, purchaseDate = purchaseDate.trim(), cost = cost.trim().toDoubleOrNull() ?: 0.0,
            serial = serial.trim(), photoFile = _photo.value, createdAt = now()))
        _photo.value = null
        msg("Asset registered: ${name.trim()}")
        return true
    }

    /* ---------- issue / return (append-only) ---------- */
    suspend fun issue(assetId: Long, worker: String, issuedOn: String, dueBack: String, notes: String): Boolean {
        val l = logs.value
        val a = l.assets.firstOrNull { it.id == assetId } ?: return false
        AssetLogic.validateIssue(l, a, worker, issuedOn, dueBack, today())?.let { msg(it); return false }
        dao.addIssue(AssetIssueEntity(assetId = assetId, worker = worker.trim(), issuedOn = issuedOn.trim(), dueBack = dueBack.trim().ifEmpty { null }, notes = notes.trim(), createdAt = now()))
        msg("${a.name} issued to ${worker.trim()}. This record is permanent.")
        return true
    }

    suspend fun recordReturn(assetId: Long, returnedOn: String, condition: String): Boolean {
        val l = logs.value
        val a = l.assets.firstOrNull { it.id == assetId } ?: return false
        val open = AssetLogic.state(l, a, today()).open
        AssetLogic.validateReturn(open, returnedOn)?.let { msg(it); return false }
        dao.addReturn(AssetReturnEntity(issueId = open!!.raw.id, returnedOn = returnedOn.trim(), condition = condition.trim(), createdAt = now()))
        msg("${a.name} returned. This record is permanent.")
        return true
    }

    /* ---------- corrections: anyone may request, only the owner decides ---------- */
    suspend fun requestCorrection(c: AssetCorrectionEntity): Boolean {
        val req = c.copy(id = 0, requestedAt = now(), reason = c.reason.trim())
        AssetLogic.validateCorrection(req)?.let { msg(it); return false }
        dao.addCorrection(req)
        msg("Correction request sent to the owner review queue. Nothing changes until it is approved.")
        return true
    }

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private val _hasPin = MutableStateFlow<Boolean?>(null)
    val hasPin: StateFlow<Boolean?> = _hasPin.asStateFlow()
    private var failures = 0
    private var lockedUntil = 0L

    init { viewModelScope.launch { _hasPin.value = pinRecord() != null } }

    private suspend fun pinRecord(): PinRecord? =
        db.keyValues().get(OwnerPin.KEY)?.let { runCatching { Json.decodeFromString(PinRecord.serializer(), it) }.getOrNull() }

    fun setPin(pin: String, confirm: String) {
        viewModelScope.launch {
            if (pinRecord() != null) return@launch msg("An owner PIN is already set.")
            if (!OwnerPin.valid(pin)) return@launch msg("PIN must be 4 to 8 digits.")
            if (pin != confirm) return@launch msg("The two PINs do not match.")
            val rec = withContext(Dispatchers.Default) { OwnerPin.create(pin) }
            db.keyValues().put(KeyValueEntity(OwnerPin.KEY, Json.encodeToString(PinRecord.serializer(), rec)))
            _hasPin.value = true; _unlocked.value = true
            msg("Owner PIN set. Review queue unlocked.")
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            val wait = lockedUntil - System.currentTimeMillis()
            if (wait > 0) return@launch msg("Too many wrong PINs. Try again in ${(wait / 1000) + 1} s.")
            val rec = pinRecord() ?: return@launch msg("Set an owner PIN first.")
            val ok = withContext(Dispatchers.Default) { OwnerPin.verify(pin, rec) }
            if (ok) { failures = 0; _unlocked.value = true; msg("Review queue unlocked.") }
            else {
                failures++
                if (failures >= 5) { lockedUntil = System.currentTimeMillis() + 30_000; failures = 0 }
                msg("Wrong PIN.")
            }
        }
    }

    fun lock() { _unlocked.value = false }

    fun decide(correctionId: Long, approve: Boolean) {
        viewModelScope.launch {
            if (!_unlocked.value) return@launch msg("Unlock the review queue with the owner PIN.")
            if (AssetLogic.decision(logs.value, correctionId) != null) return@launch
            runCatching { dao.addDecision(AssetDecisionEntity(correctionId = correctionId, decision = if (approve) "approved" else "rejected", decidedAt = now())) }
                .onSuccess { msg(if (approve) "Correction approved." else "Correction rejected.") }
                .onFailure { msg("This request was already decided.") }
        }
    }

    /** Leaving the page re-locks the queue and clears the message. */
    fun resetPage() { _message.value = ""; _unlocked.value = false }
}
