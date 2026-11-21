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
private fun ToolPage(maxWidth: Int = 1120, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Column(Modifier.widthIn(max = maxWidth.dp).fillMaxWidth()) { content() } }
}

/** CSS named colours used by the V10.1 quick groups. */
private fun cssColor(name: String): Color = when (name) {
    "red" -> Color(0xFFFF0000); "blue" -> Color(0xFF0000FF); "green" -> Color(0xFF008000)
    "orange" -> Color(0xFFFFA500); "purple" -> Color(0xFF800080); else -> QeColors.Line
}

class QuickActions(
    val onRate: (Boolean, String) -> Unit,
    val onField: (Int, String, String) -> Unit,
    val onAdd: () -> Unit,
    val onGroupRate: (String, String) -> Unit,
    val onUngroup: (String) -> Unit,
    /** Returns an alert message or null. */
    val onMakeGroup: (List<Int>) -> String?,
)

/** `#quickView` — drawQuick / calcQuick. */
@Composable
fun QuickCalcScreen(quick: QuickCalc, actions: QuickActions) = ToolPage {
    PageHeading("General CFT / KOL Calculator", "Independent of estimations. Length in feet, width and thickness in inches.")
    var alert by remember { mutableStateOf<String?>(null) }
    // Checkbox state lives in the DOM in V10.1 and is cleared by every drawQuick().
    var drawCount by remember { mutableStateOf(0) }
    val checked = remember(drawCount) { mutableListOf<Int>().toMutableStateList() }
    QeAlert(alert) { alert = null }
    QeCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        // #quickView .project-row: 2-column grid
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QeFieldLabel("Default ₹/CFT", Modifier.weight(1f)) { QeNumberField(quick.cftRate, { actions.onRate(true, it) }, Modifier.fillMaxWidth()) }
                QeFieldLabel("Default ₹/KOL", Modifier.weight(1f)) { QeNumberField(quick.kolRate, { actions.onRate(false, it) }, Modifier.fillMaxWidth()) }
            }
            QeSecondaryButton("Add row") { actions.onAdd(); drawCount++ }
        }
        var cft = 0.0; var kol = 0.0; var cost = 0.0
        quick.rows.forEachIndexed { i, r ->
            val x = QeMath.calcRow(r)
            if (x.mode == "CFT") cft += x.value else kol += x.value
            val g = quick.groups.firstOrNull { i in it.rows && it.mode == x.mode }
            val rate = g?.rate ?: if (x.mode == "CFT") quick.cftRate else quick.kolRate
            cost += x.value * QeMath.num(rate)
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QeCheckbox(i in checked) { on -> if (on) checked.add(i) else checked.remove(i) }
                        Text("Row ${i + 1}", color = QeColors.Text, fontSize = 13.sp)
                    }
                    listOf("l" to "Length (ft)", "w" to "Width (in)", "t" to "Thickness (in)", "q" to "Pieces").chunked(2).forEach { pair ->
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (k, label) ->
                                val v = when (k) { "l" -> r.l; "w" -> r.w; "t" -> r.t; else -> r.q }
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = QeColors.Text, fontSize = 13.sp)
                                    QeNumberField(v, { actions.onField(i, k, it) }, Modifier.fillMaxWidth().padding(top = 5.dp))
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Volume unit", color = QeColors.Text, fontSize = 13.sp)
                            QeSelect(r.mode, listOf("CFT" to "CFT", "KOL" to "KOL"), { actions.onField(i, "mode", it) }, Modifier.fillMaxWidth().padding(top = 5.dp))
                        }
                        Box(Modifier.weight(1f))
                    }
                    Text(
                        "${QeFormat.toFixed(x.value, 3)} ${x.mode} · ${QeFormat.money(x.value * QeMath.num(rate))}",
                        color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(QeColors.Line))
            }
        }
        // #quickGroups
        quick.groups.forEach { g ->
            val vol = g.rows.sumOf { i -> quick.rows.getOrNull(i)?.let { if (it.mode == g.mode) QeMath.calcRow(it).value else 0.0 } ?: 0.0 }
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(cssColor(g.color)))
                Column(Modifier.padding(7.dp)) {
                    Text("${g.color} · ${g.mode}", color = QeColors.Text, fontWeight = FontWeight.Bold)
                    QeFieldLabel("Rate ₹/${g.mode}") { QeNumberField(g.rate, { actions.onGroupRate(g.id, it) }, Modifier.fillMaxWidth()) }
                    Text(" · ${QeFormat.toFixed(vol, 3)} ${g.mode} · ${QeFormat.money(vol * QeMath.num(g.rate))}", color = QeColors.Text)
                    QeSecondaryButton("Ungroup", Modifier.padding(top = 4.dp)) { actions.onUngroup(g.id); drawCount++ }
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            QeSecondaryButton("Group checked rows") {
                val msg = actions.onMakeGroup(checked.sorted())
                if (msg != null) alert = msg else drawCount++
            }
            Text(
                "Total ${QeFormat.toFixed(cft, 3)} CFT; ${QeFormat.toFixed(kol, 3)} KOL | Cost ${QeFormat.money(cost)}",
                color = QeColors.Text, modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }
}

class HoppusActions(
    val onField: (Int, String, String) -> Unit,
    val onAdd: () -> Unit,
    val onRemove: (Int) -> Unit,
    val onSawRate: (String) -> Unit,
)

/** `#hoppusView` — drawLogs / calcLogs, Enter flow Length → Girth → next log. */
@Composable
fun HoppusScreen(logs: HoppusState, actions: HoppusActions) = ToolPage {
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
    }
}

/** `#notesView` */
@Composable
fun NotesScreen() = ToolPage(780) {
    PageHeading("Saved Notes", "Create and manage notes to reuse on quotations.")
    PortPendingCard("Settings (notes)")
}

/** `#settingsView` — the native Templates card is added at the end (least intrusive place). */
@Composable
fun SettingsScreen(templates: List<TemplateWithRows>, onDeleteTemplate: (Long) -> Unit) = ToolPage(780) {
    PageHeading("Settings", "Configure an AI-compatible API and convert length measurements.")
    PortPendingCard("Settings")
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
