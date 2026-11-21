package com.moraswood.qande.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.OpsAttachmentEntity
import com.moraswood.qande.logic.OpsDb
import com.moraswood.qande.logic.OpsProject
import com.moraswood.qande.logic.QeOps
import com.moraswood.qande.ops.Attachments
import com.moraswood.qande.ops.OperationsViewModel
import com.moraswood.qande.ops.PdfPreview
import com.moraswood.qande.ui.components.PortPendingCard
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors

/** operations.html sections, in nav order. */
enum class OpsTab(val id: String, val label: String, val heading: String, val milestone: String) {
    Projects("projects", "Projects", "Projects & Payments", "Projects/payments"),
    Inventory("inventory", "Expenses & Inventory", "Expenses & Inventory", "Projects/payments"),
    Machines("machines", "Machines & Tools", "Machines & Tools", "Machines"),
    Reports("reports", "Monthly Reports", "Monthly Summary", "Machines"),
    /** Native addition (Feature C): asset registry with append-only issue / return history. */
    Assets("assets", "Assets", "Assets", "Machines");
}

private val OpsLine = Color(0xFF465154)

/** Operations page header: "MORAS WOOD INDUSTRIES · Operations" + "← Q&E Home". */
@Composable
fun OperationsTopBar(onHome: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(QeColors.Header).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MORAS WOOD INDUSTRIES · Operations", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            QePrimaryButton("← Q&E Home", onClick = onHome)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QeColors.HeaderLine))
    }
}

/* ---------- operations.html building blocks ---------- */

/** `.card` (operations.html dark override): #292f32, 1px #465154, radius 16, padding 15, margin 12 0. */
@Composable
internal fun OpsCard(modifier: Modifier = Modifier, padding: Dp = 15.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.padding(vertical = 12.dp).fillMaxWidth().clip(shape).background(QeColors.Card).border(1.dp, OpsLine, shape).padding(padding), content = content)
}

@Composable internal fun H2(text: String) = Text(text, color = QeColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
@Composable internal fun H3(text: String) = Text(text, color = QeColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
@Composable internal fun Small(text: String, modifier: Modifier = Modifier) = Text(text, color = QeColors.OpsMuted, fontSize = 13.sp, modifier = modifier)

/** `<label>Label<input></label>`: 13px label, input with margin 4px 0 10px. */
@Composable
internal fun OpsLabel(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, color = QeColors.Text, fontSize = 13.sp)
        Box(Modifier.padding(top = 4.dp, bottom = 10.dp)) { content() }
    }
}

/** `.grid`: repeat(auto-fit, minmax(160px, 1fr)), gap 10. */
@Composable
internal fun OpsGrid(cells: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = maxOf(1, ((maxWidth.value + 10) / 170).toInt()).coerceAtMost(cells.size)
        Column {
            cells.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { it(Modifier.weight(1f)) }
                    repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** `<details><summary>`: tap to toggle, default ▸ / ▾ marker. */
@Composable
internal fun Details(summary: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(summary) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Text((if (open) "▾ " else "▸ ") + summary, color = QeColors.Text, modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp))
        if (open) Column(Modifier.padding(top = 4.dp), content = content)
    }
}

/** `<textarea>` whose `change` (blur) event commits the value. */
@Composable
private fun ChangeOnBlurTextArea(value: String, placeholder: String, minLines: Int, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    QeTextField(
        text, { text = it },
        Modifier.fillMaxWidth().onFocusChanged {
            if (focused && !it.isFocused && text != value) onCommit(text)
            focused = it.isFocused
        },
        placeholder = placeholder, singleLine = false, minLines = minLines,
    )
}

/** Single-line `<input>` whose `change` (blur) event commits the value. */
@Composable
internal fun ChangeOnBlurField(value: String, modifier: Modifier = Modifier, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    QeTextField(
        text, { text = it },
        modifier.onFocusChanged {
            if (focused && !it.isFocused && text != value) onCommit(text)
            focused = it.isFocused
        },
    )
}

