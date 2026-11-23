package com.moraswood.qande.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.OpsAttachmentEntity
import com.moraswood.qande.data.PpCommentEntity
import com.moraswood.qande.data.PpHardwareEntity
import com.moraswood.qande.data.PpItemEntity
import com.moraswood.qande.data.PpRevisionEntity
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.logic.QeOps
import com.moraswood.qande.logic.QeProjects
import com.moraswood.qande.logic.QeRevisions
import com.moraswood.qande.ops.OperationsViewModel
import com.moraswood.qande.projects.PpView
import com.moraswood.qande.projects.ProjectsViewModel
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/* Dashboard palette: white rounded container, dark ink, MWI green accent. */
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1C1917)
private val Sub = Color(0xFF6B7280)
private val Rule = Color(0xFFE7E5E4)
private val Hero = Color(0xFF202629)
private val Green = Color(0xFFB8E47A)
private val Blue = Color(0xFF5B8DEF)
private val Grey = Color(0xFF9CA3AF)
private val Amber = Color(0xFFF5A524)

private fun statusColor(s: String) = when (s) { QeProjects.FINISHED -> Blue; QeProjects.DELETED -> Grey; else -> Green }
private fun day(at: String) = at.take(10)
private fun plain(d: Double) = if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

class ProjectsCallbacks(
    val onHome: () -> Unit,
    /** Open the linked job's latest revision on the Quotation tab (review / add a revision via Save Project). */
    val onOpenLatest: (String) -> Unit,
    val onViewRevision: (String) -> Unit,
    val onPrintRevision: (String) -> Unit,
)

private enum class Pane { Dashboard, All, Payments }

/** Home › Projects and Payments. */
@Composable
fun ProjectsDashboardScreen(pvm: ProjectsViewModel, ovm: OperationsViewModel, saved: List<SavedProjectEntity>, cb: ProjectsCallbacks, openId: String? = null) {
    val list by pvm.projects.collectAsState()
    val message by pvm.message.collectAsState()
    var selected by rememberSaveable { mutableStateOf(openId) }
    var pane by rememberSaveable { mutableStateOf(Pane.Dashboard) }
    var creating by remember { mutableStateOf(false) }
    val current = list.firstOrNull { it.project.id == selected }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream -> writeProjectWorkbook(stream, list) }
                    ?: error("Could not open the export destination")
            }.onSuccess { pvm.msg("Excel project payment report exported.") }
             .onFailure { pvm.msg("Excel export failed: ${it.message}") }
        }
    }

    ToolPage(820) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paper).padding(16.dp)) {
            if (current != null) ProjectScreen(current, pvm, ovm, saved, cb) { selected = null }
            else {
                // header: title + avatar
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Projects & Payments", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Moras Wood Industries", color = Sub, fontSize = 13.sp)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Hero), contentAlignment = Alignment.Center) { Text("MW", color = Green, fontWeight = FontWeight.Bold) }
                }
                if (message.isNotEmpty()) Text(message, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                when (pane) {
                    Pane.Dashboard -> {
                        SummaryCard(list)
                        Spacer(Modifier.height(12.dp))
                        QeSecondaryButton("Export payment report to Excel (.xlsx)") {
                            exportLauncher.launch("MWI_Project_Payments_${LocalDate.now()}.xlsx")
                        }
                        Spacer(Modifier.height(16.dp))
                        DashboardBreakdown(list)
                        Spacer(Modifier.height(18.dp))
                        ProjectList(list, limit = 5, onSeeAll = { pane = Pane.All }) { selected = it }
                    }
                    Pane.All -> ProjectList(list, limit = null, onSeeAll = null) { selected = it }
                    Pane.Payments -> RecentPayments(list) { selected = it }
                }
                Spacer(Modifier.height(18.dp))
                BottomBar(pane, { pane = it }, onPlus = { creating = true }, onHome = cb.onHome)
            }
        }
    }
    if (creating) NewProjectDialog(onDismiss = { creating = false }) { c, n, d, v, s -> pvm.create(c, n, d, v, s) { id -> creating = false; selected = id } }
}

