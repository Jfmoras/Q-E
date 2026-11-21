package com.moraswood.qande.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.ClientEntity
import com.moraswood.qande.logic.QeClients
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeRevisions
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.QeAlert
import com.moraswood.qande.ui.components.QeCard
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QeFieldLabel
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors

/** Feature O — client database: name, phone, address, notes, linked past jobs and total spend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientsScreen(
    summaries: List<QeClients.Summary>,
    onSave: (ClientEntity) -> String?,
    onDelete: (String) -> Unit,
    onOpenJob: (String) -> Unit,
) = ToolPage(maxWidth = 900) {
    PageHeading("Clients", "Saved clients, their past jobs and total spend. Pick a client in the Quotation to fill the customer details.")
    var editing by remember { mutableStateOf<ClientEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<ClientEntity?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var alert by remember { mutableStateOf<String?>(null) }
    QeAlert(alert) { alert = null }
    QeConfirm(confirmDelete?.let { "Delete client ${it.name}? Their jobs stay saved." }) { ok -> confirmDelete?.let { if (ok) onDelete(it.id) }; confirmDelete = null }

    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        QeTextField(query, { query = it }, Modifier.weight(1f), placeholder = "Search name, phone, address")
        QePrimaryButton("+ New client", onClick = { editing = ClientEntity("", "") })
    }

    editing?.let { e ->
        ClientEditor(e, onCancel = { editing = null }, onSave = { c -> val err = onSave(c); if (err != null) alert = err else editing = null })
    }

    val shown = summaries.filter { QeClients.matches(it.client, query) }
    if (shown.isEmpty()) Text(if (summaries.isEmpty()) "No clients yet." else "No clients match.", color = QeColors.Muted, fontSize = 14.sp)
    shown.forEach { s ->
        val c = s.client
        QeCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (c.phone.isNotBlank()) Text("📞 " + c.phone, color = QeColors.Text, fontSize = 14.sp)
                    if (c.address.isNotBlank()) Text(c.address, color = QeColors.Muted, fontSize = 14.sp)
                    if (c.notes.isNotBlank()) Text(c.notes, color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total spend", color = QeColors.Muted, fontSize = 12.sp)
                    Text(QeFormat.money(s.totalSpend), color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${s.jobs.size} job" + (if (s.jobs.size == 1) "" else "s"), color = QeColors.Muted, fontSize = 12.sp)
                }
            }
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.jobs.isNotEmpty()) QeSecondaryButton(if (c.id in expanded) "Hide jobs" else "Past jobs (${s.jobs.size})", onClick = {
                    expanded = if (c.id in expanded) expanded - c.id else expanded + c.id
                })
                QeSecondaryButton("Edit", onClick = { editing = c })
                QeDangerButton("Delete") { confirmDelete = c }
            }
            if (c.id in expanded) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.jobs.forEach { j ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(QeRevisions.label(j.name, j.revision), color = QeColors.Text, fontSize = 15.sp)
                            Text(j.updatedAt.take(10), color = QeColors.Muted, fontSize = 12.sp)
                        }
                        Text(QeFormat.money(j.total), color = QeColors.Text, fontSize = 15.sp)
                        QeSecondaryButton("Open", onClick = { onOpenJob(j.rootId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientEditor(start: ClientEntity, onCancel: () -> Unit, onSave: (ClientEntity) -> Unit) {
    var name by remember(start) { mutableStateOf(start.name) }
    var phone by remember(start) { mutableStateOf(start.phone) }
    var address by remember(start) { mutableStateOf(start.address) }
    var notes by remember(start) { mutableStateOf(start.notes) }
    QeCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(if (start.id.isEmpty()) "New client" else "Edit client", color = QeColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QeFieldLabel("Name") { QeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Client name") }
            QeFieldLabel("Phone") {
                QeTextField(phone, { phone = it }, Modifier.fillMaxWidth(), placeholder = "Phone",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
            }
            QeFieldLabel("Address") { QeTextField(address, { address = it }, Modifier.fillMaxWidth(), placeholder = "Address / location", singleLine = false, minLines = 2) }
            QeFieldLabel("Notes") { QeTextField(notes, { notes = it }, Modifier.fillMaxWidth(), placeholder = "Notes", singleLine = false, minLines = 2) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QePrimaryButton("Save client", onClick = { onSave(start.copy(name = name, phone = phone, address = address, notes = notes)) })
                QeSecondaryButton("Cancel", onClick = onCancel)
            }
        }
    }
}
