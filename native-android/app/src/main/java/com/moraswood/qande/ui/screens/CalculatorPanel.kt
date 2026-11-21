package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.TemplateBanner
import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Row as CalcRow
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath
import com.moraswood.qande.ui.components.ItemPhotoBox
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.QeNumberField
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.components.QeTh
import com.moraswood.qande.ui.theme.QeColors

/** Callbacks for the Timber Calculator (app.js renderCalc handlers + native additions). */
class CalculatorActions(
    val onItemName: (Int, String) -> Unit,
    val onRowField: (Int, Int, String, String) -> Unit,
    val onAddRow: (Int) -> Unit,
    val onNewItem: () -> Unit,
    val onSendToEstimation: () -> Unit,
    // native additions
    val onItemTag: (Int, String) -> Unit,
    val onItemPhoto: (Int, String) -> Unit,
    val onSaveTemplate: (Int) -> Unit,
    val onUseTemplate: (TemplateBanner) -> Unit,
    val onDismissTemplate: (TemplateBanner) -> Unit,
)

private val MeasureW = 172.dp
private val PiecesW = 104.dp
private val TypeW = 96.dp
private val TotalW = 200.dp

/** `#calculator` panel. */
@Composable
fun CalculatorPanel(
    items: List<CalcItem>,
    banner: TemplateBanner?,
    templateStatus: Map<Int, String>,
    actions: CalculatorActions,
) {
    PageHeading(
        "Timber Calculator",
        "Choose a unit for each measurement. The app converts Length to feet and Width/Thickness to inches automatically for CFT/KOL. Press Enter to move L → W → T → Pieces → next row.",
    )
    // Focus target after Enter on Pieces adds a row: (item, row).
    var pendingFocus by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun fr(ii: Int, ri: Int, key: String) = requesters.getOrPut("$ii:$ri:$key") { FocusRequester() }

    if (banner != null && banner.itemIndex < items.size) TemplateBannerCard(banner, actions)

    items.forEachIndexed { ii, item ->
        CalcItemCard(ii, item, templateStatus[ii], actions, ::fr) { ri ->
            actions.onAddRow(ii)
            pendingFocus = ii to ri + 1
        }
    }
    LaunchedEffect(pendingFocus, items) {
        val p = pendingFocus ?: return@LaunchedEffect
        if ((items.getOrNull(p.first)?.rows?.size ?: 0) > p.second) {
            runCatching { fr(p.first, p.second, "l").requestFocus() }
            pendingFocus = null
        }
    }

    QeSecondaryButton("+ New Item", onClick = actions.onNewItem)
    val g = QeMath.grandTotals(items)
    GrandBar("Grand Total", "${QeFormat.toFixed(g.cft, 3)} CFT | ${QeFormat.toFixed(g.kol, 3)} KOL", "Send to Estimation →", actions.onSendToEstimation)
}