/* ---------- screen ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperationsScreen(tab: OpsTab, onTab: (OpsTab) -> Unit, ovm: OperationsViewModel, avm: com.moraswood.qande.assets.AssetsViewModel) {
    val message by ovm.message.collectAsState()
    val viewer by ovm.viewer.collectAsState()
    val scroll = rememberScrollState()
    LaunchedEffect(viewer?.id) { if (viewer != null) scroll.animateScrollTo(0) } // viewer.scrollIntoView()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OpsTab.entries.forEach { t -> QeSecondaryButton(t.label, onClick = { onTab(t); ovm.onTab(); avm.msg("") }) }
            }
            Text(message, color = QeColors.Text, modifier = Modifier.padding(vertical = 12.dp))
            viewer?.let { AttachmentViewer(it, ovm) }
            when (tab) {
                OpsTab.Projects -> ProjectsSection(ovm)
                OpsTab.Inventory -> InventorySection(ovm)
                OpsTab.Machines -> MachinesSection(ovm)
                OpsTab.Reports -> ReportsSection(ovm)
                OpsTab.Assets -> AssetsSection(avm)
            }
            Text(
                "Local-first operations preview. Data is saved on this device and included in Q&E's existing full JSON backup. Worker accounts, cloud sync, push notifications, camera QR scanning, printable QR stickers and cloud-synced document attachments require later development. Project attachments are stored separately on this device and are NOT included in the JSON backup. Export important files separately before clearing app data or uninstalling.",
                color = QeColors.OpsMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** `#attachmentViewer`: native image preview, PdfRenderer pages, or the "unavailable" note. */
@Composable
private fun AttachmentViewer(a: OpsAttachmentEntity, ovm: OperationsViewModel) {
    val context = LocalContext.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    OpsCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            QeSecondaryButton("Close preview", onClick = ovm::closeViewer)
        }
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
            when (Attachments.kind(a)) {
                Attachments.Kind.Image -> {
                    var bmp by remember(a.id) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(a.id) { bmp = Attachments.decodeImage(context, a, 2048) }
                    bmp?.let { Image(it.asImageBitmap(), a.name, Modifier.fillMaxWidth().heightIn(max = screenH * 0.7f), contentScale = ContentScale.Fit) }
                }
                Attachments.Kind.Pdf -> PdfPages(a, screenH * 0.65f)
                Attachments.Kind.Other -> Text("Preview is unavailable for this file type. Use Download to open it in a compatible app.", color = QeColors.Text)
            }
        }
        Row { QePrimaryButton("Download / Open externally", onClick = { ovm.openExternally(a) }) }
    }
}

@Composable
private fun PdfPages(a: OpsAttachmentEntity, height: Dp) {
    val context = LocalContext.current
    var pdf by remember(a.id) { mutableStateOf<PdfPreview?>(null) }
    var failed by remember(a.id) { mutableStateOf(false) }
    LaunchedEffect(a.id) { pdf = PdfPreview.open(Attachments.file(context, a)); failed = pdf == null }
    DisposableEffect(a.id) { onDispose { pdf?.close() } }
    if (failed) { Text("Preview is unavailable for this file type. Use Download to open it in a compatible app.", color = QeColors.Text); return }
    val doc = pdf ?: return
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).background(Color(0xFF525659))) {
        val px = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(doc.pageCount) { i ->
                var page by remember(a.id, i) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(a.id, i, px) { page = doc.render(i, px) }
                val b = page
                if (b != null) Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                else Box(Modifier.fillMaxWidth().height(height * 0.9f).background(Color.White))
            }
        }
    }
}

