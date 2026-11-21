package com.moraswood.qande.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.HoppusState
import com.moraswood.qande.data.Row as MRow
import com.moraswood.qande.data.YieldEntryEntity
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeYield
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.QeCard
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QeFieldLabel
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors
import com.moraswood.qande.yieldtracker.YieldDraft
import com.moraswood.qande.yieldtracker.YieldViewModel

/** Feature H — "Log Calculator": Hoppus (unchanged V10.1 sheet) + Yield Tracker. */
@Composable
fun LogCalculatorScreen(logs: HoppusState, actions: HoppusActions, yvm: YieldViewModel) {
    var tab by rememberSaveable { mutableStateOf("hoppus") }
    var send by remember { mutableStateOf<YieldDraft?>(null) }
    val tabs: @Composable () -> Unit = {
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hoppus" to "Hoppus", "yield" to "Yield Tracker").forEach { (id, label) ->
                if (tab == id) QePrimaryButton(label) {} else QeSecondaryButton(label) { tab = id }
            }
        }
    }
    send?.let { d ->
        SendToYieldDialog(d, onDismiss = { send = null }) { ok -> send = null; yvm.sendFromHoppus(ok); tab = "yield" }
    }
    if (tab == "hoppus") {
        HoppusScreen(logs, actions, top = tabs, onSendToYield = {
            send = YieldDraft(inputVolume = QeFormat.toFixed(QeYield.hoppusTotal(logs), 2), logDetails = QeYield.hoppusDetails(logs))
        })
    } else ToolPage { tabs(); YieldTracker(yvm) }
}

