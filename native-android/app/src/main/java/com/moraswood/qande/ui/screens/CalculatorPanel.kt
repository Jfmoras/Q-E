package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

private val MeasureW = 208.dp
private val PiecesW = 140.dp
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

    // Feature M: speech → measurements via the system RecognizerIntent (no RECORD_AUDIO permission in the app).
    var voiceTarget by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var voiceMsg by remember { mutableStateOf<String?>(null) }
    com.moraswood.qande.ui.components.QeAlert(voiceMsg) { voiceMsg = null }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        val target = voiceTarget ?: return@rememberLauncherForActivityResult
        voiceTarget = null
        if (res.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS).orEmpty()
        val pick = heard.map { com.moraswood.qande.logic.QeVoice.parse(it) }.firstOrNull { !it.isEmpty }
        val sets = pick?.let { com.moraswood.qande.logic.QeVoice.assign(target.third, it) }.orEmpty()
        if (sets.isEmpty()) voiceMsg = com.moraswood.qande.logic.QeVoice.NO_MATCH + (heard.firstOrNull()?.let { " (heard: “$it”)" } ?: "")
        else sets.forEach { (k, v) -> actions.onRowField(target.first, target.second, k, v) }
    }
    val onMic: (Int, Int, String) -> Unit = { ii, ri, key ->
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, com.moraswood.qande.logic.QeVoice.PROMPT)
        voiceTarget = Triple(ii, ri, key)
        try { voice.launch(intent) } catch (e: android.content.ActivityNotFoundException) { voiceTarget = null; voiceMsg = com.moraswood.qande.logic.QeVoice.UNAVAILABLE }
    }

    if (banner != null && banner.itemIndex < items.size) TemplateBannerCard(banner, actions.onUseTemplate, actions.onDismissTemplate)

    items.forEachIndexed { ii, item ->
        CalcItemCard(ii, item, templateStatus[ii], actions, ::fr, onEnterLastField = { ri ->
            actions.onAddRow(ii)
            pendingFocus = ii to ri + 1
        }, onMic = onMic)
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

/** Native addition: template suggestion banner shown above an item list. */
@Composable
fun TemplateBannerCard(banner: TemplateBanner, onUse: (TemplateBanner) -> Unit, onDismiss: (TemplateBanner) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape).background(QeColors.GrandBg).border(1.dp, QeColors.GrandBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(banner.message, color = QeColors.GrandText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("${banner.rows.size} row${if (banner.rows.size == 1) "" else "s"} for Item ${banner.itemIndex + 1}. You can edit every value afterwards.", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QePrimaryButton("Use template") { onUse(banner) }
            QeSecondaryButton("Dismiss") { onDismiss(banner) }
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
    onMic: (Int, Int, String) -> Unit = { _, _, _ -> },
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
            item.rows.forEachIndexed { ri, r -> CalcRowView(ii, ri, r, actions, fr, onEnterLastField, onMic) }
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
    onMic: (Int, Int, String) -> Unit = { _, _, _ -> },
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
                    MicButton { onMic(ii, ri, key) }
                }
            }
            Row(Modifier.width(PiecesW).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QeNumberField(r.q, { actions.onRowField(ii, ri, "q", it) }, Modifier.weight(1f), focusRequester = fr(ii, ri, "q"), compact = true, onEnter = { onEnterLastField(ri) })
                MicButton { onMic(ii, ri, "q") }
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

/** Feature M: mic next to a measurement field. */
@Composable
private fun MicButton(onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 4.dp).width(30.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(QeColors.Soft)
            .border(1.dp, QeColors.Line, RoundedCornerShape(15.dp)).clickable(onClickLabel = "Voice input", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("🎤", fontSize = 13.sp) }
}

