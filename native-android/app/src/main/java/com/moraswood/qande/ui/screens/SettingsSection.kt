package com.moraswood.qande.ui.screens

import android.content.Intent
import android.icu.text.NumberFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.backup.QeBackup
import com.moraswood.qande.logic.LengthConverter
import com.moraswood.qande.logic.QeAi
import com.moraswood.qande.settings.SettingsViewModel
import com.moraswood.qande.ui.components.QeAlert
import com.moraswood.qande.ui.components.QeCard
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeFieldLabel
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeSelect
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors
import java.math.RoundingMode

/** `Number.toLocaleString(undefined,{maximumFractionDigits:n})` with the device locale (ICU, half-up like V8). */
private fun localeFormat(d: Double, maxFraction: Int): String = runCatching {
    NumberFormat.getInstance().apply { maximumFractionDigits = maxFraction; roundingMode = RoundingMode.HALF_UP.ordinal }.format(d)
}.getOrElse { LengthConverter.enUs(d, maxFraction) }

@Composable
private fun SettingsH3(text: String) = Text(text, color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)

/** V10.1 `#settings` cards: Full Backup & Restore, AI API, Length converter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCards(svm: SettingsViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { svm.loadAiSettingsFields() }
    val confirm by svm.confirm.collectAsState()
    val alert by svm.alert.collectAsState()
    val reload by svm.reload.collectAsState()
    val busy by svm.busy.collectAsState()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(svm::exportTo) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(svm::importFrom) }
    QeConfirm(confirm, svm::onConfirm)
    QeAlert(alert) {
        svm.dismissAlert()
        if (reload) { // location.reload(): restart the app process so every screen reloads from the restored database.
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component?.let { ctx.startActivity(Intent.makeRestartActivityTask(it)) }
            Runtime.getRuntime().exit(0)
        }
    }

    QeCard {
        SettingsH3("Full Backup & Restore")
        Text(
            "Export all local project records, revision history, notes, quick calculations and settings, excluding protected API credentials.",
            color = QeColors.Text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QePrimaryButton("Export Backup", enabled = !busy) { save.launch(QeBackup.FILE_NAME) }
            QeSecondaryButton("Import Backup", enabled = !busy) { open.launch(arrayOf("application/json", "text/plain")) }
        }
        Text(
            "The native backup also includes item photos, project files, templates and asset logs. Always export a Full Backup before reinstalling or updating.",
            color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
        )
        if (busy) Text("Working…", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }

    val f by svm.aiForm.collectAsState()
    QeCard(Modifier.padding(top = 18.dp)) {
        SettingsH3("AI API")
        Text(QeAi.INTRO, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        QeFieldLabel("API base URL or chat-completions URL") {
            QeTextField(f.endpoint, svm::setEndpoint, Modifier.fillMaxWidth(), placeholder = "https://api.openai.com/v1", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        }
        QeFieldLabel("Model") { QeTextField(f.model, svm::setModel, Modifier.fillMaxWidth(), placeholder = "gpt-4o-mini") }
        QeFieldLabel("API key") {
            QeTextField(f.apiKey, svm::setApiKey, Modifier.fillMaxWidth(), placeholder = "Paste your API key",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), visualTransformation = PasswordVisualTransformation())
        }
        Text(f.detection, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QePrimaryButton("Save AI settings") { svm.saveAiSettings() }
            QeSecondaryButton("Remove API key") { svm.removeAiKey() }
            Text(f.status, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }

    var value by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("mm") }
    var to by rememberSaveable { mutableStateOf("in") }
    val units = LengthConverter.UNITS.map { it.code to it.label }
    QeCard(Modifier.padding(top = 18.dp)) {
        SettingsH3("Length converter")
        Text(LengthConverter.INTRO, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QeFieldLabel("Value", Modifier.weight(1f)) {
                QeTextField(value, { value = it }, Modifier.fillMaxWidth(), placeholder = "Enter a length", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            QeFieldLabel("From", Modifier.weight(1f)) { QeSelect(from, units, { from = it }, Modifier.fillMaxWidth()) }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            QeSecondaryButton("⇄") { val u = from; from = to; to = u }
        }
        QeFieldLabel("To", Modifier.padding(top = 6.dp)) { QeSelect(to, units, { to = it }, Modifier.fillMaxWidth()) }
        Text(
            LengthConverter.result(value, from, to, ::localeFormat),
            color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(QeColors.Soft).padding(14.dp),
        )
    }
}
