package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.components.QePrimaryButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.TemplateWithRows
import com.moraswood.qande.data.HoppusState
import com.moraswood.qande.data.QuickCalc
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.PortPendingCard
import com.moraswood.qande.ui.components.QeAlert
import com.moraswood.qande.ui.components.QeCard
import com.moraswood.qande.ui.components.QeCheckbox
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QeFieldLabel
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.theme.QeColors

@Composable
internal fun ToolPage(maxWidth: Int = 1120, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Column(Modifier.widthIn(max = maxWidth.dp).fillMaxWidth()) { content() } }
}

class HoppusActions(
    val onField: (Int, String, String) -> Unit,
    val onAdd: () -> Unit,
    val onRemove: (Int) -> Unit,
    val onSawRate: (String) -> Unit,
)

/** `#hoppusView` — drawLogs / calcLogs, Enter flow Length → Girth → next log. */
@Composable
fun HoppusScreen(logs: HoppusState, actions: HoppusActions, top: @Composable () -> Unit = {}, onSendToYield: (() -> Unit)? = null) = ToolPage {
    top()
    PageHeading("Hoppus Log Volume Calculator", "Quarter-girth method: girth² × length ÷ 2304, with girth in inches and length in feet.")
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun fr(i: Int, k: String) = requesters.getOrPut("$i:$k") { FocusRequester() }
    var pendingFocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingFocus, logs.rows.size) {
        val p = pendingFocus ?: return@LaunchedEffect
        if (p < logs.rows.size) { runCatching { fr(p, "length").requestFocus() }; pendingFocus = null }
    }
    QeCard {
        var total = 0.0
        logs.rows.forEachIndexed { i, r ->
            val volume = QeMath.hoppusVolume(r.length, r.girth)
            total += volume
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Log ${i + 1}", color = QeColors.Text, modifier = Modifier.width(48.dp).padding(bottom = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Length ft", color = QeColors.Text, fontSize = 12.sp)
                        QeNumberField(r.length, { actions.onField(i, "length", it) }, Modifier.fillMaxWidth(), focusRequester = fr(i, "length"), compact = true,
                            onEnter = { runCatching { fr(i, "girth").requestFocus() } })
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Girth in", color = QeColors.Text, fontSize = 12.sp)
                        QeNumberField(r.girth, { actions.onField(i, "girth", it) }, Modifier.fillMaxWidth(), focusRequester = fr(i, "girth"), compact = true,
                            onEnter = { if (i == logs.rows.size - 1) actions.onAdd(); pendingFocus = i + 1 })
                    }
                    Text("${QeFormat.toFixed(volume, 2)} CFT", color = QeColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(72.dp).padding(bottom = 10.dp))
                    QeSecondaryButton("Remove", Modifier.width(80.dp)) { actions.onRemove(i) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFDDDDDD)))
            }
        }
        QeSecondaryButton("Add log", Modifier.padding(vertical = 10.dp)) { actions.onAdd() }
        QeFieldLabel("Sawing rate ₹/CFT") { QeNumberField(logs.sawRate, actions.onSawRate, Modifier.fillMaxWidth()) }
        Text("Total ${QeFormat.toFixed(total, 2)} CFT | Sawing charges ${QeFormat.money(total * QeMath.num(logs.sawRate))}", color = QeColors.Text, modifier = Modifier.padding(vertical = 14.dp))
        // Feature H: hand the measured logs to the Yield Tracker
        if (onSendToYield != null) QePrimaryButton("Save & send to Yield Tracker", onClick = onSendToYield)
    }
}

/**
 * `#notesView` (renderNotesManager). V10.1 showed every note as an editable textarea with Update / Delete;
 * per Jason each note now reads as text with Edit (Save / Cancel, in place, no history) and Delete (with confirm).
 */
