package com.moraswood.qande.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.logic.QeStandalone
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.theme.QeColors

/**
 * Home › Timber Calculator (t40u): standalone quick check. State lives only in this composition — nothing is written to
 * Room or the key-value store, no project is created, and there is no Send to Estimation. Reuses the project
 * calculator's item/row UI and the same QeMath CFT/KOL totals.
 */
@Composable
fun TimberCalculatorScreen() = ToolPage {
    var items by remember { mutableStateOf<List<CalcItem>>(QeStandalone.initial()) }
    val actions = remember {
        CalculatorActions(
            onItemName = { ii, v -> items = QeStandalone.setName(items, ii, v) },
            onRowField = { ii, ri, k, v -> items = QeStandalone.setRowField(items, ii, ri, k, v) },
            onAddRow = { ii -> items = QeStandalone.addRow(items, ii) },
            onNewItem = { items = QeStandalone.addItem(items) },
            onSendToEstimation = {},
            onItemTag = { _, _ -> }, onItemPhoto = { _, _ -> }, onSaveTemplate = {},
            onUseTemplate = {}, onDismissTemplate = {},
        )
    }
    CalculatorPanel(items, null, emptyMap(), actions, standalone = true)
    Text(QeStandalone.NOTE, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    QeSecondaryButton("Clear all") { items = QeStandalone.initial() }
}
