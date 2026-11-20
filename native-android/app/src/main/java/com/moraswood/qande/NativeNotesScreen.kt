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

@Composable
fun NativeNotesScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("mwi_native_notes", Context.MODE_PRIVATE) }
    fun read(): List<String> = runCatching {
        val a = JSONArray(prefs.getString("notes", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    var notes by remember { mutableStateOf(read()) }
    var draft by remember { mutableStateOf("") }
    fun persist(next: List<String>) {
        notes = next
        val array = JSONArray()
        next.forEach { array.put(it) }
        prefs.edit().putString("notes", array.toString()).apply()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Saved Notes", style = MaterialTheme.typography.headlineSmall)
        Text("Reusable quotation terms and notes")
        OutlinedTextField(draft, { draft = it }, label = { Text("New note") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button(enabled = draft.isNotBlank(), onClick = {
            persist(notes + draft.trim()); draft = ""
        }) { Text("Save note") }
        notes.forEachIndexed { index, note ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(note)
                    Row {
                        TextButton(onClick = { draft = note; persist(notes.filterIndexed { i, _ -> i != index }) }) { Text("Edit") }
                        TextButton(onClick = { persist(notes.filterIndexed { i, _ -> i != index }) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