/* ---------- Projects & Payments ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectsSection(ovm: OperationsViewModel) {
    val db by ovm.ops.collectAsState()
    val files by ovm.attachments.collectAsState()
    val form by ovm.form.collectAsState()
    val formFiles by ovm.formFiles.collectAsState()
    val photos by ovm.pendingPhotos.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { ovm.setFormFiles(it) }

    H2("Projects & Payments")
    OpsCard {
        H3("Create / import existing project")
        OpsGrid(listOf(
            { m -> OpsLabel("Customer", m) { QeTextField(form.customer, { ovm.setForm(form.copy(customer = it)) }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Project", m) { QeTextField(form.name, { ovm.setForm(form.copy(name = it)) }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Original agreed value ₹", m) { QeNumberField(form.value, { ovm.setForm(form.copy(value = it)) }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Advance / payments already received ₹", m) { QeNumberField(form.paid, { ovm.setForm(form.copy(paid = it)) }, Modifier.fillMaxWidth()) } },
        ))
        OpsLabel("Project description / notes") {
            QeTextField(form.notes, { ovm.setForm(form.copy(notes = it)) }, Modifier.fillMaxWidth(), placeholder = "Scope, materials, site details, design instructions", singleLine = false, minLines = 3)
        }
        OpsLabel("Upload quotation / drawings / photos / documents (PDF, Word, Excel, images)") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QeSecondaryButton("Choose files", onClick = { picker.launch(Attachments.ACCEPT) })
                Small(if (formFiles.isEmpty()) "No file chosen" else if (formFiles.size == 1) formFiles[0].name else "${formFiles.size} files")
            }
        }
        if (photos.isNotEmpty()) Small("Item photos from the quotation (${photos.size}) will be attached.", Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QePrimaryButton("Create project & attach files", onClick = ovm::createProject)
            QeSecondaryButton("Import current Q&E quotation details", onClick = ovm::importQuote)
        }
        Small("Save your quotation in Q&E first. Import uses the latest autosaved quotation totals; verify the amount before approving.", Modifier.padding(top = 6.dp))
    }
    var search by rememberSaveable { mutableStateOf("") }
    OpsLabel("Search projects") { QeTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Search by project, customer or status") }
    val list = db.projects.filter { QeOps.matches(it, search) }
    if (list.isEmpty()) Text("No matching projects.", color = QeColors.Text, modifier = Modifier.padding(vertical = 8.dp))
    list.forEach { p -> ProjectCard(p, db, files[p.id].orEmpty(), ovm) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectCard(p: OpsProject, db: OpsDb, files: List<OpsAttachmentEntity>, ovm: OperationsViewModel) {
    var open by rememberSaveable(p.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removeFile by remember { mutableStateOf<OpsAttachmentEntity?>(null) }
    QeConfirm(if (confirmDelete) "Permanently delete " + p.name + " and its payment history?" else null) { ok -> confirmDelete = false; if (ok) ovm.deleteProject(p.id) }
    QeConfirm(if (removeFile != null) "Remove this attachment?" else null) { ok -> val f = removeFile; removeFile = null; if (ok && f != null) ovm.removeAttachment(f) }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) ovm.upload(p.id, it) }

    OpsCard(padding = 0.dp) {
        // .project-fold > summary
        FlowRow(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center,
        ) {
            Text(if (open) "▾" else "▸", color = QeColors.Accent, modifier = Modifier.align(Alignment.CenterVertically))
            Text(p.name, color = QeColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
            Text("· " + p.customer, color = QeColors.Text, modifier = Modifier.align(Alignment.CenterVertically))
            Text(p.status, color = QeColors.PillText, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(20.dp)).background(QeColors.Pill).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        if (!open) return@OpsCard
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            val f = QeOps.figures(db, p)
            OpsGrid(listOf(
                { m -> Figure("Project total", f.total, m) }, { m -> Figure("Received", f.received, m) }, { m -> Figure("Pending", f.pending, m) },
                { m -> Figure("Recorded cost", f.cost, m) }, { m -> Figure("Profit before unrecorded costs", f.profit, m) },
            ))
            OpsLabel("Project description / notes") {
                ChangeOnBlurTextArea(p.notes, "Scope, dimensions, wood species, design decisions, site details...", 4) { ovm.setNotes(p.id, it) }
            }
            OpsCard {
                Text("Quotation, photos & project documents", color = QeColors.Text, fontWeight = FontWeight.Bold)
                Small("Attach PDFs, images, drawings or other files (maximum 15 MB per file). Files stay on this device.", Modifier.padding(vertical = 8.dp))
                Row { QeSecondaryButton("Choose files", onClick = { upload.launch(Attachments.ACCEPT) }) }
                Column(Modifier.padding(top = 8.dp)) {
                    if (files.isEmpty()) Small("No files attached.")
                    files.forEach { a ->
                        FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
                            Row(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)) {
                                Text(a.name + " ", color = QeColors.Text)
                                Small("(" + Math.round(a.size / 1024.0) + " KB)")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QeSecondaryButton("Open", onClick = { ovm.open(a) })
                                QeSecondaryButton("Remove", onClick = { removeFile = a })
                            }
                        }
                    }
                }
            }
            OpsLabel("Status") { QeSelect(p.status, QeOps.STATUSES.map { it to it }, { ovm.setStatus(p.id, it) }, Modifier.fillMaxWidth()) }
            var amount by remember(p.id) { mutableStateOf("") }
            var kind by remember(p.id) { mutableStateOf("payment") }
            var note by remember(p.id) { mutableStateOf("") }
            OpsGrid(listOf(
                { m -> OpsLabel("Amount ₹", m) { QeNumberField(amount, { amount = it }, Modifier.fillMaxWidth()) } },
                { m -> OpsLabel("Entry type", m) {
                    QeSelect(kind, listOf("payment" to "Payment received", "revision" to "Approved revision extra", "expense" to "Other actual expense"), { kind = it }, Modifier.fillMaxWidth())
                } },
                { m -> OpsLabel("Description / reference", m) { QeTextField(note, { note = it }, Modifier.fillMaxWidth()) } },
            ))
            Row { QePrimaryButton("Add entry", onClick = { ovm.addEntry(p.id, amount, kind, note) { amount = ""; kind = "payment"; note = "" } }) }
            Details("Payment, revision & expense history") {
                QeOps.historyLines(p).forEach { Text(it, color = QeColors.Text, modifier = Modifier.padding(vertical = 6.dp)) }
            }
            Row { QeSecondaryButton("Delete project", onClick = { confirmDelete = true }) }
        }
    }
}

/** `<p>Label<br><b>fmt(value)</b></p>` */
@Composable
private fun Figure(label: String, v: Double, m: Modifier) {
    Column(m.padding(vertical = 8.dp)) {
        Text(label, color = QeColors.Text)
        Text(QeOps.fmt(v), color = QeColors.Text, fontWeight = FontWeight.Bold)
    }
}

