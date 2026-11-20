package com.moraswood.qande

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun ProjectPayments(project: ProjectEntity, dao: MwiDao) {
    val scope = rememberCoroutineScope()
    var entries by remember(project.id) { mutableStateOf<List<ProjectEntryEntity>>(emptyList()) }
    var amount by remember(project.id) { mutableStateOf("") }
    var date by remember(project.id) { mutableStateOf(LocalDate.now().toString()) }
    var note by remember(project.id) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    suspend fun refresh() { entries = dao.entries(project.id).filter { it.kind == "payment" }.sortedByDescending { it.recordedAt } }
    LaunchedEffect(project.id) { refresh() }
    val paid = entries.sumOf { it.amount }
    val balance = project.agreedValue - paid
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Payments", style = MaterialTheme.typography.headlineSmall)
        Text("Total project value: ₹%.2f".format(project.agreedValue))
        Text("Total received: ₹%.2f".format(paid))
        Text("Remaining: ₹%.2f".format(balance), style = MaterialTheme.typography.titleLarge)
        if (project.agreedValue > 0) LinearProgressIndicator(progress = { (paid / project.agreedValue).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Payment received (₹)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Payment date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Payment reference / note") }, modifier = Modifier.fillMaxWidth())
        val validAmount = amount.toDoubleOrNull()?.let { it > 0 && it.isFinite() } == true
        val validDate = runCatching { LocalDate.parse(date) }.isSuccess
        Button(enabled = validAmount && validDate, onClick = {
            scope.launch {
                try {
                    dao.putEntry(ProjectEntryEntity(UUID.randomUUID().toString(), project.id, "payment", amount.toDouble(), note.trim(), date))
                    amount = ""; note = ""; error = ""; refresh()
                } catch (e: Exception) { error = e.message ?: "Unable to save payment" }
            }
        }) { Text("Record payment") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Text("Payment history", style = MaterialTheme.typography.titleMedium)
        entries.forEach { entry ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("₹%.2f • %s".format(entry.amount, entry.recordedAt))
                    if (entry.note.isNotBlank()) Text(entry.note)
                }
            }
        }
    }
}