/** `.grand-bar` */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrandBar(label: String, value: String, button: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    FlowRow(
        Modifier.padding(top = 18.dp).fillMaxWidth().clip(shape).background(QeColors.GrandBg).border(1.dp, QeColors.GrandBorder, shape)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(label, color = QeColors.GrandText, modifier = Modifier.align(Alignment.CenterVertically))
        Text(value, color = QeColors.GrandText, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
        QePrimaryButton(button, modifier = Modifier.fillMaxWidth(), onClick = onClick)
    }
}

@Composable
private fun TemplateBannerCard(banner: TemplateBanner, actions: CalculatorActions) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape).background(QeColors.GrandBg).border(1.dp, QeColors.GrandBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(banner.message, color = QeColors.GrandText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("${banner.rows.size} row${if (banner.rows.size == 1) "" else "s"} for Item ${banner.itemIndex + 1}. You can edit every value afterwards.", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QePrimaryButton("Use template") { actions.onUseTemplate(banner) }
            QeSecondaryButton("Dismiss") { actions.onDismissTemplate(banner) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalcItemCard(
    ii: Int,
    item: CalcItem,
    status: String?,
    actions: CalculatorActions,
    fr: (Int, Int, String) -> FocusRequester,
    onEnterLastField: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape).background(QeColors.Card).border(1.dp, QeColors.Line, shape)) {
        // .item-head
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Item", color = QeColors.Text, fontWeight = FontWeight.Bold)
            QeTextField(item.name, { actions.onItemName(ii, it) }, placeholder = "Item name", modifier = Modifier.weight(1f))
        }
        // Native additions: furniture type tag, Save as template, item photo.
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QeTextField(item.tag, { actions.onItemTag(ii, it) }, placeholder = "Furniture type tag (optional)", modifier = Modifier.width(260.dp))
                QeSecondaryButton("Save as template") { actions.onSaveTemplate(ii) }
            }
            if (!status.isNullOrEmpty()) Text(status, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            ItemPhotoBox(item.photo, { actions.onItemPhoto(ii, it) }, Modifier.padding(top = 10.dp))
        }
        // .table-wrap > .calc-table.unit-calc-table
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row {
                QeTh("Length", MeasureW); QeTh("Width", MeasureW); QeTh("Thickness", MeasureW)
                QeTh("No. of pieces", PiecesW); QeTh("Type", TypeW); QeTh("Total", TotalW)
            }
            item.rows.forEachIndexed { ri, r -> CalcRowView(ii, ri, r, actions, fr, onEnterLastField) }
        }
        QeSecondaryButton("+ Add row", modifier = Modifier.padding(12.dp)) { actions.onAddRow(ii) }
        val tot = QeMath.itemTotals(item)
        Text(
            buildAnnotatedString {
                append("Item total: ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${QeFormat.toFixed(tot.cft, 3)} CFT") }
                append(" · ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${QeFormat.toFixed(tot.kol, 3)} KOL") }
            },
            color = QeColors.Text, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().background(QeColors.Soft).padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

private val unitOptions = QeMath.lengthUnits.map { it to it }
private val modeOptions = listOf("CFT" to "CFT", "KOL" to "KOL")

@Composable
private fun CalcRowView(
    ii: Int, ri: Int, r: CalcRow, actions: CalculatorActions,
    fr: (Int, Int, String) -> FocusRequester, onEnterLastField: (Int) -> Unit,
) {
    val x = QeMath.calcRow(r)
    val lu = r.lUnit.ifEmpty { "ft" }; val wu = r.wUnit.ifEmpty { "in" }; val tu = r.tUnit.ifEmpty { "in" }
    val showConv = lu != "ft" || wu != "in" || tu != "in"
    Column {
        Box(Modifier.width(MeasureW * 3 + PiecesW + TypeW + TotalW).height(1.dp).background(QeColors.CellLine))
        Row(verticalAlignment = Alignment.Top) {
            listOf(Triple("l", r.l, "lUnit" to lu), Triple("w", r.w, "wUnit" to wu), Triple("t", r.t, "tUnit" to tu)).forEach { (key, value, unit) ->
                Row(Modifier.width(MeasureW).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QeNumberField(
                        value, { actions.onRowField(ii, ri, key, it) }, Modifier.weight(1f), focusRequester = fr(ii, ri, key), compact = true,
                        onEnter = { runCatching { fr(ii, ri, if (key == "l") "w" else if (key == "w") "t" else "q").requestFocus() } },
                    )
                    QeSelect(unit.second, unitOptions, { actions.onRowField(ii, ri, unit.first, it) }, Modifier.width(68.dp), compact = true, bold = true)
                }
            }
            Box(Modifier.width(PiecesW).padding(8.dp)) {
                QeNumberField(r.q, { actions.onRowField(ii, ri, "q", it) }, Modifier.fillMaxWidth(), focusRequester = fr(ii, ri, "q"), compact = true, onEnter = { onEnterLastField(ri) })
            }
            Box(Modifier.width(TypeW).padding(8.dp)) {
                QeSelect(r.mode, modeOptions, { actions.onRowField(ii, ri, "mode", it) }, Modifier.fillMaxWidth(), compact = true)
            }
            Column(Modifier.width(TotalW).padding(8.dp)) {
                Spacer(Modifier.height(8.dp))
                Text(if (x.value != 0.0) "${QeFormat.toFixed(x.value, 3)} ${x.mode}" else "-", color = QeColors.Text, fontWeight = FontWeight.Bold)
                if (showConv) Text(
                    "Converted: ${QeFormat.fmtMeasure(x.lFt)} ft × ${QeFormat.fmtMeasure(x.wIn)} in × ${QeFormat.fmtMeasure(x.tIn)} in",
                    color = QeColors.Muted, fontSize = 10.sp, lineHeight = 12.5.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
