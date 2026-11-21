package com.moraswood.qande.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.PpHardwareEntity
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
                    Pane.Dashboard -> { SummaryCard(list); Spacer(Modifier.height(16.dp)); ProjectList(list, limit = 5, onSeeAll = { pane = Pane.All }) { selected = it } }
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
        Text(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year, color = Color(0xFFB4BFC1), fontSize = 13.sp)
        Text(QeOps.fmt(s.collectedThisMonth), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text("collected this month · ${s.running} running", color = Color(0xFFB4BFC1), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF3B4447))) {
                Box(Modifier.fillMaxWidth(s.pct / 100f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Green))
            }
            Text("${s.pct}%", color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
        Text("Collected ${QeOps.fmt(s.collected)} of ${QeOps.fmt(s.value)} total project value", color = Color(0xFFB4BFC1), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
            Text(v.project.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsTab(v: PpView, pvm: ProjectsViewModel, ovm: OperationsViewModel) {
    val m = v.money
    val id = v.project.id
    Section("Overview") {
        KV("Status", QeProjects.statusLabel(v.status))
        KV("Total cost", QeOps.fmt(m.total))
        KV("Payments received", QeOps.fmt(m.paid) + "  (${m.pct}%)")
        KV("Remaining", QeOps.fmt(m.remaining))
        KV("Items", "${v.items.size}")
        var start by remember(id, v.startDate) { mutableStateOf(v.startDate) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("Given / started", color = Sub, fontSize = 13.sp, modifier = Modifier.width(150.dp))
            QeTextField(start, { start = it }, Modifier.weight(1f), placeholder = "YYYY-MM-DD")
            if (start != v.startDate) QeSecondaryButton("Save", Modifier.padding(start = 6.dp)) { if (QeProjects.paymentAt(start) != null) pvm.setStartDate(id, start.trim()) else pvm.msg("Enter the date as YYYY-MM-DD.") }
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
        if (d != v.meta.description || n != v.project.notes) QeSecondaryButton("Save", Modifier.padding(top = 8.dp)) { pvm.setDescription(id, d); pvm.setNotes(id, n) }
    }
    Section("Furniture items · quoted ${QeOps.fmt(v.project.original)}") {
        if (v.items.isEmpty()) Text("No items yet. Confirm a saved quotation as a project to fill them in.", color = Sub, fontSize = 13.sp)
        v.items.forEach { it ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row { Text(it.name.ifBlank { "Item" }, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(QeOps.fmt(it.price), color = Ink, fontWeight = FontWeight.Bold) }
                if (it.qty.isNotBlank() || it.unit.isNotBlank()) Text("Qty: " + listOf(it.qty, it.unit).filter { s -> s.isNotBlank() }.joinToString(" "), color = Sub, fontSize = 12.sp)
                if (it.wood.isNotBlank()) Text("Wood: " + it.wood, color = Sub, fontSize = 12.sp)
                if (it.sizes.isNotBlank()) Text("Sizes:\n" + it.sizes, color = Sub, fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
        }
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
                Text("Delete", color = Color(0xFFB91C1C), modifier = Modifier.clickable { pvm.deleteHardware(h.id) }.padding(6.dp))
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
private fun DocumentsSection(id: String, ovm: OperationsViewModel, pvm: ProjectsViewModel) {
    val files by ovm.attachments.collectAsState()
    val viewer by ovm.viewer.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) pvm.upload(id, uris) }
    Section("Documents & pictures") {
        val list = files[id].orEmpty()
        if (list.isEmpty()) Text("No files attached.", color = Sub, fontSize = 13.sp)
        list.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.name + "  (" + Math.round(a.size / 1024.0) + " KB)", color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Open", color = Color(0xFF4D7C0F), modifier = Modifier.clickable { ovm.open(a) }.padding(6.dp))
            }
        }
        QeSecondaryButton("+ Attach files / photos", Modifier.padding(top = 6.dp)) { pick.launch(arrayOf("image/*", "application/pdf", "*/*")) }
        viewer?.takeIf { it.projectId == id }?.let { Box(Modifier.padding(top = 8.dp)) { AttachmentViewer(it, ovm) } }
    }
}

@Composable
private fun CommentsBoard(v: PpView, pvm: ProjectsViewModel) {
    Section("Comments board") {
        Text("User and client comments are kept as separate, timestamped entries and cannot be edited or deleted after saving.", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        v.comments.forEach { c ->
            val client = c.author == QeProjects.CLIENT
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(start = if (client) 24.dp else 0.dp, end = if (client) 0.dp else 24.dp)
                .clip(RoundedCornerShape(12.dp)).background(if (client) Color(0xFFEFF6FF) else Color(0xFFF7FEE7)).padding(10.dp)) {
                Text(QeProjects.authorLabel(c.author) + " · " + QeOps.shortAt(c.at), color = Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(c.text, color = Ink, fontSize = 14.sp)
            }
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
        if (v.project.revisions.isNotEmpty()) Text("Total includes approved revision extras: " + QeOps.fmt(v.project.revisions.sumOf { it.amount }), color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
    Section("Advances & payments") {
        val list = QeProjects.chronological(v.project)
        if (list.isEmpty()) Text("No payments yet.", color = Sub, fontSize = 13.sp)
        var running = 0.0
        list.forEach { p ->
            running += p.amount
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(day(p.at), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (p.note.isNotBlank()) Text(p.note, color = Sub, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(QeOps.fmt(p.amount), color = Ink, fontWeight = FontWeight.Bold)
                    Text("Balance " + QeOps.fmt(m.total - running), color = Sub, fontSize = 11.sp)
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
