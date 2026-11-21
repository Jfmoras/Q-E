package com.moraswood.qande.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QePrimaryButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.theme.QeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One `.home-action` card. Glyph follows the V10.1 :nth-child rule (▦ ▤ ◈ ↻ ▣, then ▦). */
private data class HomeAction(val title: String, val subtitle: String, val onClick: () -> Unit)

private fun glyphFor(position: Int) = when (position) { 2 -> "▤"; 3 -> "◈"; 4 -> "↻"; 5 -> "▣"; else -> "▦" }

@Composable
fun HomeScreen(
    showContinue: Boolean,
    savedPanelOpen: Boolean,
    savedProjects: List<SavedProjectEntity>,
    onViewProject: (String) -> Unit,
    onReviseProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onHoppus: () -> Unit,
    onBuilder: () -> Unit = {},
    onNewProject: () -> Unit,
    onSavedProjects: () -> Unit,
    onCloseSaved: () -> Unit,
    onOperations: () -> Unit,
    onNotes: () -> Unit,
    onQuickCalc: () -> Unit,
    onSettings: () -> Unit,
    onContinue: () -> Unit,
    onOpenLatest: (String) -> Unit = {},
    onPrintRevision: (String) -> Unit = {},
) {
    val actions = buildList {
        add(HomeAction("Log Calculator", "Hoppus log volume, sawing charges and the Yield Tracker.", onHoppus))
        add(HomeAction("New Project", "Start a calculator, estimate and quotation.", onNewProject))
        add(HomeAction("Door & Window Builder", "Frames, planking and architrave for the current project → CFT rows and a cut list.", onBuilder))
        add(HomeAction("Saved Projects", "Open or remove a project saved on this device.", onSavedProjects))
        add(HomeAction("Projects · Expenses · Inventory · Machines", "Manage project payments, material stock, machine status and monthly reports.", onOperations))
        add(HomeAction("Notes", "Manage notes to reuse in quotations.", onNotes))
        add(HomeAction("CFT / KOL Calculator", "Independent timber sizes, color groups and prices.", onQuickCalc))
        add(HomeAction("⚙ Settings", "AI, length converter, backup and restore.", onSettings))
        if (showContinue) add(HomeAction("Continue Current Project", "Resume your latest automatically saved work.", onContinue))
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.chunked(2).forEachIndexed { rowIndex, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { i, action ->
                        HomeActionCard(action, glyphFor(rowIndex * 2 + i + 1), Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (savedPanelOpen) SavedProjectsPanel(savedProjects, onCloseSaved, onViewProject, onReviseProject, onDeleteProject, onOpenLatest, onPrintRevision)
    }
}

@Composable
private fun HomeActionCard(action: HomeAction, glyph: String, modifier: Modifier) {
    Column(
        modifier
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(QeColors.Card)
            .border(1.dp, QeColors.HeaderLine, RoundedCornerShape(20.dp))
            .clickable(onClick = action.onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(QeColors.IconTile), contentAlignment = Alignment.Center) {
            Text(glyph, color = QeColors.Accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Text(action.title, color = QeColors.HomeCardText, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
        Text(action.subtitle, color = QeColors.HomeCardSub, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

private val savedDateFormat = DateTimeFormatter.ofPattern("d/M/yyyy, h:mm:ss a", Locale.forLanguageTag("en-IN"))

/**
 * `#savedProjectsPanel` (renderSavedProjects), Feature E: one card per job "Name · R{latest}" with a revision picker.
 * Open = latest revision, editable; View / Print, Print, Revise and Delete act on the picked revision.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SavedProjectsPanel(
    list: List<SavedProjectEntity>, onClose: () -> Unit,
    onView: (String) -> Unit, onRevise: (String) -> Unit, onDelete: (String) -> Unit,
    onOpenLatest: (String) -> Unit, onPrint: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    QeConfirm(if (confirmDelete != null) "Delete this saved revision?" else null) { ok ->
        val id = confirmDelete; confirmDelete = null
        if (ok && id != null) onDelete(id)
    }
    Column(
        Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(top = 20.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(QeColors.Card)
            .border(1.dp, QeColors.Line, RoundedCornerShape(16.dp)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saved Projects & Revisions", color = QeColors.Text, fontWeight = FontWeight.Bold)
            QeSecondaryButton("Close", onClick = onClose)
        }
        if (list.isEmpty()) {
            Text("No saved projects yet.", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            com.moraswood.qande.logic.QeRevisions.jobs(list).forEach { job ->
                var picked by remember(job.rootId, job.latest.id) { mutableStateOf(job.latest.id) }
                val x = job.revisions.firstOrNull { it.id == picked } ?: job.latest
                val isLatest = x.id == job.latest.id
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
                        .border(1.dp, QeColors.Line, RoundedCornerShape(16.dp)).background(QeColors.Card).padding(10.dp),
                ) {
                    Text(com.moraswood.qande.logic.QeRevisions.label(job.name, job.latest.revision), color = QeColors.Text, fontWeight = FontWeight.Bold)
                    val date = runCatching { savedDateFormat.format(Instant.parse(x.updatedAt).atZone(ZoneId.systemDefault())) }.getOrDefault(x.updatedAt)
                    Text(
                        "R${x.revision}" + (if (isLatest) " (latest)" else " (read-only)") + (x.basedOn?.let { " · from R$it" } ?: "") + " · " + date,
                        color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                    if (job.revisions.size > 1) androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        job.revisions.forEach { r ->
                            val on = r.id == x.id
                            Text(
                                "R${r.revision}", color = if (on) QeColors.OnAccent else QeColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) QeColors.Accent else QeColors.SecondaryBg)
                                    .clickable { picked = r.id }.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QePrimaryButton("Open R${job.latest.revision}", onClick = { onOpenLatest(job.rootId) })
                        QeSecondaryButton("View / Print R${x.revision}", onClick = { onView(x.id) })
                        QeSecondaryButton("Print R${x.revision}", onClick = { onPrint(x.id) })
                        QeSecondaryButton("Revise from R${x.revision}", onClick = { onRevise(x.id) })
                        QeDangerButton("Delete R${x.revision}") { confirmDelete = x.id }
                    }
                }
            }
        }
    }
}
