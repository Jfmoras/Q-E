package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.moraswood.qande.logic.QeGroups
import com.moraswood.qande.logic.QeCategories
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
    // Feature F: item groups
    val onGroupItems: (List<Int>) -> String? = { null },
    val onUngroupItems: (String) -> Unit = {},
    val onItemGroupField: (String, String, String) -> Unit = { _, _, _ -> },
    val onToggleItemGroup: (String) -> Unit = {},
    /** Feature G: mark a row vertical (area = length × thickness). */
    val onRowFace: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    /** Feature J: move an item to another category; set a category default rate. */
    val onMoveCategory: (Int, String) -> Unit = { _, _ -> },
    val onCategoryDefault: (String, String, String) -> Unit = { _, _, _ -> },
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
    groups: List<com.moraswood.qande.data.ItemGroup> = emptyList(),
    categoryDefaults: Map<String, com.moraswood.qande.data.CategoryDefaults>? = null,
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
    val ws = com.moraswood.qande.data.WorkspaceState(estimationItems = items, costings = costings, itemGroups = groups.ifEmpty { null }, categoryDefaults = categoryDefaults)
    // Feature J: Furniture | Doors & Windows tabs
    var catTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(QeCategories.FURNITURE) }
    CategoryTabs(ws, catTab) { catTab = it }
    CategoryDefaultsCard(catTab, categoryDefaults?.get(catTab) ?: com.moraswood.qande.data.CategoryDefaults(), actions)
    val inTab = items.indices.filter { QeCategories.itemCategory(ws, it) == catTab }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    LaunchedEffect(items.size) { selected.removeAll { it >= items.size } }
    val card = @Composable { i: Int ->
        val item = items[i]
        val g = QeGroups.groupOf(ws, i)
        EstimateCard(
            i, item, costings.getOrNull(i) ?: Costing.blank(), status[i], actions, sf(i), onAlert = { alert = it },
            eff = QeGroups.effectiveCosting(ws, i), group = g, selected = i in selected,
            onSelect = { on -> if (on) selected.add(i) else selected.remove(i) },
            moveLabel = if (catTab == QeCategories.DW) "Move to Furniture" else "Move to Doors & Windows",
            onMove = { selected.clear(); actions.onMoveCategory(i, if (catTab == QeCategories.DW) QeCategories.FURNITURE else QeCategories.DW) },
        ) {
            if (!actions.onSendOne(i)) focusMissing = i
        }
    }
    if (selected.isNotEmpty()) ItemGroupBar(selected.size) {
        val msg = actions.onGroupItems(selected.toList())
        if (msg != null) alert = msg else selected.clear()
    }
    if (groups.isEmpty()) {
        inTab.forEach { card(it) }
    } else {
        groups.filter { g -> g.items.firstOrNull { it in items.indices }?.let { QeCategories.itemCategory(ws, it) == catTab } == true }
            .forEach { g -> ItemGroupSection(g, QeGroups.groupTotals(ws, g), actions) { g.items.filter { it in items.indices }.forEach { card(it) } } }
        val ungrouped = inTab.filter { QeGroups.groupOf(ws, it) == null }
        if (ungrouped.isNotEmpty()) {
            val u = ungrouped.map { QeGroups.itemResult(ws, it) }.let { rs -> QeGroups.Sub(rs.sumOf { it.cost }, rs.sumOf { it.selling }, rs.sumOf { it.profit }) }
            Text("Ungrouped items", color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(subLine(u.cost, u.selling, u.profit), color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            ungrouped.forEach { card(it) }
        }
    }
    if (inTab.isEmpty() && items.isNotEmpty()) Text("No ${QeCategories.label(catTab)} items yet.", color = QeColors.Muted, modifier = Modifier.padding(bottom = 10.dp))
    val sub = QeCategories.subtotal(ws, catTab)
    Text("${QeCategories.label(catTab)} subtotal · " + subLine(sub.cost, sub.selling, sub.profit), color = QeColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    QeSecondaryButton("+ New Estimation Item") {
        val n = items.size
        actions.onNewItem()
        if (catTab == QeCategories.DW) actions.onMoveCategory(n, QeCategories.DW)
    }
    val g = QeGroups.grand(ws)
    GrandBar("Estimated Selling Price", QeFormat.money(g.selling), "Send to Quotation →") {
        val missing = actions.onSendToQuotation()
        if (missing >= 0) focusMissing = missing
    }
    if (groups.isNotEmpty() || items.any { QeCategories.itemCategory(ws, items.indexOf(it)) == QeCategories.DW }) Text("Grand total (all categories) · " + subLine(g.cost, g.selling, g.profit), color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

private fun subLine(cost: Double, selling: Double, profit: Double) =
    "Cost ${QeFormat.money(cost)} · Selling ${QeFormat.money(selling)} · Profit ${QeFormat.money(profit)}"

/** Feature F: shown while estimation items are selected. */
@Composable
private fun ItemGroupBar(count: Int, onGroup: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.padding(bottom = 14.dp).fillMaxWidth().clip(shape).background(QeColors.GrandBg).border(1.dp, QeColors.GrandBorder, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count item${if (count == 1) "" else "s"} selected", color = QeColors.GrandText, modifier = Modifier.weight(1f))
        QePrimaryButton("Group selected items", onClick = onGroup)
    }
}

/** Feature F: collapsible item group with its defaults, lumpsums and subtotals. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemGroupSection(g: com.moraswood.qande.data.ItemGroup, t: QeGroups.GroupTotals, actions: EstimationActions, members: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val open = g.collapsed != true
    fun f(key: String) = { v: String -> actions.onItemGroupField(g.id, key, v) }
    Column(Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape).background(QeColors.Soft).border(2.dp, QeColors.GrandBorder, shape).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().clickable { actions.onToggleItemGroup(g.id) }, verticalAlignment = Alignment.CenterVertically) {
            Text(if (open) "▾" else "▸", color = QeColors.Accent, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text("${g.name.ifEmpty { "Group" }} · ${g.items.size} item${if (g.items.size == 1) "" else "s"}", color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subLine(t.cost, t.selling, t.profit), color = QeColors.Muted, fontSize = 13.sp)
            }
        }
        if (open) {
            QeFieldLabel("Group name", Modifier.padding(top = 10.dp)) { QeTextField(g.name, f("name"), Modifier.fillMaxWidth()) }
            Text("Default rates for items in this group (an item's own rate wins when filled).", color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstField("Group Rate / CFT", g.cftRate, Modifier.weight(1f), f("cftRate"))
                EstField("Group Rate / KOL", g.kolRate, Modifier.weight(1f), f("kolRate"))
            }
            Text("Group lumpsums (added once for the whole group).", color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstField("Labour", g.labour, Modifier.weight(1f), f("labour"))
                EstField("Polish", g.polish, Modifier.weight(1f), f("polish"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstField("Consumables", g.consumables, Modifier.weight(1f), f("consumables"))
                EstField("Other costs", g.other, Modifier.weight(1f), f("other"))
            }
            QeFieldLabel("Group selling price") {
                Text("Blank = items' selling prices + group lumpsums. Shared onto the quotation lines by item cost.", color = QeColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
                QeNumberField(g.selling, f("selling"), Modifier.fillMaxWidth(), placeholder = QeEstimate.jsRoundString(t.itemsSelling + t.extras))
            }
            CalcLine(line("Items cost: " to false, QeFormat.money(t.itemsCost) to true, " · Group lumpsums: " to false, QeFormat.money(t.extras) to true))
            QeSecondaryButton("Ungroup", Modifier.padding(top = 8.dp, bottom = 10.dp)) { actions.onUngroupItems(g.id) }
            members()
        }
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

private val ColW: List<Dp> = listOf(110.dp, 110.dp, 110.dp, 80.dp, 70.dp, 130.dp, 70.dp, 150.dp, 120.dp, 150.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EstimateCard(
    i: Int, item: CalcItem, c: Costing, status: String?, actions: EstimationActions, sellingFocus: FocusRequester,
    onAlert: (String) -> Unit,
    eff: Costing = c, group: com.moraswood.qande.data.ItemGroup? = null, selected: Boolean = false, onSelect: (Boolean) -> Unit = {},
    moveLabel: String = "", onMove: (() -> Unit)? = null,
    onSendOne: () -> Unit,
) {
    val r = QeEstimate.costingResult(item, eff)
    val shape = RoundedCornerShape(16.dp)
    // Checkbox state is DOM-only in V10.1 and cleared by every renderEstimation().
    var renderCount by remember { mutableStateOf(0) }
    val checked = remember(renderCount, item.rows.size) { mutableListOf<Int>().toMutableStateList() }
    fun field(key: String) = { v: String -> actions.onField(i, key, v) }

    Column(Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape).background(QeColors.Card).border(1.dp, QeColors.Line, shape).padding(16.dp)) {
        // Feature F: select for an item group
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            QeCheckbox(selected, onSelect)
            Text(if (group != null) "Select (in ${group.name.ifEmpty { "group" }})" else "Select for item group", color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (onMove != null) QeSecondaryButton(moveLabel, onClick = onMove)
        }
        // .estimate-top
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QeFieldLabel("Item", Modifier.width(260.dp)) { QeTextField(item.name, { actions.onName(i, it) }, modifier = Modifier.fillMaxWidth()) }
            Text(
                line("${QeFormat.toFixed(r.tot.cft, 3)} CFT" to true, " · " to false, "${QeFormat.toFixed(r.tot.kol, 3)} KOL" to true, " · " to false, "${com.moraswood.qande.logic.QeArea.label(item, c).let { if (it == "n/a") "Area n/a" else "$it sq.ft" }}" to true),
                color = QeColors.Text, fontSize = 13.sp, modifier = Modifier.align(Alignment.Bottom).padding(bottom = 18.dp),
            )
        }
        // Native addition: item photo (carried from the calculator by Send to Estimation).
        ItemPhotoBox(item.photo, { actions.onPhoto(i, it) }, Modifier.padding(bottom = 8.dp))

        if (item.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Row { listOf("Length", "Width", "Thickness", "Pieces", "Type", "Total", "Select", "Rate group", "Wood amount", "Area (sq.ft)").forEachIndexed { k, h -> QeTh(h, ColW[k]) } }
                item.rows.forEachIndexed { j, x ->
                    val z = QeMath.calcRow(x)
                    val lu = x.lUnit.ifEmpty { "ft" }; val wu = x.wUnit.ifEmpty { "in" }; val tu = x.tUnit.ifEmpty { "in" }
                    val group = QeEstimate.rateGroupForRow(c, j)
                    val effective = QeEstimate.effectiveTimberRate(eff, x, j)
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
                        Cell(ColW[9]) {
                            Column {
                                Text(QeFormat.toFixed(com.moraswood.qande.logic.QeArea.rowArea(x), 2) + if (x.areaFace == "LT") " (L×T)" else " (L×W)", color = QeColors.Text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QeCheckbox(x.areaFace == "LT") { on -> actions.onRowFace(i, j, on) }
                                    Text("Vertical", color = QeColors.Muted, fontSize = 12.sp)
                                }
                            }
                        }
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
            if (group != null && (c.cftRate.isBlank() && group.cftRate.isNotBlank() || c.kolRate.isBlank() && group.kolRate.isNotBlank()))
                Text("Blank rates use ${group.name.ifEmpty { "group" }} defaults: CFT ${group.cftRate.ifBlank { "-" }} · KOL ${group.kolRate.ifBlank { "-" }}", color = QeColors.Muted, fontSize = 12.sp)
            CalcLine(line("Grouped cost: " to false, QeFormat.money(r.groupedWood) to true))
            CalcLine(line("Ungrouped cost: " to false, QeFormat.money(r.ungroupedWood) to true))
            CalcLine(line("Total wood cost: ${QeFormat.money(r.wood)}" to true))
        }
        SubCard("Labour") {
            val mode = c.labourMode.ifEmpty { "sqft" }
            QeFieldLabel("Pricing method") {
                QeSelect(if (mode == "lumpsum") "lumpsum" else "sqft", listOf("sqft" to "Per sq.ft", "lumpsum" to "Lumpsum"), field("labourMode"), Modifier.fillMaxWidth())
            }
            // Feature G: item area from the rows (read-only) with an override
            val areaLabel = com.moraswood.qande.logic.QeArea.label(item, c)
            CalcLine(line("Item area (from rows): " to false, (if (areaLabel == "n/a") "n/a" else "$areaLabel sq.ft") to true))
            EstField("Override item area (optional)", c.areaOverride.orEmpty(), onChange = field("areaOverride"))
            if (mode == "sqft") {
                if (com.moraswood.qande.logic.QeArea.autoLabour(c))
                    Text("Length and width blank — labour uses the item area (× quantity when filled).", color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
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

/** Feature J: category tabs with item counts and subtotals. */
@Composable
private fun CategoryTabs(ws: com.moraswood.qande.data.WorkspaceState, tab: String, onTab: (String) -> Unit) {
    Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QeCategories.ALL.forEach { (id, label) ->
            val sub = QeCategories.subtotal(ws, id)
            val text = "$label (${sub.count}) · ${QeFormat.money(sub.selling)}"
            if (tab == id) QePrimaryButton(text) {} else QeSecondaryButton(text) { onTab(id) }
        }
    }
}

/** Feature J: the category's default rates (blank item and group rates use them). */
@Composable
private fun CategoryDefaultsCard(cat: String, d: com.moraswood.qande.data.CategoryDefaults, actions: EstimationActions) {
    SubCard("${QeCategories.label(cat)} defaults") {
        Text("Used when an item (and its group) leaves the rate blank.", color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EstField("Rate / CFT", d.cftRate, Modifier.weight(1f)) { actions.onCategoryDefault(cat, "cftRate", it) }
            EstField("Rate / KOL", d.kolRate, Modifier.weight(1f)) { actions.onCategoryDefault(cat, "kolRate", it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EstField("Labour rate / sq.ft", d.labourRate, Modifier.weight(1f)) { actions.onCategoryDefault(cat, "labourRate", it) }
            EstField("Polish rate / sq.ft", d.polishRate, Modifier.weight(1f)) { actions.onCategoryDefault(cat, "polishRate", it) }
        }
    }
    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
}
