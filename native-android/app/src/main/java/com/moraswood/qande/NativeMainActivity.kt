package com.moraswood.qande

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch
import java.util.UUID

class NativeMainActivity : ComponentActivity() {
    private val database by lazy {
        Room.databaseBuilder(applicationContext, MwiDatabase::class.java, "mwi-native.db").build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = NativeFiles(applicationContext, database.dao())
        val migration = LegacyMigration(database.dao())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB8E47A), background = Color(0xFF171B1E), surface = Color(0xFF292F32), onSurface = Color(0xFFF0F3F2))) {
                val scope = rememberCoroutineScope()
                var tab by remember { mutableStateOf("Dashboard") }
                var projects by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
                var selected by remember { mutableStateOf<ProjectEntity?>(null) }
                var attachments by remember { mutableStateOf<List<AttachmentEntity>>(emptyList()) }
                var message by remember { mutableStateOf("") }
                var name by remember { mutableStateOf("") }
                var customer by remember { mutableStateOf("") }
                var amount by remember { mutableStateOf("") }
                var notes by remember { mutableStateOf("") }
                val refresh = {
                    scope.launch {
                        projects = database.dao().projects()
                        selected?.let { attachments = database.dao().attachments(it.id) }
                    }
                }
                LaunchedEffect(Unit) { refresh() }
                val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    val target = selected
                    if (target != null) scope.launch {
                        var saved = 0
                        uris.forEach { uri ->
                            try { storage.save(target.id, uri); saved++ }
                            catch (e: Exception) { message = "Attachment failed: ${e.message}" }
                        }
                        attachments = database.dao().attachments(target.id)
                        if (saved > 0) message = "Saved $saved attachment(s)"
                    }
                }
                val importLegacy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch {
                        try {
                            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                ?: error("Empty backup")
                            val count = migration.importProjects(json)
                            message = "Imported $count legacy project(s). Verify records before replacing your old app."
                            refresh()
                        } catch (e: Exception) { message = "Import failed: ${e.message}" }
                    }
                }
                val openAttachment = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
                Scaffold(topBar = {
                    Column(Modifier.fillMaxWidth().padding(top = 22.dp, start = 18.dp, end = 18.dp, bottom = 12.dp)) {
                        Text("MORAS WOOD INDUSTRIES", style = MaterialTheme.typography.titleMedium)
                        Text("MWI • $tab", style = MaterialTheme.typography.bodySmall)
                    }
                }) { insets ->
                    Column(Modifier.padding(insets).padding(horizontal = 16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Dashboard", "Projects", "Calculators").forEach { destination ->
                                TextButton(onClick = { tab = destination; selected = null }) { Text(destination) }
                            }
                        }
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.secondary)
                        when (tab) {
                            "Dashboard" -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
                                listOf(
                                    Triple("Quotation & Estimation", "CFT, estimation, quotation, saved notes, quotations and calculations", "Quotation & Estimation"),
                                    Triple("Hoppus Calculator", "Quarter-girth log volume and sawing charges", "Hoppus"),
                                    Triple("Projects & Payments", "Project details, quotations and payments", "Projects"),
                                    Triple("Inventory & Expenses", "Requirements to follow", "Inventory & Expenses"),
                                    Triple("Machines", "Running, QR, machine types and out-of-operation", "Machines"),
                                    Triple("Settings", "Backup and app settings", "Settings")
                                ).forEach { (title, description, destination) ->
                                    OutlinedCard(onClick = { tab = destination }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            "Quotation & Estimation" -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Quotation & Estimation", style = MaterialTheme.typography.headlineSmall)
                                listOf("CFT Calculator", "Estimation", "Quotation", "Saved Notes", "Saved Quotations", "Saved Calculations").forEach { item ->
                                    OutlinedCard(onClick = {
                                        if (item == "CFT Calculator") tab = "Calculators"
                                        else if (item == "Saved Notes") tab = "Notes"
                                        else tab = item
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text(item, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                Text("Native modules under construction. Existing WebView app is separate and unaffected.")
                            }
                            "Inventory & Expenses" -> Column {
                                Text("Inventory & Expenses", style = MaterialTheme.typography.headlineSmall)
                                Text("Requirements pending. Native module will be added after specifications are confirmed.")
                            }
                            "Machines" -> MachineScreen(this@NativeMainActivity)
                            "Estimation", "Quotation", "Saved Quotations", "Saved Calculations" -> Column {
                                Text(tab, style = MaterialTheme.typography.headlineSmall)
                                Text("Native implementation pending. No WebView fallback.")
                            }
                            "Hoppus" -> {
                                var girth by remember { mutableStateOf("") }
                                var logLength by remember { mutableStateOf("") }
                                var count by remember { mutableStateOf("1") }
                                var sawingRate by remember { mutableStateOf("") }
                                val g = girth.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                val l = logLength.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                val n = count.toIntOrNull()?.coerceAtLeast(0) ?: 0
                                val cft = g * g * l * n / 2304.0
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Hoppus Log Volume", style = MaterialTheme.typography.headlineSmall)
                                    Text("Quarter-girth method • girth in inches, length in feet")
                                    OutlinedTextField(girth, { girth = it }, label = { Text("Girth (inches)") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(logLength, { logLength = it }, label = { Text("Length (feet)") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(count, { count = it }, label = { Text("Number of logs") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(sawingRate, { sawingRate = it }, label = { Text("Sawing rate (₹/CFT)") }, modifier = Modifier.fillMaxWidth())
                                    Text("Hoppus volume: %.3f CFT".format(java.util.Locale.US, cft), style = MaterialTheme.typography.titleLarge)
                                    Text("Sawing charges: ₹%.2f".format(java.util.Locale.US, cft * (sawingRate.toDoubleOrNull() ?: 0.0)))
                                }
                            }
                            "Settings" -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                                OutlinedButton(onClick = { importLegacy.launch(arrayOf("application/json")) }) { Text("Import existing MWI JSON backup") }
                                Text("Only project metadata is currently supported. Full quotation, payment, expense and attachment migration is required before replacing the old app.")
                            }
                            "Notes" -> Column {
                                Text("Saved Notes", style = MaterialTheme.typography.headlineSmall)
                                Text("Native reusable notes are pending migration.")
                            }
                            "Projects" -> {
                                if (selected == null) {
                                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(name, { name = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(customer, { customer = it }, label = { Text("Customer") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(amount, { amount = it }, label = { Text("Agreed value ₹") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(notes, { notes = it }, label = { Text("Project notes") }, modifier = Modifier.fillMaxWidth())
                                        Button(enabled = name.isNotBlank() && customer.isNotBlank(), onClick = {
                                            scope.launch {
                                                database.dao().putProject(ProjectEntity(UUID.randomUUID().toString(), name.trim(), customer.trim(), amount.toDoubleOrNull() ?: 0.0, notes = notes))
                                                name = ""; customer = ""; amount = ""; notes = ""; refresh()
                                            }
                                        }) { Text("Save project") }
                                        projects.forEach { project ->
                                            OutlinedCard(onClick = {
                                                selected = project
                                                scope.launch { attachments = database.dao().attachments(project.id) }
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                                    Text("${project.customer} • ₹${project.agreedValue} • ${project.status}")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val project = selected!!
                                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TextButton(onClick = { selected = null }) { Text("← All projects") }
                                        Text(project.name, style = MaterialTheme.typography.headlineSmall)
                                        Text(project.customer)
                                        Text("Agreed: ₹${project.agreedValue}")
                                        Text("Notes: ${project.notes.ifBlank { "None" }}")
                                        Button(onClick = { pickFiles.launch(arrayOf("image/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) }) {
                                            Text("Attach photos or documents")
                                        }
                                        attachments.forEach { attachment ->
                                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(attachment.displayName)
                                                    TextButton(onClick = {
                                                        try { startActivity(storage.viewIntent(attachment)) }
                                                        catch (e: Exception) { message = "Cannot open file: ${e.message}" }
                                                    }) { Text("View / Open") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                var length by remember { mutableStateOf("") }
                                var width by remember { mutableStateOf("") }
                                var thickness by remember { mutableStateOf("") }
                                var qty by remember { mutableStateOf("1") }
                                var rate by remember { mutableStateOf("") }
                                val l = length.toDoubleOrNull() ?: 0.0
                                val w = width.toDoubleOrNull() ?: 0.0
                                val t = thickness.toDoubleOrNull() ?: 0.0
                                val n = qty.toIntOrNull()?.coerceAtLeast(0) ?: 0
                                val unit = if (w <= 6.0) "CFT" else "KOL"
                                val volume = TimberMath.volume(l, w, t, n)
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Timber Calculator", style = MaterialTheme.typography.headlineSmall)
                                    OutlinedTextField(length, { length = it }, label = { Text("Length (feet)") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(width, { width = it }, label = { Text("Width (inches)") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(thickness, { thickness = it }, label = { Text("Thickness (inches)") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(qty, { qty = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(rate, { rate = it }, label = { Text("Rate per $unit (₹)") }, modifier = Modifier.fillMaxWidth())
                                    Text("Volume: %.3f %s".format(java.util.Locale.US, volume, unit), style = MaterialTheme.typography.titleLarge)
                                    Text("Timber cost: ₹%.2f".format(java.util.Locale.US, volume * (rate.toDoubleOrNull() ?: 0.0)))
                                    Text("Other calculators, grouped rates, estimates and quotations are pending migration.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
