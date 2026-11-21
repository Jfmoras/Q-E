package com.moraswood.qande.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.assets.AssetsViewModel
import com.moraswood.qande.assets.OverdueAlerts
import com.moraswood.qande.data.AssetCorrectionEntity
import com.moraswood.qande.logic.AssetLogic
import com.moraswood.qande.logic.AssetLogs
import com.moraswood.qande.logic.AssetState
import com.moraswood.qande.logic.AssetStatus
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.photos.ItemPhotos
import com.moraswood.qande.ui.components.QeCheckbox
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors
import kotlinx.coroutines.launch

/* Feature C status chips: green in stock, amber issued, red overdue. */
private val ChipGreen = Color(0xFF2E7D32)
private val ChipAmber = Color(0xFFF9A825)
private val ChipRed = Color(0xFFC62828)

@Composable
private fun StatusChip(s: AssetStatus) {
    val (bg, fg) = when (s) { AssetStatus.InStock -> ChipGreen to Color.White; AssetStatus.Issued -> ChipAmber to Color(0xFF1B1B1B); AssetStatus.Overdue -> ChipRed to Color.White }
    Text(s.label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
private fun Thumb(name: String?, sizeDp: Int) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    var bmp by remember(name) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) { bmp = if (name.isNullOrEmpty()) null else ItemPhotos.thumbnail(context, name, px) }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(sizeDp.dp).clip(shape).background(QeColors.InputBg), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = "Asset photo", contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        else Text(if (name.isNullOrEmpty()) "No photo" else "…", color = QeColors.Muted, fontSize = 11.sp)
    }
}

private fun categoryLabel(c: String) = c.replaceFirstChar { it.uppercase() }

