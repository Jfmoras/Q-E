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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.R
import com.moraswood.qande.data.QuoteItem
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath.num
import com.moraswood.qande.logic.QeQuote
import com.moraswood.qande.photos.QuoteImages
import com.moraswood.qande.ui.components.QeAlert
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QeFieldLabel
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QeCheckbox
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors
import kotlinx.coroutines.launch

/** Quotation tab callbacks (index.html `#quotation` + app.js quotation handlers). */
data class QuotationActions(
    val onPrint: () -> Unit,
    val onExport: suspend () -> String?,
    val onCustomerName: (String) -> Unit,
    val onCustomerLocation: (String) -> Unit,
    val onSelectOption: (Int) -> Unit,
    val onOptionName: (String) -> Unit,
    val onAddOption: () -> Unit,
    val onDuplicateOption: () -> Unit,
    val canRemoveOption: () -> String?,
    val onRemoveOption: () -> Unit,
    val onField: (Int, String, String) -> Unit,
    val onRemoveItem: (Int) -> Unit,
    val onAddItem: () -> Unit,
    val onImprove: (Int, String) -> Unit,
    val onUndoAi: (Int) -> Unit,
    val onAddImages: suspend (Int, List<android.net.Uri>) -> String,
    val onRemoveImage: (Int, Int) -> Unit,
    val onDiscountType: (String) -> Unit,
    val onDiscountValue: (String) -> Unit,
    val onGst: (String) -> Unit,
    val onAddNote: () -> Unit,
    val onNote: (Int, String) -> Unit,
    val onSaveNote: (Int) -> Unit,
    val onRemoveNote: (Int) -> Unit,
    val onAddSavedNote: (Int) -> Unit,
    val onDeleteSavedNote: (Int) -> Unit,
    /** Native addition (Jason): edit a saved note in place. */
    val onEditSavedNote: (Int, String) -> Unit = { _, _ -> },
    /** Feature D: duplicate the whole option as a wood-species option (species, Rate/CFT, Rate/KOL) → status line. */
    val onDuplicateAsSpecies: (String, String, String) -> String = { _, _, _ -> "" },
    val onOptionSpecies: (String) -> Unit = {},
    val onPrintComparison: (Boolean) -> Unit = {},
    /** Feature O: client picker (null = no client) and "Save as client" → status line. */
    val onPickClient: (String?) -> Unit = {},
    val onSaveAsClient: () -> String = { "" },
    /** Feature P: delivery date (ISO yyyy-MM-dd, null clears). */
    val onDeliveryDate: (String?) -> Unit = {},
    /** Projects & Payments: turn the saved quotation into a project (null = done, else a message). */
    val onConfirmProject: ((String) -> Unit) -> Unit = {},
    /** Feature Q: client signature pad (base64 PNG, signer) / clear. */
    val onSaveSignature: (String, String) -> Unit = { _, _ -> },
    val onClearSignature: () -> Unit = {},
    /** Feature G: show each line's area on the print. */
    val onPrintArea: (Boolean) -> Unit = {},
    /** Feature J: group quotation lines by category on print. */
    val onGroupByCategory: (Boolean) -> Unit = {},
)

