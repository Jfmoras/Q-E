package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.PortPendingCard
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors

/** V10.1 workspace tabs, in order (`data-tab` ids). */
enum class WorkspaceTab(val id: String, val label: String) {
    Calculator("calculator", "Calculator"),
    Estimation("estimation", "Estimation"),
    Quotation("quotation", "Quotation"),
    Review("review", "Job Review");

    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: Calculator }
}

@Composable
fun WorkspaceScreen(
    tab: WorkspaceTab,
    onTab: (WorkspaceTab) -> Unit,
    projectName: String,
    onProjectName: (String) -> Unit,
    projectStatus: String,
    onSaveProject: () -> Unit,
    onShowProjects: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WorkspaceTabs(tab, onTab)
        ProjectToolbar(projectName, onProjectName, projectStatus, onSaveProject, onShowProjects)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 48.dp),
        ) {
            when (tab) {
                WorkspaceTab.Calculator -> {
                    PageHeading("Timber Calculator", "Choose a unit for each measurement. The app converts Length to feet and Width/Thickness to inches automatically for CFT/KOL. Press Enter to move L → W → T → Pieces → next row.")
                    PortPendingCard("Calculator")
                }
                WorkspaceTab.Estimation -> {
                    PageHeading("Estimation & Rate Builder", "Price every item separately. CFT/KOL rates, labour, polish and selling price can all differ item by item.")
                    PortPendingCard("Estimation")
                }
                WorkspaceTab.Quotation -> PortPendingCard("Quotation")
                WorkspaceTab.Review -> {
                    PageHeading("Actual Cost / Job Review", "Compare what you estimated with what actually happened.")
                    PortPendingCard("Projects/payments")
                }
            }
        }
    }
}

/** `.tabs` — sticky bar #22282b, active tab #b8e47a with 3px underline, inactive #c5cecf. */
@Composable
private fun WorkspaceTabs(active: WorkspaceTab, onTab: (WorkspaceTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(QeColors.TabBar)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            WorkspaceTab.entries.forEach { t ->
                val selected = t == active
                Column(Modifier.width(IntrinsicSize.Max).clickable { onTab(t) }) {
                    Text(
                        t.label,
                        color = if (selected) QeColors.Accent else QeColors.TabInactive,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) QeColors.Accent else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QeColors.Line))
    }
}

/** `.project-toolbar` — project name, Save Project, Saved Projects, status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectToolbar(name: String, onName: (String) -> Unit, status: String, onSave: () -> Unit, onShow: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(QeColors.TabBar)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QeTextField(name, onName, placeholder = "Project / job name", modifier = Modifier.fillMaxWidth())
            QePrimaryButton("Save Project", onClick = onSave)
            QeSecondaryButton("Saved Projects", onClick = onShow)
            if (status.isNotEmpty()) Text(status, color = QeColors.Muted, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QeColors.Line))
    }
}