/* ---------- Expenses & Inventory ---------- */

@Composable
private fun InventorySection(ovm: OperationsViewModel) {
    val db by ovm.ops.collectAsState()
    H2("Expenses & Inventory")
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(QeOps.CATEGORIES[0]) }
    var unit by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var min by rememberSaveable { mutableStateOf("") }
    OpsCard {
        H3("Register material / consumable")
        OpsGrid(listOf(
            { m -> OpsLabel("Material name", m) { QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "120-grit sanding disc") } },
            { m -> OpsLabel("Category", m) { QeSelect(category, QeOps.CATEGORIES.map { it to it }, { category = it }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Unit", m) { QeTextField(unit, { unit = it }, Modifier.fillMaxWidth(), placeholder = "pcs, L, kg, CFT") } },
            { m -> OpsLabel("Cost per unit ₹", m) { QeNumberField(rate, { rate = it }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Reorder level", m) { QeNumberField(min, { min = it }, Modifier.fillMaxWidth()) } },
        ))
        Row { QePrimaryButton("Add material", onClick = { ovm.addMaterial(name, unit, category, rate, min) }) }
    }
    db.materials.forEach { m ->
        OpsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                Text(QeOps.stockText(db, m), color = QeColors.Text)
            }
            Small(QeOps.materialDetail(m), Modifier.padding(top = 4.dp))
            if (QeOps.lowStock(db, m)) Text("Low stock: reorder needed", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }
    }
    // The selects are rebuilt by render(): selection falls back to the first material / "Unassigned".
    var material by remember(db.materials.size, db.movements.size) { mutableStateOf(db.materials.firstOrNull()?.id.orEmpty()) }
    var type by rememberSaveable { mutableStateOf("purchase") }
    var qty by rememberSaveable { mutableStateOf("") }
    var worker by rememberSaveable { mutableStateOf("") }
    var project by remember(db.projects.size, db.movements.size) { mutableStateOf("") }
    OpsCard {
        H3("Record material movement")
        OpsLabel("Material") { QeSelect(material, db.materials.map { it.id to it.name + " (" + it.unit + ")" }, { material = it }, Modifier.fillMaxWidth()) }
        OpsGrid(listOf(
            { m -> OpsLabel("Action", m) {
                QeSelect(type, listOf("purchase" to "Purchase / receive", "issue" to "Issue to worker / project", "return" to "Return unused material"), { type = it }, Modifier.fillMaxWidth())
            } },
            { m -> OpsLabel("Quantity", m) { QeNumberField(qty, { qty = it }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Employee", m) { QeTextField(worker, { worker = it }, Modifier.fillMaxWidth(), placeholder = "Name") } },
            { m -> OpsLabel("Project", m) { QeSelect(project, listOf("" to "Unassigned") + db.projects.map { it.id to it.name }, { project = it }, Modifier.fillMaxWidth()) } },
        ))
        Row { QePrimaryButton("Record transaction", onClick = { ovm.recordMovement(material, qty, type, worker, project) }) }
        Small("Submitted transactions cannot be edited. Owner can record a compensating correction with an audit trail. Future worker corrections will require owner approval.", Modifier.padding(top = 6.dp))
    }
    OpsCard {
        H3("Material transaction history")
        val lines = QeOps.movementLines(db)
        if (lines.isEmpty()) Text("No transactions.", color = QeColors.Text)
        lines.forEach { Text(it, color = QeColors.Text, modifier = Modifier.padding(vertical = 6.dp)) }
    }
}

/** `#machines`: Register machine + machine cards (status select, assigned worker committed on change). */
@Composable
private fun MachinesSection(ovm: OperationsViewModel) {
    val db by ovm.ops.collectAsState()
    H2("Machines & Tools")
    var name by rememberSaveable { mutableStateOf("") }
    var serial by rememberSaveable { mutableStateOf("") }
    var worker by rememberSaveable { mutableStateOf("") }
    OpsCard {
        H3("Register machine")
        OpsGrid(listOf(
            { m -> OpsLabel("Name", m) { QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Grinder") } },
            { m -> OpsLabel("Serial / model", m) { QeTextField(serial, { serial = it }, Modifier.fillMaxWidth()) } },
            { m -> OpsLabel("Assigned employee", m) { QeTextField(worker, { worker = it }, Modifier.fillMaxWidth()) } },
        ))
        Row { QePrimaryButton("Register", onClick = { ovm.addMachine(name, serial, worker) }) }
    }
    db.machines.forEach { m ->
        OpsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(QeOps.machineTitle(m), color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                Text(m.status, color = QeColors.PillText, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(QeColors.Pill).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Small(QeOps.machineDetail(m), Modifier.padding(top = 4.dp, bottom = 8.dp))
            OpsLabel("Status") { QeSelect(m.status, QeOps.MACHINE_STATUSES.map { it to it }, { ovm.changeMachine(m.id, it, null) }, Modifier.fillMaxWidth()) }
            OpsLabel("Assigned worker") { ChangeOnBlurField(m.worker, Modifier.fillMaxWidth()) { ovm.changeMachine(m.id, null, it) } }
            Small(QeOps.machineLookup(m))
        }
    }
}

/** `#reports`: Monthly Summary for `#reportMonth` (type=month → YYYY-MM field with previous / next month). */
@Composable
private fun ReportsSection(ovm: OperationsViewModel) {
    val db by ovm.ops.collectAsState()
    val month by ovm.reportMonth.collectAsState()
    H2("Monthly Summary")
    OpsCard {
        OpsLabel("Month") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                fun shift(d: Long) = runCatching { java.time.YearMonth.parse(month).plusMonths(d).toString() }.getOrDefault(month)
                QeSecondaryButton("‹", onClick = { ovm.setReportMonth(shift(-1)) })
                QeTextField(month, { ovm.setReportMonth(it) }, Modifier.width(140.dp), placeholder = "YYYY-MM")
                QeSecondaryButton("›", onClick = { ovm.setReportMonth(shift(1)) })
            }
        }
        QeOps.reportLines(ovm.reportFor(db, month)).forEach { (label, value) ->
            Text(androidx.compose.ui.text.buildAnnotatedString {
                append(label)
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append(value); pop()
            }, color = QeColors.Text, modifier = Modifier.padding(vertical = 6.dp))
        }
        Small(QeOps.REPORT_NOTE)
    }
}
