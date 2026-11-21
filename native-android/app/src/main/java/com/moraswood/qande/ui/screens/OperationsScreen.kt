package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.ui.components.PortPendingCard
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.theme.QeColors

/** operations.html sections, in nav order. */
enum class OpsTab(val id: String, val label: String, val heading: String, val milestone: String) {
    Projects("projects", "Projects", "Projects & Payments", "Projects/payments"),
    Inventory("inventory", "Expenses & Inventory", "Expenses & Inventory", "Projects/payments"),
    Machines("machines", "Machines & Tools", "Machines & Tools", "Machines"),
    Reports("reports", "Monthly Reports", "Monthly Summary", "Machines");
}

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
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(QeColors.HeaderLine))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperationsScreen(tab: OpsTab, onTab: (OpsTab) -> Unit, message: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OpsTab.entries.forEach { t -> QeSecondaryButton(t.label, onClick = { onTab(t) }) }
            }
            Text(message, color = QeColors.Text, modifier = Modifier.padding(vertical = 12.dp))
            Text(tab.heading, color = QeColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
            PortPendingCard(tab.milestone)
            Text(
                "Local-first operations preview. Data is saved on this device and included in Q&E's existing full JSON backup. Worker accounts, cloud sync, push notifications, camera QR scanning, printable QR stickers and cloud-synced document attachments require later development. Project attachments are stored separately on this device and are NOT included in the JSON backup. Export important files separately before clearing app data or uninstalling.",
                color = QeColors.OpsMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