@Composable
private fun JobTypeField(value: String, onChange: (String) -> Unit, known: List<String>) {
    QeFieldLabel("Job type") { QeTextField(value, onChange, Modifier.fillMaxWidth(), placeholder = "e.g. Doors") }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        (QeYield.JOB_TYPES + known).distinct().filter { it.isNotBlank() && it != value }.take(12).forEach { s ->
            Text(s, color = QeColors.Text, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(QeColors.Soft)
                .border(1.dp, QeColors.Line, RoundedCornerShape(12.dp)).clickable { onChange(s) }.padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun SendToYieldDialog(d0: YieldDraft, onDismiss: () -> Unit, onOk: (YieldDraft) -> Unit) {
    var d by remember { mutableStateOf(d0) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QeColors.Card,
        title = { Text("Save & send to Yield Tracker", color = QeColors.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QeFieldLabel("Raw volume", Modifier.weight(1f)) { QeNumberField(d.inputVolume, { d = d.copy(inputVolume = it) }, Modifier.fillMaxWidth()) }
                    QeFieldLabel("Unit", Modifier.weight(1f)) { QeSelect(d.inputUnit, QeYield.UNITS, { d = d.copy(inputUnit = it) }, Modifier.fillMaxWidth()) }
                }
                QeFieldLabel("Species") { QeTextField(d.species, { d = d.copy(species = it) }, Modifier.fillMaxWidth(), placeholder = "e.g. Teak") }
                JobTypeField(d.jobType, { d = d.copy(jobType = it) }, emptyList())
                QeFieldLabel("Date") { QeTextField(d.date, { d = d.copy(date = it) }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") }
                QeFieldLabel("Log details (optional)") { QeTextField(d.logDetails, { d = d.copy(logDetails = it) }, Modifier.fillMaxWidth(), singleLine = false, minLines = 3) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onOk(d) }) { Text("Save", color = QeColors.Accent) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = QeColors.Muted) } },
    )
}

@Composable
private fun YieldTracker(yvm: YieldViewModel) {
    val entries by yvm.entries.collectAsState()
    val editing by yvm.editing.collectAsState()
    val status by yvm.status.collectAsState()
    var confirm by remember { mutableStateOf<Long?>(null) }
    QeConfirm(confirm?.let { "Delete this yield entry?" }) { ok -> val id = confirm; confirm = null; if (ok && id != null) yvm.delete(id) }
    PageHeading("Yield Tracker", "Raw log volume in, finished timber out: yield %, loss % and where the waste went.")
    if (status.isNotEmpty()) Text(status, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
    val e = editing
    if (e != null) YieldEditor(e, entries.map { it.jobType }, onCancel = { yvm.edit(null) }, onSave = yvm::save)
    else QePrimaryButton("New yield entry", Modifier.padding(bottom = 12.dp)) { yvm.edit(YieldDraft()) }

    val points = QeYield.trend(entries)
    if (points.size >= 2) QeCard(Modifier.padding(bottom = 12.dp)) {
        Text("Yield trend", color = QeColors.Text, fontWeight = FontWeight.Bold)
        YieldTrend(points)
        Text("${points.first().first} → ${points.last().first} · latest ${QeYield.pct(points.last().second)}", color = QeColors.Muted, fontSize = 12.sp)
    }
    if (entries.isNotEmpty()) {
        AggregateCard("Average by species", QeYield.bySpecies(entries))
        AggregateCard("Average by job type", QeYield.byJobType(entries))
    }
    QeCard {
        Text("Entries", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        if (entries.isEmpty()) Text("No yield entries yet. Measure logs on the Hoppus tab and tap “Save & send to Yield Tracker”, or add one here.", color = QeColors.Muted)
        entries.forEach { x -> EntryRow(x, onEdit = { yvm.edit(YieldDraft.of(x)) }, onDelete = { confirm = x.id }) }
    }
}

@Composable
private fun EntryRow(x: YieldEntryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val input = QeYield.inputCft(x); val out = QeYield.outputCft(x)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(listOf(x.date, x.species.ifBlank { "—" }, x.jobType.ifBlank { "—" }).joinToString(" · "), color = QeColors.Text, fontWeight = FontWeight.Bold)
        Text("In ${QeFormat.toFixed(input, 2)} CFT → Out ${QeFormat.toFixed(out, 2)} CFT · Yield ${QeYield.pct(QeYield.yieldPct(input, out))} · Loss ${QeYield.pct(QeYield.lossPct(input, out))}", color = QeColors.Text, fontSize = 13.sp)
        Text("Waste: offcuts ${QeFormat.toFixed(x.offcuts, 2)} · breakage ${QeFormat.toFixed(x.breakage, 2)} · other ${QeFormat.toFixed(x.other, 2)} · unaccounted ${QeFormat.toFixed(QeYield.unaccounted(x), 2)} CFT", color = QeColors.Muted, fontSize = 12.sp)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QeSecondaryButton("Edit", onClick = onEdit); QeDangerButton("Delete", onClick = onDelete)
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(QeColors.Line))
    }
}

@Composable
private fun AggregateCard(title: String, rows: List<QeYield.Aggregate>) = QeCard(Modifier.padding(bottom = 12.dp)) {
    Text(title, color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
    Row { listOf("Group" to 1.4f, "Entries" to 0.7f, "In CFT" to 1f, "Out CFT" to 1f, "Yield" to 0.9f, "Mean" to 0.9f).forEach { (h, w) -> Text(h, color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(w)) } }
    rows.forEach { a ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Text(a.key, color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1.4f))
            Text("${a.count}", color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.weight(0.7f))
            Text(QeFormat.toFixed(a.input, 2), color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(QeFormat.toFixed(a.output, 2), color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(QeYield.pct(a.weightedYield), color = QeColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
            Text(QeYield.pct(a.meanYield), color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.weight(0.9f))
        }
    }
    Text("Yield = Σ output ÷ Σ input (volume-weighted); Mean = average of the entries' yield %.", color = QeColors.Muted, fontSize = 11.sp)
}

@Composable
private fun YieldTrend(points: List<Pair<String, Double>>) {
    val line = QeColors.Accent; val grid = QeColors.Line
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
        val ys = points.map { it.second }
        val lo = minOf(0.0, ys.min()); val hi = maxOf(100.0, ys.max())
        fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
        listOf(0.0, 50.0, 100.0).forEach { g -> drawLine(grid, Offset(0f, y(g)), Offset(size.width, y(g)), 1f) }
        val step = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p -> val o = Offset(i * step, y(p.second)); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, line, style = Stroke(width = 4f))
        points.forEachIndexed { i, p -> drawCircle(line, 6f, Offset(i * step, y(p.second))) }
    }
}

@Composable
private fun YieldEditor(d0: YieldDraft, knownJobTypes: List<String>, onCancel: () -> Unit, onSave: (YieldDraft) -> Unit) {
    var d by remember(d0) { mutableStateOf(d0) }
    val e = d.toEntity(0)
    val input = QeYield.inputCft(e); val out = QeYield.outputCft(e)
    QeCard(Modifier.padding(bottom = 12.dp)) {
        Text(if (d.id == 0L) "New yield entry" else "Edit yield entry", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        QeFieldLabel("Date") { QeTextField(d.date, { d = d.copy(date = it) }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD") }
        QeFieldLabel("Species") { QeTextField(d.species, { d = d.copy(species = it) }, Modifier.fillMaxWidth(), placeholder = "e.g. Teak") }
        JobTypeField(d.jobType, { d = d.copy(jobType = it) }, knownJobTypes)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QeFieldLabel("Raw log volume", Modifier.weight(1f)) { QeNumberField(d.inputVolume, { d = d.copy(inputVolume = it) }, Modifier.fillMaxWidth()) }
            QeFieldLabel("Unit", Modifier.weight(1f)) { QeSelect(d.inputUnit, QeYield.UNITS, { d = d.copy(inputUnit = it) }, Modifier.fillMaxWidth()) }
        }
        QeFieldLabel("Finished output") {
            QeSelect(d.outputMode, listOf("direct" to "Enter CFT directly", "rows" to "From finished sizes (L × W × T × pieces)"), { d = d.copy(outputMode = it) }, Modifier.fillMaxWidth())
        }
        if (d.outputMode == "rows") {
            d.outputRows.forEachIndexed { i, r ->
                fun set(nr: MRow) { d = d.copy(outputRows = d.outputRows.mapIndexed { j, x -> if (j == i) nr else x }) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 6.dp)) {
                    QeFieldLabel("L ft", Modifier.weight(1f)) { QeNumberField(r.l, { set(r.copy(l = it)) }, Modifier.fillMaxWidth(), compact = true) }
                    QeFieldLabel("W in", Modifier.weight(1f)) { QeNumberField(r.w, { set(r.copy(w = it)) }, Modifier.fillMaxWidth(), compact = true) }
                    QeFieldLabel("T in", Modifier.weight(1f)) { QeNumberField(r.t, { set(r.copy(t = it)) }, Modifier.fillMaxWidth(), compact = true) }
                    QeFieldLabel("Pcs", Modifier.weight(0.8f)) { QeNumberField(r.q, { set(r.copy(q = it)) }, Modifier.fillMaxWidth(), compact = true) }
                    QeSecondaryButton("✕", Modifier.width(48.dp)) { d = d.copy(outputRows = d.outputRows.filterIndexed { j, _ -> j != i }.ifEmpty { listOf(MRow.blank()) }) }
                }
            }
            QeSecondaryButton("Add size", Modifier.padding(bottom = 8.dp)) { d = d.copy(outputRows = d.outputRows + MRow.blank()) }
        } else QeFieldLabel("Output CFT") { QeNumberField(d.outputVolume, { d = d.copy(outputVolume = it) }, Modifier.fillMaxWidth()) }
        Text("Waste breakdown (CFT)", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QeFieldLabel("Offcuts", Modifier.weight(1f)) { QeNumberField(d.offcuts, { d = d.copy(offcuts = it) }, Modifier.fillMaxWidth()) }
            QeFieldLabel("Breakage", Modifier.weight(1f)) { QeNumberField(d.breakage, { d = d.copy(breakage = it) }, Modifier.fillMaxWidth()) }
            QeFieldLabel("Other", Modifier.weight(1f)) { QeNumberField(d.other, { d = d.copy(other = it) }, Modifier.fillMaxWidth()) }
        }
        QeFieldLabel("Log details (optional)") { QeTextField(d.logDetails, { d = d.copy(logDetails = it) }, Modifier.fillMaxWidth(), singleLine = false, minLines = 2) }
        QeFieldLabel("Notes (optional)") { QeTextField(d.notes, { d = d.copy(notes = it) }, Modifier.fillMaxWidth(), singleLine = false, minLines = 2) }
        Text("Input ${QeFormat.toFixed(input, 2)} CFT · Output ${QeFormat.toFixed(out, 2)} CFT · Yield ${QeYield.pct(QeYield.yieldPct(input, out))} · Loss ${QeYield.pct(QeYield.lossPct(input, out))}",
            color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        Text("Recorded waste ${QeFormat.toFixed(QeYield.waste(e), 2)} CFT · unaccounted ${QeFormat.toFixed(QeYield.unaccounted(e), 2)} CFT", color = QeColors.Muted, fontSize = 12.sp)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QePrimaryButton("Save entry") { onSave(d) }
            QeSecondaryButton("Cancel", onClick = onCancel)
        }
    }
}
