package com.moraswood.qande

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private data class Machine(
    val id: String, val name: String, val type: String, val issued: String,
    val running: Boolean, val history: List<String>
)

private fun readMachines(context: Context): List<Machine> = runCatching {
    val raw = context.getSharedPreferences("mwi_machine_registry", Context.MODE_PRIVATE).getString("machines", "[]")
    val a = JSONArray(raw)
    (0 until a.length()).map { i ->
        val m = a.getJSONObject(i)
        val h = m.optJSONArray("history") ?: JSONArray()
        Machine(m.getString("id"), m.getString("name"), m.optString("type"), m.optString("issued"),
            m.optBoolean("running", true), (0 until h.length()).map { h.getString(it) })
    }
}.getOrDefault(emptyList())

private fun writeMachines(context: Context, machines: List<Machine>) {
    val a = JSONArray()
    machines.forEach { m ->
        val h = JSONArray()
        m.history.forEach { h.put(it) }
        a.put(JSONObject().put("id", m.id).put("name", m.name).put("type", m.type)
            .put("issued", m.issued).put("running", m.running).put("history", h))
    }
    context.getSharedPreferences("mwi_machine_registry", Context.MODE_PRIVATE).edit().putString("machines", a.toString()).apply()
}

@Composable
fun MachineScreen(context: Context) {
    var machines by remember { mutableStateOf(readMachines(context)) }
    var view by remember { mutableStateOf("Overview") }
    var selected by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var issued by remember { mutableStateOf(LocalDate.now().toString()) }
    var problem by remember { mutableStateOf("") }
    var repairDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val save: (List<Machine>) -> Unit = { next -> machines = next; writeMachines(context, next) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Machines", style = MaterialTheme.typography.headlineSmall)
        if (selected != null) {
            val machine = machines.find { it.id == selected }
            if (machine != null) {
                TextButton(onClick = { selected = null }) { Text("Back to Machines") }
                Text(machine.name, style = MaterialTheme.typography.titleLarge)
                Text("ID: ${machine.id}")
                Text("Type: ${machine.type} | Issued: ${machine.issued}")
                Text(if (machine.running) "Running" else "Out of operation")
                OutlinedTextField(problem, { problem = it }, label = { Text("Problem / repair details") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repairDate, { repairDate = it }, label = { Text("Repair / issue date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = problem.isNotBlank() && runCatching { LocalDate.parse(repairDate) }.isSuccess, onClick = {
                    save(machines.map { if (it.id == machine.id) it.copy(history = it.history + "$repairDate: ${problem.trim()}") else it })
                    problem = ""
                }) { Text("Add dated maintenance record") }
                machine.history.forEach { Text(it) }
                Button(onClick = {
                    save(machines.map { if (it.id == machine.id) it.copy(running = !it.running) else it })
                }) { Text(if (machine.running) "Mark out of operation" else "Return to operation") }
            }
        } else {
            listOf("Running (${machines.count { it.running }})", "Scan QR / Add Machine", "Generate QR", "Machine Types", "Out of Operation (${machines.count { !it.running }})").forEach { option ->
                OutlinedCard(onClick = { view = option }, modifier = Modifier.fillMaxWidth()) {
                    Text(option, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            when {
                view.startsWith("Scan QR") -> {
                    Text("Add Machine", style = MaterialTheme.typography.titleMedium)
                    Text("QR scanning is not yet available. Add a machine manually below.")
                    OutlinedTextField(name, { name = it }, label = { Text("Machine name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(type, { type = it }, label = { Text("Machine type") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(issued, { issued = it }, label = { Text("Issued date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = name.isNotBlank() && runCatching { LocalDate.parse(issued) }.isSuccess, onClick = {
                        save(machines + Machine(UUID.randomUUID().toString(), name.trim(), type.trim(), issued, true, emptyList()))
                        name = ""; type = ""; view = "Overview"
                    }) { Text("Save machine") }
                }
                view == "Generate QR" -> Text("QR generation and printable labels are pending implementation. Every machine already has a stable unique ID.")
                view == "Machine Types" -> {
                    machines.groupBy { it.type.ifBlank { "Uncategorized" } }.forEach { (category, group) ->
                        Text("$category (${group.size})", style = MaterialTheme.typography.titleMedium)
                        group.forEach { m -> TextButton(onClick = { selected = m.id }) { Text(m.name) } }
                    }
                }
                else -> {
                    val visible = if (view.startsWith("Out of Operation")) machines.filterNot { it.running } else machines.filter { it.running }
                    visible.forEach { m ->
                        OutlinedCard(onClick = { selected = m.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium)
                                Text("${m.type} • Issued ${m.issued} • ${m.history.size} maintenance records")
                            }
                        }
                    }
                }
            }
        }
    }
}
