package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.AppViewModel
import com.moraswood.qande.data.DwDefaults
import com.moraswood.qande.data.DwStructure
import com.moraswood.qande.logic.QeDoorWindow
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.QeCard
import com.moraswood.qande.ui.components.QeCheckbox
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QeFieldLabel
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.components.QeTh
import com.moraswood.qande.ui.theme.QeColors

/** Feature I — Door & Window Builder for the current project. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoorWindowScreen(vm: AppViewModel, onOpenCalculator: () -> Unit, onPrintCutList: (() -> Unit)? = null) = ToolPage {
    val ws by vm.workspace.collectAsState()
    val defaults by vm.dwDefaults.collectAsState()
    val templates by vm.dwTemplates.collectAsState()
    val list = ws.doorWindows.orEmpty()
    var status by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    QeConfirm(confirmRemove?.let { "Remove this structure?" }) { ok -> val id = confirmRemove; confirmRemove = null; if (ok && id != null) vm.removeStructure(id) }

    PageHeading("Door & Window Builder", "Frames, centre supports, planking and architrave → editable CFT rows and a cut list.")
    QeCard(Modifier.padding(bottom = 12.dp)) {
        Text(ws.projectName.ifBlank { "Current project" }, color = QeColors.Text, fontWeight = FontWeight.Bold)
        QeFieldLabel("Wastage per cut for this job") {
            QeSelect(ws.dwWastage.orEmpty(), listOf("" to "Settings default (${QeDoorWindow.fmt(QeMath.num(defaults.wastage))} in)") + QeDoorWindow.WASTAGE, vm::setDwWastage, Modifier.fillMaxWidth())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QeDoorWindow.KINDS.forEach { (k, label) -> QeSecondaryButton("+ $label") { vm.addStructure(k) } }
        }
        if (templates.isNotEmpty()) {
            Text("Templates", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            templates.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(QeDoorWindow.title(t), color = QeColors.Text, modifier = Modifier.weight(1f))
                    QeSecondaryButton("Use") { vm.useDwTemplate(i) }
                    QeDangerButton("Delete") { vm.deleteDwTemplate(i) }
                }
            }
        }
        if (status.isNotEmpty()) Text(status, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }

    list.forEach { st ->
        StructureCard(st, ws.dwWastage, defaults,
            onChange = { change -> vm.updateStructure(st.id, change) },
            onAddToCalc = { val n = vm.addStructuresToCalculator(listOf(st.id)); if (n > 0) onOpenCalculator() else status = "Nothing to add — check the sizes." },
            onDuplicate = { vm.duplicateStructure(st.id) },
            onTemplate = { status = vm.saveDwTemplate(st.id, st.name) },
            onRemove = { confirmRemove = st.id })
    }
    if (list.isEmpty()) Text("Add a main door, bedroom door, other door or window to start.", color = QeColors.Muted)
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        QePrimaryButton("Add all to calculator") { val n = vm.addStructuresToCalculator(list.map { it.id }); if (n > 0) onOpenCalculator() else status = "Nothing to add — check the sizes." }
        if (onPrintCutList != null) QeSecondaryButton("Print cut list", onClick = onPrintCutList)
    }
}

@Composable
private fun NumField(label: String, value: String, placeholder: String = "", modifier: Modifier = Modifier, onChange: (String) -> Unit) =
    QeFieldLabel(label, modifier) { QeNumberField(value, onChange, Modifier.fillMaxWidth(), placeholder = placeholder) }

private val unitOptions = QeMath.lengthUnits.map { it to it }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StructureCard(
    st: DwStructure, jobWastage: String?, defaults: DwDefaults,
    onChange: ((DwStructure) -> DwStructure) -> Unit,
    onAddToCalc: () -> Unit, onDuplicate: () -> Unit, onTemplate: () -> Unit, onRemove: () -> Unit,
) {
    val r = QeDoorWindow.compute(st, jobWastage, defaults)
    val (fw, ft) = QeDoorWindow.wood(st)
    val window = QeDoorWindow.isWindow(st)
    QeCard(Modifier.padding(bottom = 12.dp)) {
        Text(QeDoorWindow.title(st), color = QeColors.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QeFieldLabel("Name", Modifier.weight(1.3f)) { QeTextField(st.name, { v -> onChange { it.copy(name = v) } }, Modifier.fillMaxWidth(), placeholder = QeDoorWindow.kindLabel(st.kind)) }
            QeFieldLabel("Type", Modifier.weight(1f)) { QeSelect(st.kind, QeDoorWindow.KINDS, { v -> onChange { it.copy(kind = v) } }, Modifier.fillMaxWidth()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            NumField("Length (height)", st.height, modifier = Modifier.weight(1f)) { v -> onChange { it.copy(height = v) } }
            QeSelect(st.heightUnit.ifEmpty { "ft" }, unitOptions, { v -> onChange { it.copy(heightUnit = v) } }, Modifier.width(72.dp).padding(bottom = 10.dp))
            NumField("Width", st.width, modifier = Modifier.weight(1f)) { v -> onChange { it.copy(width = v) } }
            QeSelect(st.widthUnit.ifEmpty { "ft" }, unitOptions, { v -> onChange { it.copy(widthUnit = v) } }, Modifier.width(72.dp).padding(bottom = 10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Quantity (identical sets)", st.quantity, modifier = Modifier.weight(1f)) { v -> onChange { it.copy(quantity = v) } }
            QeFieldLabel("Species", Modifier.weight(1f)) { QeTextField(st.species, { v -> onChange { it.copy(species = v) } }, Modifier.fillMaxWidth(), placeholder = "e.g. Teak") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QeFieldLabel("Wood size (in)", Modifier.weight(1f)) { QeSelect(st.wood, QeDoorWindow.WOODS, { v -> onChange { it.copy(wood = v) } }, Modifier.fillMaxWidth()) }
            if (st.wood == "custom") {
                NumField("Width in", st.woodW, modifier = Modifier.weight(0.7f)) { v -> onChange { it.copy(woodW = v) } }
                NumField("Thick in", st.woodT, modifier = Modifier.weight(0.7f)) { v -> onChange { it.copy(woodT = v) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Long pieces", st.longs, "${QeDoorWindow.defaultLongs(st.kind)}", Modifier.weight(1f)) { v -> onChange { it.copy(longs = v) } }
            NumField("Short pieces", st.shorts, "${QeDoorWindow.defaultShorts(st.kind)}", Modifier.weight(1f)) { v -> onChange { it.copy(shorts = v) } }
            if (window) QeFieldLabel("Centre supports", Modifier.weight(1f)) { QeSelect(if (st.supports == "2") "2" else "1", listOf("1" to "Single centre", "2" to "Two supports"), { v -> onChange { it.copy(supports = v) } }, Modifier.fillMaxWidth()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QeFieldLabel("Mounting style", Modifier.weight(1f)) { QeSelect(if (st.mounting == "screw") "screw" else "juttu", listOf("juttu" to "JUTTU", "screw" to "SCREW"), { v -> onChange { it.copy(mounting = v) } }, Modifier.fillMaxWidth()) }
            if (st.mounting != "screw") NumField("Juttu extension per end (in)", st.juttuExt, "${QeDoorWindow.fmt(QeMath.num(defaults.juttuExt))} (default)", Modifier.weight(1f)) { v -> onChange { it.copy(juttuExt = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Subtracted dimension (in)", st.subtractDim, "${QeDoorWindow.fmt(ft)} (thickness)", Modifier.weight(1f)) { v -> onChange { it.copy(subtractDim = v) } }
            QeFieldLabel("Wastage per cut", Modifier.weight(1f)) {
                QeSelect(st.wastage, listOf("" to "Job default") + QeDoorWindow.WASTAGE, { v -> onChange { it.copy(wastage = v) } }, Modifier.fillMaxWidth())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            QeCheckbox(st.architrave) { v -> onChange { it.copy(architrave = v) } }
            Text("Architrave", color = QeColors.Text)
        }
        if (st.architrave) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumField("Architrave width (in)", st.archW, modifier = Modifier.weight(1f)) { v -> onChange { it.copy(archW = v) } }
            NumField("Architrave thickness (in)", st.archT, modifier = Modifier.weight(1f)) { v -> onChange { it.copy(archT = v) } }
            if (window) { QeCheckbox(st.archBottom) { v -> onChange { it.copy(archBottom = v) } }; Text("Include bottom piece", color = QeColors.Text, fontSize = 13.sp) }
        }
        if (!window) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QeCheckbox(st.planking) { v -> onChange { it.copy(planking = v) } }
                Text("Planking", color = QeColors.Text)
            }
            if (st.planking) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QeFieldLabel("Side supports", Modifier.weight(1f)) { QeSelect(if (st.plankSide == "4") "4" else "5", listOf("4" to "4 in", "5" to "5 in"), { v -> onChange { it.copy(plankSide = v) } }, Modifier.fillMaxWidth()) }
                QeFieldLabel("Plank width", Modifier.weight(1f)) { QeSelect(st.plankWidth, QeDoorWindow.PLANK_WIDTHS.map { it to if (it == "custom") "Custom" else "$it in" }, { v -> onChange { it.copy(plankWidth = v) } }, Modifier.fillMaxWidth()) }
                if (st.plankWidth == "custom") NumField("Custom in", st.plankWidthCustom, modifier = Modifier.weight(0.8f)) { v -> onChange { it.copy(plankWidthCustom = v) } }
                NumField("Plank thick (in)", st.plankT, "${QeDoorWindow.fmt(ft)} (frame)", Modifier.weight(1f)) { v -> onChange { it.copy(plankT = v) } }
            }
        }

        r.warnings.forEach { Text(it, color = QeColors.DangerText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (r.pieces.isNotEmpty()) PiecesTable(r)
        Details("Geometry assumptions") { QeDoorWindow.assumptions(st, jobWastage, defaults).forEach { Text("• $it", color = QeColors.Muted, fontSize = 13.sp) } }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QePrimaryButton("Add to calculator", onClick = onAddToCalc)
            QeSecondaryButton("Duplicate", onClick = onDuplicate)
            QeSecondaryButton("Save as template", onClick = onTemplate)
            QeDangerButton("Remove", onClick = onRemove)
        }
    }
}

private val PW: List<Dp> = listOf(170.dp, 90.dp, 90.dp, 70.dp, 70.dp, 60.dp, 80.dp)

@Composable
private fun PiecesTable(r: QeDoorWindow.Result) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Row { listOf("Piece", "Finished in", "Cut in", "W in", "T in", "Qty", "CFT").forEachIndexed { k, h -> QeTh(h, PW[k]) } }
        r.pieces.forEach { p ->
            Box(Modifier.width(PW.fold(0.dp) { a, b -> a + b }).height(1.dp).background(QeColors.CellLine))
            Row(Modifier.padding(vertical = 6.dp)) {
                listOf(p.label, QeDoorWindow.fmt(p.finishedIn), QeDoorWindow.fmt(p.cutIn), QeDoorWindow.fmt(p.widthIn), QeDoorWindow.fmt(p.thicknessIn), "${p.qty}", QeFormat.toFixed(p.cft, 3))
                    .forEachIndexed { k, t -> Text(t, color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.width(PW[k]).padding(horizontal = 6.dp)) }
            }
        }
        Text("Total ${r.totalPieces} pieces · ${QeFormat.toFixed(r.totalCft, 3)} CFT", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Settings card: builder defaults. */
@Composable
fun BuilderDefaultsCard(d: DwDefaults, onChange: (DwDefaults) -> Unit) = QeCard(Modifier.padding(top = 16.dp)) {
    Text("Door & Window Builder defaults", color = QeColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Text("Used when a structure or job leaves the field blank. The Juttu extension of 3 in is a placeholder — please confirm.", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
    QeFieldLabel("Juttu extension per end (in)") { QeNumberField(d.juttuExt, { onChange(d.copy(juttuExt = it)) }, Modifier.fillMaxWidth()) }
    QeFieldLabel("Wastage per cut") { QeSelect(d.wastage.ifEmpty { "0" }, QeDoorWindow.WASTAGE, { onChange(d.copy(wastage = it)) }, Modifier.fillMaxWidth()) }
}