@Composable
private fun SummaryCard(list: List<PpView>) {
    val month = YearMonth.now()
    val s = QeProjects.summary(list.map { it.project }, { id -> list.firstOrNull { it.project.id == id }?.status ?: QeProjects.RUNNING }, month.toString())
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Hero).padding(18.dp)) {
        Text("Financial overview", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("All active and finished projects · ${s.running} running", color = Color(0xFFB4BFC1), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        DashboardMoney("Total project value", s.value, Green)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Total collected", color = Color(0xFFB4BFC1), fontSize = 12.sp)
                Text(QeOps.fmt(s.collected), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Total pending", color = Color(0xFFB4BFC1), fontSize = 12.sp)
                Text(QeOps.fmt(s.value - s.collected), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF3B4447))) {
                Box(Modifier.fillMaxWidth(s.pct / 100f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Green))
            }
            Text("${s.pct}% collected", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
        Text("${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}: ${QeOps.fmt(s.collectedThisMonth)} received",
            color = Color(0xFFB4BFC1), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DashboardMoney(label: String, value: Double, accent: Color) {
    Text(label, color = Color(0xFFB4BFC1), fontSize = 12.sp)
    Text(QeOps.fmt(value), color = accent, fontSize = 27.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun DashboardBreakdown(list: List<PpView>) {
    val live = list.filter { it.status != QeProjects.DELETED }
    val byMonth = live.flatMap { it.project.payments }
        .groupBy { it.at.take(7) }
        .toSortedMap()
        .entries.takeLast(6)
    Column(Modifier.fillMaxWidth()) {
        Text("Monthly collections", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        if (byMonth.isEmpty()) Text("No payments recorded.", color = Sub, fontSize = 13.sp)
        else {
            val max = byMonth.maxOf { it.value.sumOf { payment -> payment.amount } }.coerceAtLeast(1.0)
            byMonth.forEach { entry ->
                val collected = entry.value.sumOf { it.amount }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.key, color = Sub, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                    Box(Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEF1ED))) {
                        Box(Modifier.fillMaxWidth((collected / max).toFloat().coerceIn(0f, 1f)).height(13.dp).background(Color(0xFF8CBC4C)))
                    }
                    Text(QeOps.fmt(collected), color = Ink, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Project payment breakdown", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        if (live.isEmpty()) Text("No projects yet.", color = Sub, fontSize = 13.sp)
        live.sortedByDescending { it.money.remaining }.forEach { v ->
            val m = v.money
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(v.project.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${m.pct}%", color = Sub, fontSize = 12.sp)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE9ECE8))) {
                    Box(Modifier.fillMaxWidth(m.pct / 100f).height(8.dp).background(Color(0xFF8CBC4C)))
                }
                Text("Collected ${QeOps.fmt(m.paid)}  ·  Pending ${QeOps.fmt(m.remaining)}",
                    color = Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Produces a real two-sheet Excel workbook with no external Android dependencies. */
private fun writeProjectWorkbook(out: OutputStream, list: List<PpView>) {
    val live = list.filter { it.status != QeProjects.DELETED }
    val month = YearMonth.now().toString()
    val s = QeProjects.summary(list.map { it.project },
        { id -> list.firstOrNull { it.project.id == id }?.status ?: QeProjects.RUNNING }, month)
    val overview = mutableListOf<List<Any>>(
        listOf("Moras Wood Industries - Project Payment Report"),
        listOf("Export date", LocalDate.now().toString()),
        listOf("Total project value", s.value),
        listOf("Total collected", s.collected),
        listOf("Total pending", s.value - s.collected),
        listOf("Collected this month", s.collectedThisMonth),
        listOf("Collection percentage", s.pct),
        emptyList(),
        listOf("Project", "Customer", "Status", "Revision", "Project value", "Collected", "Pending", "Collected %", "Start date")
    )
    live.forEach { v ->
        overview.add(listOf(v.project.name, v.project.customer, QeProjects.statusLabel(v.status),
            v.revisionLabel, v.money.total, v.money.paid, v.money.remaining, v.money.pct, v.startDate))
    }
    val payments = mutableListOf<List<Any>>(listOf("Date", "Project", "Customer", "Amount", "Notes"))
    live.flatMap { v -> v.project.payments.map { p ->
        listOf<Any>(p.at.take(10), v.project.name, v.project.customer, p.amount, p.note)
    } }.sortedBy { it[0].toString() }.forEach { payments.add(it) }
    ZipOutputStream(out).use { zip ->
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Project summary" sheetId="1" r:id="rId1"/><sheet name="Payment transactions" sheetId="2" r:id="rId2"/></sheets></workbook>""")
        entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>""")
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 32 }
        fun xml(rows: List<List<Any>>): String = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { i, row ->
                append("<row r=\"${i + 1}\">")
                row.forEachIndexed { j, cell ->
                    var col = j + 1
                    var colName = ""
                    while (col > 0) { col--; colName = ('A' + (col % 26)).toString() + colName; col /= 26 }
                    append("<c r=\"$colName${i + 1}\"")
                    if (cell is Number) append("><v>${cell}</v></c>")
                    else append(" t=\"inlineStr\"><is><t>${esc(cell.toString())}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        entry("xl/worksheets/sheet1.xml", xml(overview))
        entry("xl/worksheets/sheet2.xml", xml(payments))
    }
}

@Composable
private fun ProjectList(list: List<PpView>, limit: Int?, onSeeAll: (() -> Unit)?, onOpen: (String) -> Unit) {
    val sec = QeProjects.sections(list, { it.status }, { it.project.created })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (limit == null) "All projects" else "Running projects", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (onSeeAll != null) Text("See All", color = Color(0xFF4D7C0F), fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onSeeAll).padding(6.dp))
    }
    val running = if (limit != null) sec.running.take(limit) else sec.running
    if (running.isEmpty()) Text("No running projects. Tap + to add one, or Confirm as Project from a saved quotation.", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
    running.forEach { ProjectRow(it, dim = false) { onOpen(it.project.id) } }
    if (limit == null || onSeeAll == null) {
        Fold("Finished (${sec.finished.size})", sec.finished) { ProjectRow(it, dim = true) { onOpen(it.project.id) } }
        Fold("Deleted (${sec.deleted.size})", sec.deleted) { ProjectRow(it, dim = true) { onOpen(it.project.id) } }
    } else if (sec.finished.isNotEmpty() || sec.deleted.isNotEmpty()) {
        Text("${sec.finished.size} finished · ${sec.deleted.size} deleted — See All", color = Sub, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onSeeAll).padding(top = 6.dp))
    }
}

@Composable
private fun <T> Fold(title: String, items: List<T>, row: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    var open by rememberSaveable(title.substringBefore(" ")) { mutableStateOf(false) }
    Text((if (open) "▾ " else "▸ ") + title, color = Sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(top = 14.dp, bottom = 6.dp))
    if (open) items.forEach { row(it) }
}

@Composable
private fun ProjectRow(v: PpView, dim: Boolean, onClick: () -> Unit) {
    val m = v.money
    Row(Modifier.fillMaxWidth().alpha(if (dim) 0.55f else 1f).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(statusColor(v.status).copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text(v.project.name.take(1).uppercase().ifEmpty { "P" }, color = if (v.status == QeProjects.RUNNING) Color(0xFF3F6212) else statusColor(v.status), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.project.name + "  · " + v.revisionLabel, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(v.meta.description, v.project.notes).firstOrNull { it.isNotBlank() } ?: v.project.customer
            Text(sub, color = Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(QeOps.fmt(m.total), color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(if (dim) QeProjects.statusLabel(v.status) else v.startDate, color = Sub, fontSize = 12.sp)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
}

@Composable
private fun RecentPayments(list: List<PpView>, onOpen: (String) -> Unit) {
    Text("Payments", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    val rows = list.flatMap { v -> v.project.payments.map { v to it } }.sortedByDescending { it.second.at }
    if (rows.isEmpty()) Text("No payments recorded yet.", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
    rows.take(50).forEach { (v, p) ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(v.project.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Green.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { Text("₹", color = Color(0xFF3F6212), fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(v.project.name, color = Ink, fontWeight = FontWeight.SemiBold)
                if (p.note.isNotBlank()) Text(p.note, color = Sub, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) { Text(QeOps.fmt(p.amount), color = Ink, fontWeight = FontWeight.Bold); Text(day(p.at), color = Sub, fontSize = 12.sp) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
    }
}

@Composable
private fun BottomBar(pane: Pane, onPane: (Pane) -> Unit, onPlus: () -> Unit, onHome: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(72.dp)) {
        Row(Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter).clip(RoundedCornerShape(18.dp)).background(Color(0xFFF5F5F4)), verticalAlignment = Alignment.CenterVertically) {
            NavItem("⌂", "Home", false, Modifier.weight(1f), onHome)
            NavItem("▦", "Dashboard", pane == Pane.Dashboard, Modifier.weight(1f)) { onPane(Pane.Dashboard) }
            Spacer(Modifier.width(64.dp))
            NavItem("☰", "Projects", pane == Pane.All, Modifier.weight(1f)) { onPane(Pane.All) }
            NavItem("₹", "Payments", pane == Pane.Payments, Modifier.weight(1f)) { onPane(Pane.Payments) }
        }
        Box(Modifier.size(58.dp).align(Alignment.TopCenter).clip(CircleShape).background(Hero).clickable(onClickLabel = "New project", onClick = onPlus), contentAlignment = Alignment.Center) {
            Text("+", color = Green, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = if (active) Ink else Grey, fontSize = 18.sp)
        Text(label, color = if (active) Ink else Grey, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, String, String, String, String) -> Unit) {
    var customer by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }; var value by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QeTextField(customer, { customer = it }, Modifier.fillMaxWidth(), placeholder = "Customer")
                QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Project")
                QeTextField(desc, { desc = it }, Modifier.fillMaxWidth(), placeholder = "What the project is (description)", singleLine = false, minLines = 2)
                QeTextField(value, { value = it }, Modifier.fillMaxWidth(), placeholder = "Total project value ₹")
                QeTextField(start, { start = it }, Modifier.fillMaxWidth(), placeholder = "Start date YYYY-MM-DD")
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(customer, name, desc, value, start) }) { Text("Create", color = QeColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = QeColors.Muted) } },
    )
}

/* ---------------- one project ---------------- */

private enum class PTab(val label: String) { Details("Project details"), Quotation("Quotation"), Payments("Payments") }

@Composable
private fun ProjectScreen(v: PpView, pvm: ProjectsViewModel, ovm: OperationsViewModel, saved: List<SavedProjectEntity>, cb: ProjectsCallbacks, onBack: () -> Unit) {
    var tab by rememberSaveable(v.project.id) { mutableStateOf(PTab.Details) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ Back", color = Color(0xFF4D7C0F), fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(v.project.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(v.project.customer, color = Sub, fontSize = 13.sp)
        }
        Text(v.revisionLabel, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF5F5F4)).padding(horizontal = 10.dp, vertical = 4.dp))
        StatusChip(v.status)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F4)).padding(4.dp)) {
        PTab.entries.forEach { t ->
            Text(t.label, color = if (t == tab) Ink else Sub, fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (t == tab) Paper else Color.Transparent).clickable { tab = t }.padding(vertical = 9.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    when (tab) {
        PTab.Details -> DetailsTab(v, pvm, ovm)
        PTab.Quotation -> QuotationTab(v, pvm, ovm, saved, cb)
        PTab.Payments -> PaymentsTab(v, pvm)
    }
}

@Composable
private fun StatusChip(s: String) =
    Text(QeProjects.statusLabel(s), color = if (s == QeProjects.RUNNING) Color(0xFF3F6212) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (s == QeProjects.RUNNING) Green else statusColor(s)).padding(horizontal = 10.dp, vertical = 4.dp))

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Rule, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun KV(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(k, color = Sub, fontSize = 13.sp, modifier = Modifier.width(150.dp)); Text(v, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
}

/** A pending "Are you sure?" question: message + what to do on Yes. */
private class Ask(val title: String, val text: String, val yes: String, val action: () -> Unit)

@Composable
private fun AskDialog(ask: Ask?, onDone: () -> Unit) {
    val a = ask ?: return
    AlertDialog(onDismissRequest = onDone, title = { Text(a.title) }, text = { Text(a.text) },
        confirmButton = { TextButton(onClick = { a.action(); onDone() }) { Text(a.yes, color = Color(0xFFB91C1C)) } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel", color = QeColors.Muted) } })
}

@Composable
private fun Link(text: String, danger: Boolean = false, onClick: () -> Unit) =
    Text(text, color = if (danger) Color(0xFFB91C1C) else Color(0xFF4D7C0F), fontSize = 13.sp, modifier = Modifier.clickable(onClick = onClick).padding(6.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsTab(v: PpView, pvm: ProjectsViewModel, ovm: OperationsViewModel) {
    val m = v.money
    val id = v.project.id
    var ask by remember(id) { mutableStateOf<Ask?>(null) }
    AskDialog(ask) { ask = null }
    Section("Overview") {
        KV("Status", QeProjects.statusLabel(v.status))
        KV("Revision", v.revisionLabel + if (v.revisions.isNotEmpty()) "  (${v.revisions.size} older, read-only)" else "")
        KV("Total cost", QeOps.fmt(m.total))
        KV("Payments received", QeOps.fmt(m.paid) + "  (${m.pct}%)")
        KV("Remaining", QeOps.fmt(m.remaining))
        KV("Items", "${v.items.size}")
        var start by remember(id, v.startDate) { mutableStateOf(v.startDate) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("Given / started", color = Sub, fontSize = 13.sp, modifier = Modifier.width(150.dp))
            QeTextField(start, { start = it }, Modifier.weight(1f), placeholder = "YYYY-MM-DD")
            if (start != v.startDate) QeSecondaryButton("Save", Modifier.padding(start = 6.dp)) { if (QeProjects.paymentAt(start) != null) pvm.setStartDate(id, start.trim()) else pvm.msg("Enter the date as YYYY-MM-DD.") }
            else if (v.meta.startDate.isNotBlank()) Link("Reset", danger = true) {
                ask = Ask("Reset start date?", "The start date goes back to the day the project was created (${v.project.created.take(10)}).", "Reset") { pvm.clearStartDate(id) }
            }
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (v.status) {
                QeProjects.RUNNING -> { QeSecondaryButton("Mark finished") { pvm.setStatus(id, QeProjects.FINISHED) }; QeDangerButton("Delete") { pvm.setStatus(id, QeProjects.DELETED) } }
                QeProjects.FINISHED -> { QeSecondaryButton("Back to running") { pvm.setStatus(id, QeProjects.RUNNING) }; QeDangerButton("Delete") { pvm.setStatus(id, QeProjects.DELETED) } }
                else -> QePrimaryButton("Undo delete (back to running)") { pvm.setStatus(id, QeProjects.RUNNING) }
            }
        }
    }
    Section("What the project is") {
        var d by remember(id, v.meta.description) { mutableStateOf(v.meta.description) }
        QeTextField(d, { d = it }, Modifier.fillMaxWidth(), placeholder = "Description", singleLine = false, minLines = 2)
        var n by remember(id, v.project.notes) { mutableStateOf(v.project.notes) }
        QeTextField(n, { n = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = "Notes", singleLine = false, minLines = 2)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d != v.meta.description || n != v.project.notes) QeSecondaryButton("Save") { pvm.setDescription(id, d); pvm.setNotes(id, n) }
            if (v.meta.description.isNotBlank()) Link("Delete description", danger = true) {
                ask = Ask("Delete description?", "The project description will be cleared.", "Delete") { pvm.setDescription(id, ""); d = "" }
            }
            if (v.project.notes.isNotBlank()) Link("Delete notes", danger = true) {
                ask = Ask("Delete notes?", "The project notes will be cleared.", "Delete") { pvm.setNotes(id, ""); n = "" }
            }
        }
    }
    RevisionsSection(v, pvm)
    Section("Furniture items · ${v.revisionLabel} · total ${QeOps.fmt(m.total)}") {
        var editItem by remember(id) { mutableStateOf<PpItemEntity?>(null) }
        if (v.items.isEmpty()) Text("No items yet. Confirm a saved quotation as a project, or add them here.", color = Sub, fontSize = 13.sp)
        v.items.forEach { it ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.name.ifBlank { "Item" }, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(QeOps.fmt(it.price), color = Ink, fontWeight = FontWeight.Bold)
                    Link("Edit") { editItem = it }
                    Link("Delete", danger = true) { ask = Ask("Delete item?", "Remove \"${it.name}\" from ${v.revisionLabel}? Older revisions keep their copy.", "Delete") { pvm.deleteItem(it.id) } }
                }
                if (it.qty.isNotBlank() || it.unit.isNotBlank()) Text("Qty: " + listOf(it.qty, it.unit).filter { s -> s.isNotBlank() }.joinToString(" "), color = Sub, fontSize = 12.sp)
                if (it.wood.isNotBlank()) Text("Wood: " + it.wood, color = Sub, fontSize = 12.sp)
                if (it.sizes.isNotBlank()) Text("Sizes:\n" + it.sizes, color = Sub, fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
        }
        QeSecondaryButton("+ Add item", Modifier.padding(top = 6.dp)) { editItem = PpItemEntity("", id, (v.items.maxOfOrNull { it.position } ?: -1) + 1, "") }
        Text("Item prices are a record; the project total changes with Revise project.", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        editItem?.let { e -> ItemDialog(e, { editItem = null }) { pvm.saveItem(it); editItem = null } }
    }
    Section("Extra hardware needed") {
        var editing by remember(id) { mutableStateOf<PpHardwareEntity?>(null) }
        v.hardware.forEach { h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h.name + if (h.qty.isNotBlank()) " × " + h.qty else "", color = Ink, fontSize = 14.sp)
                    if (h.note.isNotBlank()) Text(h.note, color = Sub, fontSize = 12.sp)
                }
                Text("Edit", color = Color(0xFF4D7C0F), modifier = Modifier.clickable { editing = h }.padding(6.dp))
                Text("Delete", color = Color(0xFFB91C1C), modifier = Modifier.clickable { ask = Ask("Delete hardware?", "Remove \"${h.name}\" from the hardware list?", "Delete") { pvm.deleteHardware(h.id) } }.padding(6.dp))
            }
        }
        QeSecondaryButton("+ Add hardware", Modifier.padding(top = 6.dp)) { editing = PpHardwareEntity("", id, "") }
        editing?.let { e -> HardwareDialog(e, { editing = null }) { pvm.saveHardware(it); editing = null } }
    }
    DocumentsSection(id, ovm, pvm)
    CommentsBoard(v, pvm)
}

@Composable
private fun HardwareDialog(h: PpHardwareEntity, onDismiss: () -> Unit, onSave: (PpHardwareEntity) -> Unit) {
    var name by remember { mutableStateOf(h.name) }; var qty by remember { mutableStateOf(h.qty) }; var note by remember { mutableStateOf(h.note) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (h.id.isEmpty()) "Add hardware" else "Edit hardware") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Item (e.g. 4\" hinges)")
            QeTextField(qty, { qty = it }, Modifier.fillMaxWidth(), placeholder = "Quantity")
            QeTextField(note, { note = it }, Modifier.fillMaxWidth(), placeholder = "Note")
        } },
        confirmButton = { TextButton(onClick = { onSave(h.copy(name = name.trim(), qty = qty.trim(), note = note.trim())) }) { Text("Save", color = QeColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = QeColors.Muted) } })
}

@Composable
private fun ItemDialog(i: PpItemEntity, onDismiss: () -> Unit, onSave: (PpItemEntity) -> Unit) {
    var name by remember { mutableStateOf(i.name) }; var qty by remember { mutableStateOf(i.qty) }; var unit by remember { mutableStateOf(i.unit) }
    var price by remember { mutableStateOf(if (i.id.isEmpty() && i.price == 0.0) "" else plain(i.price)) }
    var sizes by remember { mutableStateOf(i.sizes) }; var wood by remember { mutableStateOf(i.wood) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (i.id.isEmpty()) "Add item" else "Edit item") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Item")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QeTextField(qty, { qty = it }, Modifier.weight(1f), placeholder = "Qty")
                QeTextField(unit, { unit = it }, Modifier.weight(1f), placeholder = "Unit")
            }
            QeTextField(price, { price = it }, Modifier.fillMaxWidth(), placeholder = "Price ₹")
            QeTextField(wood, { wood = it }, Modifier.fillMaxWidth(), placeholder = "Wood")
            QeTextField(sizes, { sizes = it }, Modifier.fillMaxWidth(), placeholder = "Sizes (one per line)", singleLine = false, minLines = 2)
        } },
        confirmButton = { TextButton(onClick = { onSave(i.copy(name = name.trim(), qty = qty.trim(), unit = unit.trim(), price = price.trim().toDoubleOrNull() ?: 0.0, sizes = sizes.trim(), wood = wood.trim())) }) { Text("Save", color = QeColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = QeColors.Muted) } })
}

/** Project revisions: current R is editable; "Revise project" freezes it and starts the next R. */
@Composable
private fun RevisionsSection(v: PpView, pvm: ProjectsViewModel) {
    var revise by remember(v.project.id) { mutableStateOf(false) }
    var view by remember(v.project.id) { mutableStateOf<PpRevisionEntity?>(null) }
    Section("Project revisions") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(v.revisionLabel + "  (current, editable)", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(QeOps.fmt(v.money.total), color = Ink, fontWeight = FontWeight.Bold)
        }
        v.revisions.sortedByDescending { it.revision }.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(QeProjects.label(r.revision) + "  (read-only) · " + QeOps.fmt(r.total), color = Ink, fontSize = 13.sp)
                    Text(day(r.createdAt) + if (r.reason.isNotBlank()) " · " + r.reason else "", color = Sub, fontSize = 12.sp)
                }
                Link("View") { view = r }
            }
        }
        QePrimaryButton("Revise project", Modifier.padding(top = 8.dp)) { revise = true }
        Text("Use when the client adds or removes extras: ${v.revisionLabel} is kept read-only and ${QeProjects.label(v.revision + 1)} becomes the editable version. Total and remaining follow the latest revision.",
            color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
    if (revise) {
        var total by remember { mutableStateOf(plain(v.money.total)) }
        var reason by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { revise = false }, title = { Text("Revise project → ${QeProjects.label(v.revision + 1)}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${v.revisionLabel} (${QeOps.fmt(v.money.total)}) is saved as a read-only revision.", color = Sub, fontSize = 13.sp)
                QeTextField(total, { total = it }, Modifier.fillMaxWidth(), placeholder = "Revised total project value ₹")
                QeTextField(reason, { reason = it }, Modifier.fillMaxWidth(), placeholder = "What changed (extras added / removed)", singleLine = false, minLines = 2)
            } },
            confirmButton = { TextButton(onClick = { if (pvm.revise(v, total, reason)) revise = false }) { Text("Create ${QeProjects.label(v.revision + 1)}", color = QeColors.Accent) } },
            dismissButton = { TextButton(onClick = { revise = false }) { Text("Cancel", color = QeColors.Muted) } })
    }
    view?.let { r -> SnapshotDialog(r) { view = null } }
}

@Composable
private fun SnapshotDialog(r: PpRevisionEntity, onDismiss: () -> Unit) {
    val s = QeProjects.decode(r.snapshotJson)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(QeProjects.label(r.revision) + " · read-only") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KV("Total value", QeOps.fmt(r.total))
                if (s != null) {
                    KV("Paid by then", QeOps.fmt(s.paid))
                    KV("Saved", QeOps.shortAt(s.at))
                    if (s.startDate.isNotBlank()) KV("Given / started", s.startDate)
                    if (r.reason.isNotBlank()) KV("Next revision", r.reason)
                    if (s.description.isNotBlank()) KV("Description", s.description)
                    if (s.notes.isNotBlank()) KV("Notes", s.notes)
                    Text("Items", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    if (s.items.isEmpty()) Text("None", color = Sub, fontSize = 13.sp)
                    s.items.forEach { i ->
                        Text(i.name + (if (i.qty.isNotBlank()) " × " + i.qty + " " + i.unit else "") + " — " + QeOps.fmt(i.price), color = Ink, fontSize = 13.sp)
                        if (i.wood.isNotBlank()) Text("Wood: " + i.wood, color = Sub, fontSize = 12.sp)
                        if (i.sizes.isNotBlank()) Text("Sizes:\n" + i.sizes, color = Sub, fontSize = 12.sp)
                    }
                    Text("Hardware", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    if (s.hardware.isEmpty()) Text("None", color = Sub, fontSize = 13.sp)
                    s.hardware.forEach { h -> Text(h.name + (if (h.qty.isNotBlank()) " × " + h.qty else "") + (if (h.note.isNotBlank()) " · " + h.note else ""), color = Ink, fontSize = 13.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = QeColors.Accent) } })
}

@Composable
private fun DocumentsSection(id: String, ovm: OperationsViewModel, pvm: ProjectsViewModel) {
    val files by ovm.attachments.collectAsState()
    val viewer by ovm.viewer.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) pvm.upload(id, uris) }
    var ask by remember(id) { mutableStateOf<Ask?>(null) }
    var rename by remember(id) { mutableStateOf<OpsAttachmentEntity?>(null) }
    AskDialog(ask) { ask = null }
    Section("Documents & pictures") {
        val list = files[id].orEmpty()
        if (list.isEmpty()) Text("No files attached.", color = Sub, fontSize = 13.sp)
        list.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.name + "  (" + Math.round(a.size / 1024.0) + " KB)", color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Open", color = Color(0xFF4D7C0F), modifier = Modifier.clickable { ovm.open(a) }.padding(6.dp))
                Link("Rename") { rename = a }
                Link("Delete", danger = true) { ask = Ask("Delete file?", "Remove ${a.name} from this project? This cannot be undone.", "Delete") { ovm.removeAttachment(a) } }
            }
        }
        rename?.let { a ->
            var name by remember(a.id) { mutableStateOf(a.name) }
            AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename file") },
                text = { QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "File name") },
                confirmButton = { TextButton(onClick = { pvm.renameAttachment(a, name); rename = null }) { Text("Save", color = QeColors.Accent) } },
                dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel", color = QeColors.Muted) } })
        }
        QeSecondaryButton("+ Attach files / photos", Modifier.padding(top = 6.dp)) { pick.launch(arrayOf("image/*", "application/pdf", "*/*")) }
        viewer?.takeIf { it.projectId == id }?.let { Box(Modifier.padding(top = 8.dp)) { AttachmentViewer(it, ovm) } }
    }
}

@Composable
private fun CommentsBoard(v: PpView, pvm: ProjectsViewModel) {
    Section("Comments board") {
        var ask by remember(v.project.id) { mutableStateOf<Ask?>(null) }
        var editing by remember(v.project.id) { mutableStateOf<PpCommentEntity?>(null) }
        AskDialog(ask) { ask = null }
        Text("User and client comments are kept as separate, timestamped entries. Edit or delete any entry; edits are marked.", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        v.comments.forEach { c ->
            val client = c.author == QeProjects.CLIENT
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(start = if (client) 24.dp else 0.dp, end = if (client) 0.dp else 24.dp)
                .clip(RoundedCornerShape(12.dp)).background(if (client) Color(0xFFEFF6FF) else Color(0xFFF7FEE7)).padding(10.dp)) {
                Text(QeProjects.authorLabel(c.author) + " · " + QeOps.shortAt(c.at) + if (c.editedAt.isNotBlank()) " · edited " + QeOps.shortAt(c.editedAt) else "", color = Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(c.text, color = Ink, fontSize = 14.sp)
                Row(Modifier.align(Alignment.End)) {
                    Link("Edit") { editing = c }
                    Link("Delete", danger = true) { ask = Ask("Delete comment?", "Delete this ${QeProjects.authorLabel(c.author).lowercase()} comment? This cannot be undone.", "Delete") { pvm.deleteComment(c.id) } }
                }
            }
        }
        editing?.let { c ->
            var t by remember(c.id) { mutableStateOf(c.text) }
            AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit ${QeProjects.authorLabel(c.author).lowercase()} comment") },
                text = { QeTextField(t, { t = it }, Modifier.fillMaxWidth(), placeholder = "Comment", singleLine = false, minLines = 3) },
                confirmButton = { TextButton(onClick = { if (pvm.editComment(c, t)) editing = null }) { Text("Save", color = QeColors.Accent) } },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = QeColors.Muted) } })
        }
        var author by remember(v.project.id) { mutableStateOf(QeProjects.USER) }
        var text by remember(v.project.id) { mutableStateOf("") }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(QeProjects.USER, QeProjects.CLIENT).forEach { a ->
                Text(QeProjects.authorLabel(a), color = if (author == a) Color.White else Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (author == a) Hero else Color(0xFFF5F5F4)).clickable { author = a }.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        QeTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = "Add a ${QeProjects.authorLabel(author).lowercase()} comment", singleLine = false, minLines = 2)
        QePrimaryButton("Add comment", Modifier.padding(top = 8.dp)) { if (pvm.addComment(v.project.id, author, text)) text = "" }
    }
}

@Composable
private fun QuotationTab(v: PpView, pvm: ProjectsViewModel, ovm: OperationsViewModel, saved: List<SavedProjectEntity>, cb: ProjectsCallbacks) {
    val id = v.project.id
    val job = QeRevisions.jobs(saved).firstOrNull { j -> (v.meta.jobRootId.isNotEmpty() && j.rootId == v.meta.jobRootId) || (v.meta.jobUuid.isNotEmpty() && j.revisions.any { it.jobUuid == v.meta.jobUuid }) }
    Section("Linked quotation") {
        if (job == null) Text("No saved quotation is linked. Open a saved quotation and tap Confirm as Project, or upload a PDF below.", color = Sub, fontSize = 13.sp)
        else {
            Text(job.name, color = Ink, fontWeight = FontWeight.SemiBold)
            Text("Confirmed from R${v.meta.quoteRevision} · latest R${job.latest.revision}", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            job.revisions.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("R${r.revision}" + if (r.id == job.latest.id) "  (latest, editable)" else "  (read-only)", color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("View", color = Color(0xFF4D7C0F), modifier = Modifier.clickable { cb.onViewRevision(r.id) }.padding(6.dp))
                    Text("Print", color = Color(0xFF4D7C0F), modifier = Modifier.clickable { cb.onPrintRevision(r.id) }.padding(6.dp))
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QePrimaryButton("Open quotation") { cb.onOpenLatest(job.rootId) }
                QeSecondaryButton("Add revision") { cb.onOpenLatest(job.rootId); pvm.msg("Edit the quotation, then Save Project to create R${job.latest.revision + 1}.") }
            }
            Text("Add revision opens the latest revision in the Quotation tab; Save Project stores it as the next R number (older revisions stay read-only).", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    val files by ovm.attachments.collectAsState()
    val pdf = files[id].orEmpty().firstOrNull { it.id == v.meta.quotePdfId }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { pvm.upload(id, listOf(it), asQuotation = true) } }
    Section("PDF quotation") {
        if (pdf == null) Text("No PDF quotation uploaded.", color = Sub, fontSize = 13.sp) else {
            Text(pdf.name, color = Ink, fontSize = 13.sp)
            Box(Modifier.padding(top = 8.dp)) { AttachmentViewer(pdf, ovm) }
        }
        QeSecondaryButton(if (pdf == null) "Upload PDF quotation" else "Replace PDF quotation", Modifier.padding(top = 8.dp)) { pick.launch(arrayOf("application/pdf")) }
    }
}

@Composable
private fun PaymentsTab(v: PpView, pvm: ProjectsViewModel) {
    val m = v.money
    Section("Payment summary") {
        KV("Total project amount", QeOps.fmt(m.total))
        KV("Received so far", QeOps.fmt(m.paid))
        KV("Remaining", QeOps.fmt(m.remaining))
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Rule)) {
            Box(Modifier.fillMaxWidth(m.pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Green))
        }
        Text("Total follows the latest project revision (${v.revisionLabel}).", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        if (v.project.revisions.isNotEmpty()) Text("Includes revision changes: " + QeOps.fmt(v.project.revisions.sumOf { it.amount }), color = Sub, fontSize = 12.sp)
    }
    var ask by remember(v.project.id) { mutableStateOf<Ask?>(null) }
    AskDialog(ask) { ask = null }
    Section("Advances & payments") {
        val list = v.paymentRows
        if (list.isEmpty()) Text("No payments yet.", color = Sub, fontSize = 13.sp)
        var running = 0.0
        list.forEach { p ->
            running += p.amount
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day(p.at), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (p.note.isNotBlank()) Text(p.note, color = Sub, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(QeOps.fmt(p.amount), color = Ink, fontWeight = FontWeight.Bold)
                    Text("Balance " + QeOps.fmt(m.total - running), color = Sub, fontSize = 11.sp)
                }
                Link("Delete", danger = true) {
                    ask = Ask("Delete payment?", "Delete the payment of ${QeOps.fmt(p.amount)} dated ${day(p.at)}? The remaining amount goes up by this amount.", "Delete") { pvm.deletePayment(p.rowId) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
        }
    }
    Section("Add payment") {
        var amount by remember(v.project.id) { mutableStateOf("") }
        var date by remember(v.project.id) { mutableStateOf(LocalDate.now().toString()) }
        var note by remember(v.project.id) { mutableStateOf("") }
        QeTextField(amount, { amount = it }, Modifier.fillMaxWidth(), placeholder = "Amount ₹")
        QeTextField(date, { date = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = "Date YYYY-MM-DD")
        QeTextField(note, { note = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = "Note (advance, cash, UPI…)")
        QePrimaryButton("Add payment", Modifier.padding(top = 8.dp)) { if (pvm.addPayment(v.project.id, amount, date, note)) { amount = ""; note = "" } }
    }
}
