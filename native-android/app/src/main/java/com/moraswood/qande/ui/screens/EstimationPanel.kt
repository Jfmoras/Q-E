package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.TemplateBanner
import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.logic.QeEstimate
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.ui.components.ItemPhotoBox
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.QeAlert
import com.moraswood.qande.ui.components.QeCheckbox
import com.moraswood.qande.ui.components.QeFieldLabel
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.components.QeTh
import com.moraswood.qande.ui.theme.QeColors

/** Callbacks for the Estimation tab (app.js renderEstimation handlers + native additions). */
class EstimationActions(
    val onName: (Int, String) -> Unit,
    val onField: (Int, String, String) -> Unit,
    /** Returns the alert text or null. */
    val onGroup: (Int, List<Int>) -> String?,
    val onUngroup: (Int, List<Int>) -> Unit,
    val onGroupRate: (Int, String, String) -> Unit,
    val onRemoveGroup: (Int, String) -> Unit,
    val onNewItem: () -> Unit,
    /** Returns the index of the item missing a selling price, or -1. */
    val onSendToQuotation: () -> Int,
    /** Returns false when the selling price is missing. */
    val onSendOne: (Int) -> Boolean,
    // native additions
    val onPhoto: (Int, String) -> Unit,
    val onUseTemplate: (TemplateBanner) -> Unit,
    val onDismissTemplate: (TemplateBanner) -> Unit,
)

fun cssHex(hex: String, fallback: Color = Color(0xFF3B82F6)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/** `#estimation` panel. */
@Composable
fun EstimationPanel(
    items: List<CalcItem>,
    costings: List<Costing>,
    status: Map<Int, String>,
    banner: TemplateBanner?,
    actions: EstimationActions,
) {
    PageHeading("Estimation & Rate Builder", "Price every item separately. CFT/KOL rates, labour, polish and selling price can all differ item by item.")
    var alert by remember { mutableStateOf<String?>(null) }
    QeAlert(alert) { alert = null }
    val sellingFocus = remember { HashMap<Int, FocusRequester>() }
    fun sf(i: Int) = sellingFocus.getOrPut(i) { FocusRequester() }
    var focusMissing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusMissing) { focusMissing?.let { runCatching { sf(it).requestFocus() }; focusMissing = null } }

    if (banner != null && banner.itemIndex < items.size) TemplateBannerCard(banner, actions.onUseTemplate, actions.onDismissTemplate)

    if (items.isEmpty()) {
        val shape = RoundedCornerShape(16.dp)
        Text(
            "No calculator items have been sent yet. You can still add an estimation item manually.",
            color = Color(0xFF57534E),
            modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth().clip(shape).background(QeColors.Card).border(1.dp, QeColors.Line, shape).padding(16.dp),
        )
    }
    items.forEachIndexed { i, item ->
        EstimateCard(i, item, costings.getOrNull(i) ?: Costing.blank(), status[i], actions, sf(i), onAlert = { alert = it }) {
            if (!actions.onSendOne(i)) focusMissing = i
        }
    }
    QeSecondaryButton("+ New Estimation Item", onClick = actions.onNewItem)
    val g = QeEstimate.estimateGrand(com.moraswood.qande.data.WorkspaceState(estimationItems = items, costings = costings))
    GrandBar("Estimated Selling Price", QeFormat.money(g.selling), "Send to Quotation →") {
        val missing = actions.onSendToQuotation()
        if (missing >= 0) focusMissing = missing
    }
}

@Composable
private fun SubCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(shape).background(QeColors.Soft).border(1.dp, QeColors.Line, shape).padding(13.dp)) {
        Text(title, color = QeColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

/** `.calc-line` */
@Composable
private fun CalcLine(text: androidx.compose.ui.text.AnnotatedString) {
    val shape = RoundedCornerShape(16.dp)
    Text(text, color = QeColors.Text, modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(shape).background(QeColors.Card).border(1.dp, QeColors.Line, shape).padding(9.dp))
}

private fun line(vararg parts: Pair<String, Boolean>) = buildAnnotatedString {
    parts.forEach { (t, bold) -> if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) } else append(t) }
}