@Composable
fun NotesScreen(vm: com.moraswood.qande.AppViewModel) = ToolPage(780) {
    val notes by vm.savedNotes.collectAsState()
    val status by vm.notesManagerStatus.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.clearNotesManagerStatus() }
    var draft by remember { mutableStateOf("") }
    var editing by remember(notes) { mutableStateOf<Pair<Int, String>?>(null) }
    var confirm by remember { mutableStateOf<Int?>(null) }
    QeConfirm(confirm?.let { "Delete this saved note?" }) { ok ->
        val i = confirm
        confirm = null
        if (ok && i != null) vm.deleteSavedNoteFromManager(i)
    }
    PageHeading("Saved Notes", "Create and manage notes to reuse on quotations.")
    QeCard {
        QeFieldLabel("New note") { QeTextField(draft, { draft = it }, Modifier.fillMaxWidth(), placeholder = "Enter a note to reuse on quotations", singleLine = false, minLines = 3) }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QePrimaryButton("Save note", onClick = { scope.launch { if (vm.createSavedNote(draft)) draft = "" } })
            if (status.isNotEmpty()) Text(status, color = QeColors.Muted, fontSize = 14.sp)
        }
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (notes.isEmpty()) Text("No saved notes yet.", color = QeColors.Muted)
            notes.forEachIndexed { i, note ->
                val e = editing
                if (e != null && e.first == i) {
                    QeTextField(e.second, { editing = i to it }, Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QeSecondaryButton("Save", onClick = { vm.updateSavedNote(i, e.second); if (e.second.isNotBlank()) editing = null })
                        QeSecondaryButton("Cancel", onClick = { editing = null })
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note, color = QeColors.Text, modifier = Modifier.weight(1f))
                    QeSecondaryButton("Edit", onClick = { editing = i to note })
                    QeDangerButton("Delete", onClick = { confirm = i })
                }
            }
        }
    }
}

/** `#settingsView` — the native Templates card is added at the end (least intrusive place). */
@Composable
fun SettingsScreen(
    templates: List<TemplateWithRows>, onDeleteTemplate: (Long) -> Unit, svm: com.moraswood.qande.settings.SettingsViewModel,
    dwDefaults: com.moraswood.qande.data.DwDefaults = com.moraswood.qande.data.DwDefaults(), onDwDefaults: (com.moraswood.qande.data.DwDefaults) -> Unit = {},
) = ToolPage(780) {
    PageHeading("Settings", "Configure an AI-compatible API and convert length measurements.")
    SettingsCards(svm)
    BuilderDefaultsCard(dwDefaults, onDwDefaults)
    TemplatesCard(templates, onDeleteTemplate)
}

/** Native addition: browse and delete saved calculator templates. */
@Composable
private fun TemplatesCard(templates: List<TemplateWithRows>, onDelete: (Long) -> Unit) {
    var confirm by remember { mutableStateOf<TemplateWithRows?>(null) }
    QeConfirm(confirm?.let { "Delete the template “${it.template.name}”?" }) { ok ->
        val t = confirm
        confirm = null
        if (ok && t != null) onDelete(t.template.id)
    }
    QeCard(Modifier.padding(top = 18.dp)) {
        Text("Templates", color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Saved from the Timber Calculator with “Save as template”. Matching templates are suggested when you type the same item or furniture type.",
            color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        if (templates.isEmpty()) Text("No saved templates yet.", color = QeColors.Muted)
        templates.forEach { t ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(t.template.name, color = QeColors.Text, fontWeight = FontWeight.Bold)
                Text("Type: ${t.template.furnitureType} · ${t.rows.size} row${if (t.rows.size == 1) "" else "s"}", color = QeColors.Muted, fontSize = 12.sp)
                t.rows.sortedBy { it.position }.forEach { r ->
                    Text(
                        "${r.l} ${r.lUnit} × ${r.w} ${r.wUnit} × ${r.t} ${r.tUnit} × ${r.q} · ${r.mode}",
                        color = QeColors.Muted, fontSize = 12.sp,
                    )
                }
                QeDangerButton("Delete", Modifier.padding(top = 6.dp)) { confirm = t }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(QeColors.Line))
        }
    }
}