/** Native addition (Feature C): asset registry, append-only issue / return history, owner review queue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetsSection(avm: AssetsViewModel) {
    val logs by avm.logs.collectAsState()
    val message by avm.message.collectAsState()
    val today = remember { avm.today() }
    val context = LocalContext.current
    H2("Assets")
    Small("Register each tool, machine and accessory once. Every issue and return is a permanent record: it cannot be edited or deleted. Mistakes are fixed with a correction request that only the owner can approve.")
    if (message.isNotEmpty()) Text(message, color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))

    // Overdue reminders need POST_NOTIFICATIONS on Android 13+.
    var canNotify by remember { mutableStateOf(OverdueAlerts.canNotify(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = OverdueAlerts.canNotify(context) }
    if (!canNotify) OpsCard {
        Small("Overdue reminders are off. The daily check runs on this phone only and names the overdue item and the worker.")
        if (Build.VERSION.SDK_INT >= 33) Row(Modifier.padding(top = 8.dp)) { QeSecondaryButton("Allow notifications", onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) }
        else Small("Turn on notifications for Q&E in Android settings.", Modifier.padding(top = 6.dp))
    }

    RegisterAssetCard(avm)
    val states = AssetLogic.states(logs, today)
    if (states.isEmpty()) Small("No assets registered yet.", Modifier.padding(vertical = 8.dp))
    states.forEach { AssetCard(it, logs, avm) }
    ReviewQueueCard(logs, avm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterAssetCard(avm: AssetsViewModel) {
    val scope = rememberCoroutineScope()
    val photo by avm.photo.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("tool") }
    var purchase by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var serial by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> val n = pending; pending = ""; if (n.isNotEmpty()) avm.photoCaptured(n, ok) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) avm.photoPicked(uri) }
    OpsCard {
        H3("Register asset")
        OpsGrid(listOf(
            { m -> OpsLabel("Name", m) { QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Bosch drill") } },
            { m -> OpsLabel("Category", m) { QeSelect(category, AssetLogic.CATEGORIES.map { it to categoryLabel(it) }, { category = it }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Purchase date", m) { QeTextField(purchase, { purchase = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") } },
            { m -> OpsLabel("Cost ₹", m) { QeNumberField(cost, { cost = it }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Serial number (optional)", m) { QeTextField(serial, { serial = it }, Modifier.fillMaxWidth()) } },
        ))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            Thumb(photo, 72)
            QeSecondaryButton(if (photo == null) "Take photo" else "Retake", onClick = { runCatching { val (n, uri) = avm.newCaptureTarget(); pending = n; camera.launch(uri) } })
            QeSecondaryButton(if (photo == null) "Choose photo" else "Replace", onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            if (photo != null) QeDangerButton("Remove photo", onClick = { avm.clearPhoto() })
        }
        Row {
            QePrimaryButton("Register asset", onClick = {
                scope.launch { if (avm.register(name, category, purchase, cost, serial)) { name = ""; purchase = ""; cost = ""; serial = "" } }
            })
        }
    }
}

@Composable
private fun AssetCard(s: AssetState, logs: AssetLogs, avm: AssetsViewModel) {
    val scope = rememberCoroutineScope()
    val a = s.asset
    var open by rememberSaveable(a.id) { mutableStateOf(false) }
    OpsCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (a.photoFile != null) Thumb(a.photoFile, 48)
            Column(Modifier.weight(1f)) {
                Text((if (open) "▾ " else "▸ ") + a.name, color = QeColors.Text, fontWeight = FontWeight.Bold)
                Small(listOfNotNull(categoryLabel(a.category), a.serial.ifBlank { null }?.let { "Serial: $it" },
                    a.purchaseDate.ifBlank { null }?.let { "Bought $it" }, if (a.cost > 0) "₹" + QeFormat.toFixed(a.cost, 2).removeSuffix(".00") else null).joinToString(" · "))
            }
            StatusChip(s.status)
        }
        val o = s.open
        when {
            o != null -> Small("Issued to ${o.worker} on ${o.issuedOn}" + (o.dueBack?.let { " · due back $it" } ?: " · no due-back date"), Modifier.padding(top = 6.dp))
            s.lastWorker != null -> Small("Last issued to ${s.lastWorker}", Modifier.padding(top = 6.dp))
        }
        if (!open) return@OpsCard
        val today = avm.today()
        if (o == null) {
            var worker by rememberSaveable(a.id, "w") { mutableStateOf("") }
            var issued by rememberSaveable(a.id, "i") { mutableStateOf(today.toString()) }
            var due by rememberSaveable(a.id, "d") { mutableStateOf(AssetLogic.defaultDueBack(today)) }
            var notes by rememberSaveable(a.id, "n") { mutableStateOf("") }
            H3("Issue to worker")
            OpsGrid(listOf(
                { m -> OpsLabel("Worker", m) { QeTextField(worker, { worker = it }, Modifier.fillMaxWidth()) } },
                { m -> OpsLabel("Date issued", m) { QeTextField(issued, { issued = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") } },
                { m -> OpsLabel("Due back (optional)", m) { QeTextField(due, { due = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") } },
                { m -> OpsLabel("Notes (optional)", m) { QeTextField(notes, { notes = it }, Modifier.fillMaxWidth()) } },
            ))
            Row { QePrimaryButton("Issue", onClick = { scope.launch { if (avm.issue(a.id, worker, issued, due, notes)) { worker = ""; notes = "" } } }) }
        } else {
            var returned by rememberSaveable(a.id, "r") { mutableStateOf(today.toString()) }
            var condition by rememberSaveable(a.id, "c") { mutableStateOf("") }
            H3("Record return")
            OpsGrid(listOf(
                { m -> OpsLabel("Date returned", m) { QeTextField(returned, { returned = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") } },
                { m -> OpsLabel("Condition notes", m) { QeTextField(condition, { condition = it }, Modifier.fillMaxWidth(), placeholder = "Good, blade worn, …") } },
            ))
            Row { QePrimaryButton("Record return", onClick = { scope.launch { if (avm.recordReturn(a.id, returned, condition)) condition = "" } }) }
        }
        Timeline(a.id, logs, avm)
    }
}

@Composable
private fun Timeline(assetId: Long, logs: AssetLogs, avm: AssetsViewModel) {
    val entries = AssetLogic.timeline(logs, assetId)
    var correcting by remember { mutableStateOf<Pair<String, Long>?>(null) }
    Details("History (${entries.size})") {
        if (entries.isEmpty()) Small("No history yet.")
        entries.forEach { e ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(e.date + " · " + e.kind, color = QeColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    textDecoration = if (e.struck) TextDecoration.LineThrough else null)
                Text(e.text + if (e.struck) " (voided by approved correction)" else "", color = QeColors.OpsMuted, fontSize = 13.sp,
                    textDecoration = if (e.struck) TextDecoration.LineThrough else null)
                if (e.targetType != null && e.targetId != null && !e.struck) {
                    val target = e.targetType to e.targetId
                    if (correcting == target) CorrectionForm(e.targetType, e.targetId, avm) { correcting = null }
                    else Row(Modifier.padding(top = 4.dp)) { QeSecondaryButton("Request correction", onClick = { correcting = target }) }
                }
            }
        }
    }
}

@Composable
private fun CorrectionForm(type: String, id: Long, avm: AssetsViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var worker by remember { mutableStateOf("") }
    var issued by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var noDue by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var returned by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var void by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    Column(Modifier.padding(top = 6.dp)) {
        Small("Fill only the fields that are wrong. The original entry stays in the history.")
        val cells = mutableListOf<@Composable (Modifier) -> Unit>()
        if (type == "issue") {
            cells += { m -> OpsLabel("Correct worker", m) { QeTextField(worker, { worker = it }, Modifier.fillMaxWidth()) } }
            cells += { m -> OpsLabel("Correct issue date", m) { QeTextField(issued, { issued = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") } }
            cells += { m -> OpsLabel("Correct due-back date", m) { QeTextField(due, { due = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") } }
            cells += { m -> OpsLabel("Correct notes", m) { QeTextField(notes, { notes = it }, Modifier.fillMaxWidth()) } }
        } else {
            cells += { m -> OpsLabel("Correct return date", m) { QeTextField(returned, { returned = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") } }
            cells += { m -> OpsLabel("Correct condition", m) { QeTextField(condition, { condition = it }, Modifier.fillMaxWidth()) } }
        }
        cells += { m -> OpsLabel("Reason (required)", m) { QeTextField(reason, { reason = it }, Modifier.fillMaxWidth()) } }
        OpsGrid(cells)
        if (type == "issue") Row(verticalAlignment = Alignment.CenterVertically) { QeCheckbox(noDue) { noDue = it }; Small("Remove the due-back date") }
        Row(verticalAlignment = Alignment.CenterVertically) { QeCheckbox(void) { void = it }; Small("Void this entry (recorded by mistake)") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            QePrimaryButton("Send for approval", onClick = {
                fun v(s: String) = s.trim().ifEmpty { null }
                val c = AssetCorrectionEntity(
                    targetType = type, targetId = id, worker = v(worker), issuedOn = v(issued), dueBack = if (noDue) null else v(due), clearDueBack = noDue,
                    notes = v(notes), returnedOn = v(returned), condition = v(condition), voidEntry = void, reason = reason, requestedAt = "",
                )
                scope.launch { if (avm.requestCorrection(c)) onDone() }
            })
            QeSecondaryButton("Cancel", onClick = onDone)
        }
    }
}

@Composable
private fun ReviewQueueCard(logs: AssetLogs, avm: AssetsViewModel) {
    val unlocked by avm.unlocked.collectAsState()
    val hasPin by avm.hasPin.collectAsState()
    val pending = AssetLogic.pending(logs)
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val pinKeyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    OpsCard {
        H3("Owner review queue (${pending.size} pending)")
        when {
            hasPin == false -> {
                Small("Set a 4–8 digit owner PIN. Only the PIN can approve or reject corrections. It is stored as a salted hash on this phone and cannot be recovered.")
                OpsGrid(listOf(
                    { m -> OpsLabel("New owner PIN", m) { QeTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), keyboardOptions = pinKeyboard, visualTransformation = PasswordVisualTransformation()) } },
                    { m -> OpsLabel("Confirm PIN", m) { QeTextField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), keyboardOptions = pinKeyboard, visualTransformation = PasswordVisualTransformation()) } },
                ))
                Row { QePrimaryButton("Set owner PIN", onClick = { avm.setPin(pin, pin2); pin = ""; pin2 = "" }) }
            }
            !unlocked -> {
                Small("Requests are visible to the owner only.")
                OpsLabel("Owner PIN") { QeTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), keyboardOptions = pinKeyboard, visualTransformation = PasswordVisualTransformation()) }
                Row { QePrimaryButton("Unlock", onClick = { avm.unlock(pin); pin = "" }) }
            }
            else -> {
                if (pending.isEmpty()) Small("No pending requests.")
                pending.forEach { c ->
                    val assetName = when (c.targetType) {
                        "issue" -> logs.issues.firstOrNull { it.id == c.targetId }?.assetId
                        else -> logs.returns.firstOrNull { it.id == c.targetId }?.let { r -> logs.issues.firstOrNull { it.id == r.issueId }?.assetId }
                    }?.let { aid -> logs.assets.firstOrNull { it.id == aid }?.name } ?: "Unknown asset"
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("$assetName · ${c.targetType} #${c.targetId}", color = QeColors.Text, fontWeight = FontWeight.Bold)
                        Small(AssetLogic.describe(c))
                        Small("Reason: ${c.reason} · requested ${c.requestedAt.take(10)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            QePrimaryButton("Approve", onClick = { avm.decide(c.id, true) })
                            QeDangerButton("Reject", onClick = { avm.decide(c.id, false) })
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp)) { QeSecondaryButton("Lock", onClick = { avm.lock() }) }
            }
        }
    }
}
