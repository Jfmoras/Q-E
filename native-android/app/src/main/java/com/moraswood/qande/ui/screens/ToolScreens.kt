package com.moraswood.qande.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.PortPendingCard

@Composable
private fun ToolPage(maxWidth: Int = 1120, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Column(Modifier.widthIn(max = maxWidth.dp).fillMaxWidth()) { content() } }
}

/** `#quickView` */
@Composable
fun QuickCalcScreen() = ToolPage {
    PageHeading("General CFT / KOL Calculator", "Independent of estimations. Length in feet, width and thickness in inches.")
    PortPendingCard("Calculator")
}

/** `#hoppusView` */
@Composable
fun HoppusScreen() = ToolPage {
    PageHeading("Hoppus Log Volume Calculator", "Quarter-girth method: girth² × length ÷ 2304, with girth in inches and length in feet.")
    PortPendingCard("Calculator")
}

/** `#notesView` */
@Composable
fun NotesScreen() = ToolPage(780) {
    PageHeading("Saved Notes", "Create and manage notes to reuse on quotations.")
    PortPendingCard("Settings (notes)")
}

/** `#settingsView` */
@Composable
fun SettingsScreen() = ToolPage(780) {
    PageHeading("Settings", "Configure an AI-compatible API and convert length measurements.")
    PortPendingCard("Settings")
}