/** `field(label,key,val)` */
@Composable
private fun EstField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    QeFieldLabel(label, modifier) { QeNumberField(value, onChange, Modifier.fillMaxWidth()) }
}

/** `measureField(label,key,val,unitKey,unit)` */
@Composable
private fun EstMeasureField(label: String, value: String, unit: String, modifier: Modifier, onValue: (String) -> Unit, onUnit: (String) -> Unit) {
    QeFieldLabel(label, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QeNumberField(value, onValue, Modifier.weight(1f))
            QeSelect(unit, QeMath.lengthUnits.map { it to it }, onUnit, Modifier.width(76.dp), bold = true)
        }
    }
}

private val ColW: List<Dp> = listOf(110.dp, 110.dp, 110.dp, 80.dp, 70.dp, 130.dp, 70.dp, 150.dp, 120.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EstimateCard(
    i: Int, item: CalcItem, c: Costing, status: String?, actions: EstimationActions, sellingFocus: FocusRequester,
    onAlert: (String) -> Unit, onSendOne: () -> Unit,
) {
    val r = QeEstimate.costingResult(item, c)
    val shape = RoundedCornerShape(16.dp)
    // Checkbox state is DOM-only in V10.1 and cleared by every renderEstimation().
    var renderCount by remember { mutableStateOf(0) }
    val checked = remember(renderCount, item.rows.size) { mutableListOf<Int>().toMutableStateList() }
    fun field(key: String) = { v: String -> actions.onField(i, key, v) }

    Column(Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape).background(QeColors.Card).border(1.dp, QeColors.Line, shape).padding(16.dp)) {
        // .estimate-top
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QeFieldLabel("Item", Modifier.width(260.dp)) { QeTextField(item.name, { actions.onName(i, it) }, modifier = Modifier.fillMaxWidth()) }
            Text(
                line("${QeFormat.toFixed(r.tot.cft, 3)} CFT" to true, " · " to false, "${QeFormat.toFixed(r.tot.kol, 3)} KOL" to true),
                color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.align(Alignment.Bottom).padding(bottom = 18.dp),
            )
        }
        // Native addition: item photo (carried from the calculator by Send to Estimation).
        ItemPhotoBox(item.photo, { actions.onPhoto(i, it) }, Modifier.padding(bottom = 8.dp))

        if (item.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Row { listOf("Length", "Width", "Thickness", "Pieces", "Type", "Total", "Select", "Rate group", "Wood amount").forEachIndexed { k, h -> QeTh(h, ColW[k]) } }
                item.rows.forEachIndexed { j, x ->
                    val z = QeMath.calcRow(x)
                    val lu = x.lUnit.ifEmpty { "ft" }; val wu = x.wUnit.ifEmpty { "in" }; val tu = x.tUnit.ifEmpty { "in" }
                    val group = QeEstimate.rateGroupForRow(c, j)
                    val effective = QeEstimate.effectiveTimberRate(c, x, j)
                    Box(Modifier.width(ColW.fold(0.dp) { a, b -> a + b }).height(1.dp).background(QeColors.CellLine))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MeasureCell(ColW[0], QeEstimate.originalMeasure(x.l, lu), if (lu != "ft") "${QeFormat.fmtMeasure(z.lFt)} ft" else null)
                        MeasureCell(ColW[1], QeEstimate.originalMeasure(x.w, wu), if (wu != "in") "${QeFormat.fmtMeasure(z.wIn)} in" else null)
                        MeasureCell(ColW[2], QeEstimate.originalMeasure(x.t, tu), if (tu != "in") "${QeFormat.fmtMeasure(z.tIn)} in" else null)
                        Cell(ColW[3]) { Text(x.q.ifEmpty { "-" }, color = QeColors.Text) }
                        Cell(ColW[4]) { Text(x.mode, color = QeColors.Text) }
                        Cell(ColW[5]) { Text("${QeFormat.toFixed(z.value, 3)} ${z.mode}", color = QeColors.Text, fontWeight = FontWeight.Bold) }
                        Cell(ColW[6]) { QeCheckbox(j in checked) { on -> if (on) checked.add(j) else checked.remove(j) } }
                        Cell(ColW[7]) {
                            if (group != null) Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = cssHex(group.color.ifEmpty { "#3b82f6" }), fontWeight = FontWeight.Bold)) { append("●") }
                                    append(" ${group.name}")
                                },
                                color = QeColors.Text,
                            ) else Text("Default ${z.mode}", color = QeColors.Text)
                        }
                        Cell(ColW[8]) { Text(QeFormat.money(z.value * QeMath.num(effective)), color = QeColors.Text) }
                    }
                }
            }
            // .timber-group-controls
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QeSecondaryButton("Group selected rows") {
                    val msg = actions.onGroup(i, checked.toList())
                    if (msg != null) onAlert(msg) else renderCount++
                }
                QeSecondaryButton("Ungroup selected rows") { actions.onUngroup(i, checked.toList()); renderCount++ }
                Text("Select rows of the same CFT or KOL type to share a rate.", color = QeColors.Text, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
            // .timber-rate-groups
            c.rateGroups.forEach { g ->
                val sum = QeEstimate.groupSummary(item, g)
                val gs = RoundedCornerShape(16.dp)
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(gs).background(QeColors.Soft).border(1.dp, QeColors.Line, gs).padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 6.dp).size(14.dp).clip(RoundedCornerShape(4.dp)).background(cssHex(g.color.ifEmpty { "#3b82f6" })))
                        Text("${g.name} · ${QeFormat.toFixed(sum.volume, 3)} ${g.mode}", color = QeColors.Text, fontWeight = FontWeight.Bold)
                    }
                    QeFieldLabel("Rate / ${g.mode}", Modifier.padding(top = 8.dp)) { QeNumberField(g.rate, { actions.onGroupRate(i, g.id, it) }, Modifier.fillMaxWidth()) }
                    Text(QeFormat.money(sum.amount), color = QeColors.Text)
                    QeSecondaryButton("Remove group", Modifier.padding(top = 6.dp)) { actions.onRemoveGroup(i, g.id); renderCount++ }
                }
            }
        }

        // .estimate-grid (single column on phones)
        SubCard("Wood cost") {
            EstField("Rate / CFT", c.cftRate, onChange = field("cftRate"))
            EstField("Rate / KOL", c.kolRate, onChange = field("kolRate"))
            CalcLine(line("Grouped cost: " to false, QeFormat.money(r.groupedWood) to true))
            CalcLine(line("Ungrouped cost: " to false, QeFormat.money(r.ungroupedWood) to true))
            CalcLine(line("Total wood cost: ${QeFormat.money(r.wood)}" to true))
        }
        SubCard("Labour") {
            val mode = c.labourMode.ifEmpty { "sqft" }
            QeFieldLabel("Pricing method") {
                QeSelect(if (mode == "lumpsum") "lumpsum" else "sqft", listOf("sqft" to "Per sq.ft", "lumpsum" to "Lumpsum"), field("labourMode"), Modifier.fillMaxWidth())
            }
            if (mode == "sqft") {
                Text("Enter finished door/window dimensions in any unit. The app converts both measurements to feet before calculating sq.ft.", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstMeasureField("Length", c.labourLength, c.labourLengthUnit.ifEmpty { "ft" }, Modifier.weight(1f), field("labourLength"), field("labourLengthUnit"))
                    EstMeasureField("Width", c.labourWidth, c.labourWidthUnit.ifEmpty { "ft" }, Modifier.weight(1f), field("labourWidth"), field("labourWidthUnit"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstField("Quantity", c.labourQty, Modifier.weight(1f), field("labourQty"))
                    EstField("Rate / sq.ft", c.labourRate, Modifier.weight(1f), field("labourRate"))
                }
                CalcLine(line("Area: " to false, "${QeFormat.toFixed(r.labourArea, 2)} sq.ft" to true, " · Labour: " to false, QeFormat.money(r.labour) to true))
            } else {
                EstField("Labour lumpsum", c.labourLumpsum, onChange = field("labourLumpsum"))
                CalcLine(line("Labour: " to false, QeFormat.money(r.labour) to true))
            }
        }
        SubCard("Polish") {
            QeFieldLabel("Pricing method") {
                // V10.1 select shows the first option ("Per sq.ft") when polishMode matches neither value.
                QeSelect(if (c.polishMode == "lumpsum") "lumpsum" else "sqft", listOf("sqft" to "Per sq.ft", "lumpsum" to "Lumpsum"), field("polishMode"), Modifier.fillMaxWidth())
            }
            if (c.polishMode == "sqft") {
                Text(
                    line("Timber surface area from Calculator (all faces): " to false, "${QeFormat.toFixed(r.surface, 2)} sq.ft" to true, ". Mixed input units are converted automatically." to false),
                    color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp),
                )
                EstField("Override polish area (optional)", c.polishAreaOverride, onChange = field("polishAreaOverride"))
                EstField("Polish rate / sq.ft", c.polishRate, onChange = field("polishRate"))
            } else {
                EstField("Polish lumpsum", c.polishLumpsum, onChange = field("polishLumpsum"))
            }
            CalcLine(line("Polish: " to false, QeFormat.money(r.polish) to true))
        }

        // .estimate-bottom
        Column(Modifier.padding(top = 14.dp)) {
            EstField("Consumables", c.consumables, onChange = field("consumables"))
            EstField("Other costs", c.other, onChange = field("other"))
            QeFieldLabel("Estimated selling price") {
                Text(
                    "Auto-filled from estimated cost. Edit this amount to add your margin/profit; this is the price sent to Quotation.",
                    color = QeColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp),
                )
                QeNumberField(c.sellingPrice, field("sellingPrice"), Modifier.fillMaxWidth(), focusRequester = sellingFocus, error = status != null)
            }
        }

        // .metrics (2 × 2 on phones)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Estimated cost", QeFormat.money(r.cost), Modifier.weight(1f))
                Metric("Profit", QeFormat.money(r.profit), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Markup", "${QeFormat.toFixed(r.markup, 1)}%", Modifier.weight(1f))
                Metric("Margin", "${QeFormat.toFixed(r.margin, 1)}%", Modifier.weight(1f))
            }
        }

        // .estimate-send-row
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(QeColors.Line))
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "When the estimated selling price is ready, send this item to Quotation. Quotation will receive the item description and this estimated selling price; you add Unit and Quantity there.",
                color = QeColors.Muted, fontSize = 13.sp,
            )
            if (!status.isNullOrEmpty()) Text(status, color = QeColors.ErrorText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            QePrimaryButton("Send this item to Quotation →", Modifier.fillMaxWidth(), onClick = onSendOne)
        }
    }
}

@Composable
private fun Cell(width: Dp, content: @Composable () -> Unit) {
    Box(Modifier.width(width).padding(8.dp)) { content() }
}

@Composable
private fun MeasureCell(width: Dp, text: String, converted: String?) {
    Column(Modifier.width(width).padding(8.dp)) {
        Text(text, color = QeColors.Text)
        if (converted != null) Text(converted, color = QeColors.Muted, fontSize = 10.sp, lineHeight = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** `.metric` */
@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(QeColors.Soft).padding(13.dp)) {
        Text(label, color = QeColors.Muted, fontSize = 12.sp)
        Text(value, color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}
