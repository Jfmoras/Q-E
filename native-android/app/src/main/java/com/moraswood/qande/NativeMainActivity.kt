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
            MaterialTheme(colorScheme = darkColorScheme()) {
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
                                Text("Native MWI foundation", style = MaterialTheme.typography.headlineSmall)
                                Text("Project records and file attachments are stored natively. Other existing modules are being migrated.")
                                Button(onClick = { tab = "Projects" }) { Text("Projects & Payments") }
                                OutlinedButton(onClick = { importLegacy.launch(arrayOf("application/json")) }) { Text("Import old MWI JSON backup") }
                                Text("Do not uninstall the original app. This importer currently covers project metadata only, not quotation images or uploaded documents.")
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
                            else -> Column {
                                Text("Calculators", style = MaterialTheme.typography.headlineSmall)
                                Text("CFT / KOL, Hoppus, estimates and quotation screens are pending migration. The old app remains the production version.")
                            }
                        }
                    }
                }
            }
        }
    }
}