private val UnitOptions = listOf("Set", "Piece", "Nos", "Lumpsum", "Sq.ft", "Rft", "Job")
private val AiStyles = listOf("professional" to "Professional", "concise" to "Short & clear", "detailed" to "Detailed")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotationPanel(
    state: WorkspaceState,
    savedNotes: List<String>,
    noteStatus: String,
    aiStatus: Map<Int, String>,
    aiBusy: Set<Int>,
    actions: QuotationActions,
    clients: List<com.moraswood.qande.data.ClientEntity> = emptyList(),
    signature: com.moraswood.qande.data.SignatureEntity? = null,
) {
    val scope = rememberCoroutineScope()
    var alert by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    QeAlert(alert) { alert = null }
    QeConfirm(if (confirmRemove) "Remove this quotation option?" else null) { ok -> confirmRemove = false; if (ok) actions.onRemoveOption() }

    var speciesDialog by remember { mutableStateOf(false) }
    var speciesStatus by remember { mutableStateOf("") }
    if (speciesDialog) SpeciesDialog(onDismiss = { speciesDialog = false }) { sp, cft, kol ->
        speciesDialog = false
        speciesStatus = actions.onDuplicateAsSpecies(sp, cft, kol)
    }

    // .quote-actions
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End) {
        QePrimaryButton("Print quotation", onClick = actions.onPrint)
        QeSecondaryButton("Export to Projects", onClick = { scope.launch { actions.onExport()?.let { alert = it } } })
        QeSecondaryButton("Confirm as Project", onClick = { actions.onConfirmProject { alert = it } })
    }
    // Feature D: print the official (active) option; other options only as an opt-in appendix.
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        QeCheckbox(state.printComparison == true, actions.onPrintComparison)
        Text("Include other options as comparison appendix", color = QeColors.Text, fontSize = 14.sp)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        QeCheckbox(state.printArea == true, actions.onPrintArea)
        Text("Show area (sq.ft) on print", color = QeColors.Text, fontSize = 14.sp)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        QeCheckbox(state.quoteGroupByCategory == true, actions.onGroupByCategory)
        Text("Group lines by category on print", color = QeColors.Text, fontSize = 14.sp)
    }

    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 650.dp
        Column(
            Modifier.widthIn(max = 900.dp).fillMaxWidth().align(Alignment.TopCenter).clip(shape).background(QeColors.Card)
                .border(1.dp, QeColors.Line, shape).padding(if (wide) 36.dp else 16.dp),
        ) {
            // .quote-editor-head
            Row(verticalAlignment = if (wide) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.mwi_logo), "MWI", Modifier.size(70.dp), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text("MORAS WOOD INDUSTRIES", color = QeColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Moorukaveri, Kinnigoli, Mangaluru - 574150", color = QeColors.Text, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Text("+91 63616 95416 | info@moraswood.com | GSTIN: 29AFPPM1242L1Z1", color = QeColors.Text, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(QeColors.Line))
            // .quote-title-line
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("QUOTATION", color = QeColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Date: " + QeQuote.longDate(), color = QeColors.Text, fontSize = 16.sp)
            }
            // Feature O (native, screen only): pick a saved client to fill the customer block.
            var clientStatus by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val linked = state.clientId?.takeIf { id -> clients.any { it.id == id } }
                QeSelect(linked, listOf<Pair<String?, String>>(null to (if (clients.isEmpty()) "No saved clients" else "Client: none")) + clients.map { it.id to ("Client: " + it.name + (if (it.phone.isNotBlank()) " · " + it.phone else "")) },
                    { clientStatus = ""; actions.onPickClient(it) }, Modifier.weight(1f))
                if (linked == null) QeSecondaryButton("Save as client", onClick = { clientStatus = actions.onSaveAsClient() })
            }
            if (clientStatus.isNotEmpty()) Text(clientStatus, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            DeliveryDateRow(state.deliveryDate, actions.onDeliveryDate)
            SignatureRow(signature, state.customer.name, actions.onSaveSignature, actions.onClearSignature)
            // .customer-grid
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QeTextField(state.customer.name, actions.onCustomerName, Modifier.weight(1f), placeholder = "Customer name")
                QeTextField(state.customer.location, actions.onCustomerLocation, Modifier.weight(1f), placeholder = "Customer location")
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QeTextField(state.customer.name, actions.onCustomerName, Modifier.fillMaxWidth(), placeholder = "Customer name")
                QeTextField(state.customer.location, actions.onCustomerLocation, Modifier.fillMaxWidth(), placeholder = "Customer location")
            }

            // .quote-option-editor
            val opts = state.quoteOptions
            val active = state.activeQuoteOption.coerceIn(0, maxOf(0, opts.size - 1))
            Column(
                Modifier.padding(vertical = 14.dp).fillMaxWidth().border(1.dp, Color(0xFFD6D3D1), RoundedCornerShape(10.dp)).padding(14.dp),
            ) {
                val selectField: @Composable (Modifier) -> Unit = { m ->
                    QeFieldLabel("Quotation option", m) {
                        QeSelect(active, opts.mapIndexed { i, o -> i to o.name.ifEmpty { "Option " + (i + 1) } }, actions.onSelectOption, Modifier.fillMaxWidth())
                    }
                }
                val nameField: @Composable (Modifier) -> Unit = { m ->
                    QeFieldLabel("Option name", m) {
                        QeTextField(opts.getOrNull(active)?.name.orEmpty(), actions.onOptionName, Modifier.fillMaxWidth(), placeholder = "Material, grade or design")
                    }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { selectField(Modifier.weight(1f)); nameField(Modifier.weight(1f)) }
                else { selectField(Modifier.fillMaxWidth()); nameField(Modifier.fillMaxWidth()) }
                QeFieldLabel("Wood species") {
                    QeTextField(opts.getOrNull(active)?.species.orEmpty(), actions.onOptionSpecies, Modifier.fillMaxWidth(), placeholder = "e.g. Teak, Sal, Honne (optional)")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QeSecondaryButton("+ Add option", onClick = actions.onAddOption)
                    QeSecondaryButton("Duplicate option", onClick = actions.onDuplicateOption)
                    QeSecondaryButton("Duplicate as new option (wood species)", onClick = { speciesDialog = true })
                    QeDangerButton("Remove option") { val msg = actions.canRemoveOption(); if (msg != null) alert = msg else confirmRemove = true }
                }
                if (speciesStatus.isNotEmpty()) Text(speciesStatus, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (opts.size > 1) OptionsPricing(opts, active, actions.onSelectOption)
            }

            // #quotationItems
            Column(verticalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.padding(top = 2.dp, bottom = 13.dp)) {
                state.quoteItems.forEachIndexed { i, it ->
                    QuoteItemCard(i, it, wide, aiStatus[i].orEmpty(), i in aiBusy, actions, onAlert = { m -> alert = m })
                }
            }
            Row { QeSecondaryButton("+ Add quotation item", onClick = actions.onAddItem) }

            // .quote-controls
            Column(
                Modifier.padding(top = 18.dp).fillMaxWidth().clip(shape).background(QeColors.Soft).border(1.dp, QeColors.Line, shape).padding(12.dp),
            ) {
                val type: @Composable (Modifier) -> Unit = { m ->
                    QeFieldLabel("Discount type", m) {
                        QeSelect(state.quote.discountType, listOf("amount" to "₹ Amount", "percent" to "Percent %"), actions.onDiscountType, Modifier.fillMaxWidth())
                    }
                }
                val value: @Composable (Modifier) -> Unit = { m -> QeFieldLabel("Discount", m) { QeNumberField(state.quote.discountValue, actions.onDiscountValue, Modifier.fillMaxWidth()) } }
                val gst: @Composable (Modifier) -> Unit = { m -> QeFieldLabel("GST %", m) { QeNumberField(state.quote.gstPercent, actions.onGst, Modifier.fillMaxWidth()) } }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { type(Modifier.weight(1f)); value(Modifier.weight(1f)); gst(Modifier.weight(1f)) }
                else { type(Modifier.fillMaxWidth()); value(Modifier.fillMaxWidth()); gst(Modifier.fillMaxWidth()) }
            }

            // #quoteTotalsScreen (renderQuoteTotals)
            val t = QeQuote.quoteTotals(state)
            Column(Modifier.padding(top = 18.dp).widthIn(max = 430.dp).fillMaxWidth().align(Alignment.End)) {
                TotalLine("Total Price", QeFormat.money(t.subtotal))
                TotalLine(QeQuote.discountLabel(state.quote.discountType, state.quote.discountValue), "- " + QeFormat.money(t.discount))
                TotalLine("GST (${QeQuote.jsNumber(num(state.quote.gstPercent))}%)", QeFormat.money(t.gst))
                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(2.dp).background(Color(0xFF171C1E)))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grand Total", color = QeColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(QeFormat.money(t.grand), color = QeColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // .notes-editor
            Box(Modifier.padding(top = 22.dp).fillMaxWidth().height(1.dp).background(QeColors.Line))
            Text("Notes", color = QeColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
            var selected by remember(savedNotes) { mutableStateOf<Int?>(null) }
            val selectNote: @Composable (Modifier) -> Unit = { m ->
                QeSelect(selected, listOf<Pair<Int?, String>>(null to "Select saved note") + savedNotes.mapIndexed { i, x -> i to x }, { selected = it }, m)
            }
            val addSaved = @Composable { QeSecondaryButton("Add saved note", onClick = { selected?.let(actions.onAddSavedNote) }) }
            val delSaved = @Composable { QeSecondaryButton("Delete saved", onClick = { selected?.let(actions.onDeleteSavedNote) }) }
            var editing by remember(savedNotes) { mutableStateOf<Pair<Int, String>?>(null) }
            val editSaved = @Composable { QeSecondaryButton("Edit saved", onClick = { selected?.let { i -> savedNotes.getOrNull(i)?.let { editing = i to it } } }) }
            if (wide) Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                selectNote(Modifier.weight(1f)); addSaved(); editSaved(); delSaved()
            } else Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectNote(Modifier.fillMaxWidth()); addSaved(); editSaved(); delSaved()
            }
            editing?.let { (idx, text) ->
                Column(Modifier.padding(bottom = 12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QeTextField(text, { editing = idx to it }, Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QeSecondaryButton("Save", onClick = { actions.onEditSavedNote(idx, text); if (text.isNotBlank()) editing = null })
                        QeSecondaryButton("Cancel", onClick = { editing = null })
                    }
                }
            }
            state.quote.notes.forEachIndexed { i, note ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", color = QeColors.Text, modifier = Modifier.padding(top = 9.dp))
                    Column(Modifier.weight(1f)) {
                        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QeTextField(note, { v -> actions.onNote(i, v) }, Modifier.weight(1f), placeholder = "Add note", singleLine = false, minLines = 2)
                            QeSecondaryButton("Save", onClick = { actions.onSaveNote(i) })
                            QeSecondaryButton("Remove", onClick = { actions.onRemoveNote(i) })
                        } else {
                            QeTextField(note, { v -> actions.onNote(i, v) }, Modifier.fillMaxWidth(), placeholder = "Add note", singleLine = false, minLines = 2)
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QeSecondaryButton("Save", onClick = { actions.onSaveNote(i) })
                                QeSecondaryButton("Remove", onClick = { actions.onRemoveNote(i) })
                            }
                        }
                    }
                }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.Center) {
                QeSecondaryButton("+ Add note", onClick = actions.onAddNote)
                if (noteStatus.isNotEmpty()) Text(noteStatus, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = QeColors.Text)
        Text(value, color = QeColors.Text, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteItemCard(
    i: Int, it: QuoteItem, wide: Boolean, status: String, busy: Boolean, actions: QuotationActions, onAlert: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(16.dp)
    var style by rememberSaveable(i) { mutableStateOf("professional") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) scope.launch { onAlert(actions.onAddImages(i, uris)) }
    }
    Column(Modifier.fillMaxWidth().clip(shape).background(QeColors.Card).border(1.dp, QeColors.Line, shape).padding(13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Item ${i + 1}", color = QeColors.Text, fontWeight = FontWeight.Bold)
            QeDangerButton("Remove") { actions.onRemoveItem(i) }
        }
        Column(Modifier.padding(top = 10.dp)) {
            QeFieldLabel("Description") {
                Column {
                    QeTextField(it.description, { v -> actions.onField(i, "description", v) }, Modifier.fillMaxWidth(),
                        placeholder = "Write the item description in your own words", singleLine = false, minLines = 4)
                    // .ai-desc-tools
                    val improve = @Composable { m: Modifier -> QeSecondaryButton(if (busy) "Improving…" else "✨ Improve with AI", m, enabled = !busy) { actions.onImprove(i, style) } }
                    val undo = @Composable { m: Modifier -> if (it.aiOriginal.isNotEmpty()) QeSecondaryButton("Undo AI", m) { actions.onUndoAi(i) } }
                    val styleSel = @Composable { m: Modifier -> QeSelect(style, AiStyles, { s -> style = s }, m) }
                    val statusText = @Composable { m: Modifier -> Text(status, color = QeColors.Muted, fontSize = 13.sp, modifier = m) }
                    if (wide) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        styleSel(Modifier.widthIn(min = 150.dp, max = 190.dp)); improve(Modifier); undo(Modifier); statusText(Modifier.weight(1f))
                    } else Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        styleSel(Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { improve(Modifier.weight(1f)); if (it.aiOriginal.isNotEmpty()) undo(Modifier.weight(1f)) }
                        if (status.isNotEmpty()) statusText(Modifier)
                    }
                }
            }
            val units: @Composable (Modifier) -> Unit = { m -> QeFieldLabel("Units", m) { UnitInput(it.unit) { v -> actions.onField(i, "unit", v) } } }
            val qty: @Composable (Modifier) -> Unit = { m -> QeFieldLabel("Quantity", m) { QeNumberField(it.qty, { v -> actions.onField(i, "qty", v) }, Modifier.fillMaxWidth()) } }
            val price: @Composable (Modifier) -> Unit = { m -> QeFieldLabel("Unit price", m) { QeNumberField(it.unitPrice, { v -> actions.onField(i, "unitPrice", v) }, Modifier.fillMaxWidth()) } }
            val total: @Composable (Modifier) -> Unit = { m ->
                Column(m.padding(bottom = 10.dp).clip(RoundedCornerShape(9.dp)).background(QeColors.Soft).padding(13.dp)) {
                    Text("Total", color = QeColors.Muted, fontSize = 12.sp)
                    Text(QeFormat.money(num(it.qty) * num(it.unitPrice)), color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
                units(Modifier.weight(0.75f)); qty(Modifier.weight(0.7f)); price(Modifier.weight(1f)); total(Modifier.weight(1f))
            } else { units(Modifier.fillMaxWidth()); qty(Modifier.fillMaxWidth()); price(Modifier.fillMaxWidth()); total(Modifier.fillMaxWidth()) }
            QeFieldLabel("Wood species (optional)") {
                QeTextField(it.species.orEmpty(), { v -> actions.onField(i, "species", v) }, Modifier.fillMaxWidth(), placeholder = "Species for this line")
            }
            // Feature J: category of the line
            QeFieldLabel("Category") {
                QeSelect(com.moraswood.qande.logic.QeCategories.of(it.category), com.moraswood.qande.logic.QeCategories.ALL, { v -> actions.onField(i, "category", v) }, Modifier.fillMaxWidth())
            }
            // Feature G: area carried from Estimation (editable)
            QeFieldLabel("Area (sq.ft, optional)") {
                QeTextField(it.area.orEmpty(), { v -> actions.onField(i, "area", v) }, Modifier.fillMaxWidth(), placeholder = "From estimation")
            }
            it.wood?.let { w ->
                Text(
                    "Timber link: ${"%.2f".format(w.cft)} CFT · ${"%.2f".format(w.kol)} KOL · wood ${QeFormat.money(w.woodCft + w.woodKol)}",
                    color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        // .quote-image-actions
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QeSecondaryButton("+ Add images") { picker.launch("image/*") }
            Text("Up to 6 images per item", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
        if (it.images.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                val cols = maxOf(1, ((maxWidth.value + 8) / (110 + 8)).toInt())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    it.images.withIndex().chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (j, src) -> ImageThumb(src, Modifier.weight(1f)) { actions.onRemoveImage(i, j) } }
                            repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** `<input list="unitOptions" placeholder="Set / Piece / Lumpsum">` — free text plus the datalist suggestions. */
@Composable
private fun UnitInput(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QeTextField(value, onChange, Modifier.weight(1f), placeholder = "Set / Piece / Lumpsum")
            Text("▾", color = QeColors.Muted, fontSize = 14.sp, modifier = Modifier.clickable { open = true }.padding(horizontal = 8.dp, vertical = 10.dp))
        }
        val matches = UnitOptions.filter { o -> value.isEmpty() || o.contains(value, ignoreCase = true) }.ifEmpty { UnitOptions }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(QeColors.InputBg)) {
            matches.forEach { o -> DropdownMenuItem(text = { Text(o, color = QeColors.InputText) }, onClick = { open = false; onChange(o) }) }
        }
    }
}

@Composable
private fun ImageThumb(src: String, modifier: Modifier, onRemove: () -> Unit) {
    var bmp by remember(src) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(src) { bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { QuoteImages.decode(src, 360) } }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.aspectRatio(4f / 3f).clip(shape).border(1.dp, QeColors.Line, shape).background(QeColors.Bg)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(28.dp).clip(CircleShape).background(Color(0xE8FFFFFF)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Text("×", color = Color(0xFF1C1917), fontSize = 20.sp) }
    }
}

/** `#review` — Actual Cost / Job Review. */
@Composable
fun ReviewPanel(state: WorkspaceState, onMaterials: (String) -> Unit, onLabourHours: (String) -> Unit) {
    com.moraswood.qande.ui.components.PageHeading("Actual Cost / Job Review", "Compare what you estimated with what actually happened.")
    com.moraswood.qande.ui.components.QeCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 640.dp
            val metric: @Composable (Modifier) -> Unit = { m ->
                Column(m.clip(RoundedCornerShape(9.dp)).background(QeColors.Soft).padding(13.dp)) {
                    Text("Estimated Cost", color = QeColors.Muted, fontSize = 12.sp)
                    Text(QeFormat.money(com.moraswood.qande.logic.QeEstimate.estimateGrand(state).cost), color = QeColors.Text, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                }
            }
            val mat: @Composable (Modifier) -> Unit = { m -> QeFieldLabel("Actual material + consumables", m) { QeNumberField(state.actual.materials, onMaterials, Modifier.fillMaxWidth()) } }
            val hrs: @Composable (Modifier) -> Unit = { m -> QeFieldLabel("Actual labour hours", m) { QeNumberField(state.actual.labourHours, onLabourHours, Modifier.fillMaxWidth()) } }
            if (wide) Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { metric(Modifier.weight(1f)); mat(Modifier.weight(1f)); hrs(Modifier.weight(1f)) }
            else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { metric(Modifier.fillMaxWidth()); mat(Modifier.fillMaxWidth()); hrs(Modifier.fillMaxWidth()) }
        }
        Text("Use this after job completion to compare estimated costs against actual material and labour usage.",
            color = QeColors.Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
    }
}


/** Feature D: every option with its full pricing; the active option carries the official total. */
@Composable
private fun OptionsPricing(opts: List<com.moraswood.qande.data.QuoteOption>, active: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(top = 12.dp).fillMaxWidth()) {
        Text("Options & pricing", color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        opts.forEachIndexed { i, o ->
            val t = QeQuote.totalsForOption(o)
            val on = i == active
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier.padding(bottom = 6.dp).fillMaxWidth().clip(shape).background(if (on) QeColors.GrandBg else QeColors.Soft)
                    .border(if (on) 2.dp else 1.dp, if (on) QeColors.Accent else QeColors.Line, shape).clickable { onSelect(i) }.padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(com.moraswood.qande.logic.QeSpecies.optionLabel(o, i), color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (on) Text("● Active — official total", color = QeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Total ${QeFormat.rs(t.subtotal)}" + (if (t.discount > 0) " · Discount - ${QeFormat.rs(t.discount)}" else "") +
                        " · GST (${QeQuote.jsNumber(num(o.gstPercent))}%) ${QeFormat.rs(t.gst)}",
                    color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                )
                Text("Grand total ${QeFormat.rs(t.grand)}", color = QeColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Feature D: species name + optional species rates for re-pricing linked lines. */
@Composable
private fun SpeciesDialog(onDismiss: () -> Unit, onOk: (String, String, String) -> Unit) {
    var sp by remember { mutableStateOf("") }
    var cft by remember { mutableStateOf("") }
    var kol by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QeColors.Card,
        title = { Text("Duplicate as new option", color = QeColors.Text) },
        text = {
            Column {
                Text("Copies every line, image, discount and GST of the current option. Lines sent from Estimation are re-priced from their CFT/KOL with the rates below; other lines keep their price.",
                    color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                QeFieldLabel("Wood species") { QeTextField(sp, { sp = it }, Modifier.fillMaxWidth(), placeholder = "e.g. Teak") }
                QeFieldLabel("Rate / CFT for this species") { QeNumberField(cft, { cft = it }, Modifier.fillMaxWidth()) }
                QeFieldLabel("Rate / KOL for this species") { QeNumberField(kol, { kol = it }, Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onOk(sp, cft, kol) }) { Text("Duplicate", color = QeColors.Accent) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = QeColors.Muted) } },
    )
}

/** Feature P (native, screen only): delivery date shown on the Delivery calendar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryDateRow(date: String?, onDate: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val parsed = com.moraswood.qande.logic.QeCalendar.parse(date)
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Delivery date:", color = QeColors.Muted, fontSize = 14.sp)
        QeSecondaryButton(parsed?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)) ?: "Set date", onClick = { open = true })
        if (parsed != null) QeSecondaryButton("Clear", onClick = { onDate(null) })
    }
    if (open) {
        val millis = (parsed ?: java.time.LocalDate.now()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = millis)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    st.selectedDateMillis?.let { onDate(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { androidx.compose.material3.DatePicker(st) }
    }
}
