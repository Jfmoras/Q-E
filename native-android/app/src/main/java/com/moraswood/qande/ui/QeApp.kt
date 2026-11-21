package com.moraswood.qande.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moraswood.qande.AppViewModel
import com.moraswood.qande.ui.components.QeConfirm
import com.moraswood.qande.ui.components.QeTopBar
import com.moraswood.qande.ui.screens.CalculatorActions
import com.moraswood.qande.ui.screens.CalculatorPanel
import com.moraswood.qande.ui.screens.EstimationActions
import com.moraswood.qande.ui.screens.EstimationPanel
import com.moraswood.qande.ui.screens.HoppusActions
import com.moraswood.qande.ui.screens.QuickActions
import com.moraswood.qande.ui.screens.HomeScreen
import com.moraswood.qande.ui.screens.HoppusScreen
import com.moraswood.qande.ui.screens.NotesScreen
import com.moraswood.qande.ui.screens.OperationsScreen
import com.moraswood.qande.ops.OperationsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moraswood.qande.ui.screens.OperationsTopBar
import com.moraswood.qande.ui.screens.OpsTab
import com.moraswood.qande.ui.screens.QuickCalcScreen
import com.moraswood.qande.ui.screens.SettingsScreen
import com.moraswood.qande.ui.screens.WorkspaceScreen
import com.moraswood.qande.ui.screens.WorkspaceTab
import com.moraswood.qande.ui.screens.QuotationActions
import com.moraswood.qande.ui.screens.QuotationPanel
import com.moraswood.qande.ui.screens.ReviewPanel
import com.moraswood.qande.print.QuotePrint
import com.moraswood.qande.ui.theme.QeColors

/** V10.1 views (`showView`) as navigation routes. Every view sits directly above Home, like the WebView history. */
object Routes {
    const val HOME = "home"
    const val WORKSPACE = "workspace"
    const val NOTES = "notes"
    const val QUICK = "quick"
    const val HOPPUS = "hoppus"
    const val SETTINGS = "settings"
    const val OPERATIONS = "operations"
}

private fun NavHostController.openView(route: String) = navigate(route) {
    popUpTo(Routes.HOME)
    launchSingleTop = true
}

private fun NavHostController.goHome() {
    if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME)
}

@Composable
fun QeApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.HOME
    val activity = LocalContext.current as? Activity
    val workspace by vm.workspace.collectAsState()
    val savedProjects by vm.savedProjects.collectAsState()
    var savedPanelOpen by rememberSaveable { mutableStateOf(false) }
    val projectStatus by vm.projectStatus.collectAsState()
    val savedNotes by vm.savedNotes.collectAsState()
    val noteStatus by vm.noteStatus.collectAsState()
    val aiStatus by vm.aiStatus.collectAsState()
    val aiBusy by vm.aiBusy.collectAsState()
    val context = LocalContext.current
    var opsTab by rememberSaveable { mutableStateOf(OpsTab.Projects) }
    val ovm: OperationsViewModel = viewModel()
    val quick by vm.quick.collectAsState()
    val hoppus by vm.hoppus.collectAsState()
    val templates by vm.templates.collectAsState()
    val banner by vm.templateBanner.collectAsState()
    val templateStatus by vm.templateStatus.collectAsState()
    val estimationBanner by vm.estimationBanner.collectAsState()
    val estimationStatus by vm.estimationStatus.collectAsState()
    var confirmNewProject by rememberSaveable { mutableStateOf(false) }

    // app.js startNewProject(): confirm only when there is work to lose.
    fun startNewProject() {
        if (vm.hasProjectWork()) { confirmNewProject = true; return }
        vm.startNewProject(); nav.openView(Routes.WORKSPACE)
    }
    QeConfirm(
        if (confirmNewProject) "Start a new project? Your current unsaved work will be replaced. Save the current project first if you want to keep it." else null,
    ) { ok ->
        confirmNewProject = false
        if (ok) { vm.startNewProject(); nav.openView(Routes.WORKSPACE) }
    }

    // Back-button fix: with no navigation history left, finish() the activity instead of doing nothing.
    BackHandler(enabled = entry != null && nav.previousBackStackEntry == null) { activity?.finish() }

    Column(Modifier.fillMaxSize().background(QeColors.Bg).navigationBarsPadding().imePadding()) {
        if (route == Routes.OPERATIONS) OperationsTopBar(onHome = { nav.goHome() })
        else QeTopBar(showHome = route != Routes.HOME, onHome = { nav.goHome() })

        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
            composable(Routes.HOME) {
                HomeScreen(
                    showContinue = vm.hasProjectWork(),
                    savedPanelOpen = savedPanelOpen,
                    savedProjects = savedProjects,
                    onViewProject = { id -> if (vm.viewSavedProject(id)) { savedPanelOpen = false; nav.openView(Routes.WORKSPACE) } },
                    onReviseProject = { id -> if (vm.reviseSavedProject(id)) { savedPanelOpen = false; nav.openView(Routes.WORKSPACE) } },
                    onDeleteProject = vm::deleteSavedProject,
                    onHoppus = { nav.openView(Routes.HOPPUS) },
                    onNewProject = { startNewProject() },
                    onSavedProjects = { savedPanelOpen = true },
                    onCloseSaved = { savedPanelOpen = false },
                    onOperations = { opsTab = OpsTab.Projects; ovm.resetPage(); nav.openView(Routes.OPERATIONS) },
                    onNotes = { nav.openView(Routes.NOTES) },
                    onQuickCalc = { nav.openView(Routes.QUICK) },
                    onSettings = { nav.openView(Routes.SETTINGS) },
                    onContinue = { nav.openView(Routes.WORKSPACE) },
                )
            }
            composable(Routes.WORKSPACE) {
                WorkspaceScreen(
                    tab = WorkspaceTab.fromId(workspace.tab),
                    onTab = { vm.setTab(it.id) },
                    projectName = workspace.projectName,
                    onProjectName = vm::setProjectName,
                    projectStatus = projectStatus,
                    onSaveProject = vm::saveProject,
                    onShowProjects = { savedPanelOpen = true; nav.goHome() },
                    calculator = {
                        CalculatorPanel(
                            items = workspace.items,
                            banner = banner,
                            templateStatus = templateStatus,
                            actions = remember(vm) {
                                CalculatorActions(
                                    onItemName = vm::setItemName,
                                    onRowField = vm::setRowField,
                                    onAddRow = vm::addRow,
                                    onNewItem = vm::addItem,
                                    onSendToEstimation = vm::sendToEstimation,
                                    onItemTag = vm::setItemTag,
                                    onItemPhoto = vm::setItemPhoto,
                                    onSaveTemplate = vm::saveItemAsTemplate,
                                    onUseTemplate = vm::applyTemplate,
                                    onDismissTemplate = vm::dismissTemplate,
                                )
                            },
                        )
                    },
                    estimation = {
                        EstimationPanel(
                            items = workspace.estimationItems,
                            costings = workspace.costings,
                            status = estimationStatus,
                            banner = estimationBanner,
                            actions = remember(vm) {
                                EstimationActions(
                                    onName = vm::setEstimationName,
                                    onField = vm::setCostingField,
                                    onGroup = vm::groupTimberRows,
                                    onUngroup = vm::ungroupTimberRows,
                                    onGroupRate = vm::setGroupRate,
                                    onRemoveGroup = vm::removeTimberGroup,
                                    onNewItem = vm::newEstimationItem,
                                    onSendToQuotation = vm::sendToQuotation,
                                    onSendOne = vm::sendOneToQuotation,
                                    onPhoto = vm::setEstimationPhoto,
                                    onUseTemplate = vm::applyTemplate,
                                    onDismissTemplate = vm::dismissTemplate,
                                )
                            },
                        )
                    },
                    quotation = {
                        QuotationPanel(
                            state = workspace,
                            savedNotes = savedNotes,
                            noteStatus = noteStatus,
                            aiStatus = aiStatus,
                            aiBusy = aiBusy,
                            actions = remember(vm) {
                                QuotationActions(
                                    onPrint = { QuotePrint.print(context, vm.workspace.value) },
                                    onExport = {
                                        val err = vm.exportToProjects()
                                        if (err == null) { opsTab = OpsTab.Projects; ovm.resetPage(); ovm.consumePendingImport(); nav.openView(Routes.OPERATIONS) }
                                        err
                                    },
                                    onCustomerName = vm::setCustomerName,
                                    onCustomerLocation = vm::setCustomerLocation,
                                    onSelectOption = vm::selectQuoteOption,
                                    onOptionName = vm::setQuoteOptionName,
                                    onAddOption = vm::addQuoteOption,
                                    onDuplicateOption = vm::duplicateQuoteOption,
                                    canRemoveOption = vm::canRemoveQuoteOption,
                                    onRemoveOption = vm::removeQuoteOption,
                                    onField = vm::setQuoteField,
                                    onRemoveItem = vm::removeQuoteItem,
                                    onAddItem = vm::addQuoteItem,
                                    onImprove = vm::improveDescription,
                                    onUndoAi = vm::undoAi,
                                    onAddImages = vm::addQuoteImages,
                                    onRemoveImage = vm::removeQuoteImage,
                                    onDiscountType = vm::setDiscountType,
                                    onDiscountValue = vm::setDiscountValue,
                                    onGst = vm::setGstPercent,
                                    onAddNote = vm::addNote,
                                    onNote = vm::setNote,
                                    onSaveNote = vm::saveNote,
                                    onRemoveNote = vm::removeNote,
                                    onAddSavedNote = vm::addSavedNote,
                                    onDeleteSavedNote = vm::deleteSavedNote,
                                    onEditSavedNote = vm::editSavedNote,
                                )
                            },
                        )
                    },
                    review = { ReviewPanel(workspace, vm::setActualMaterials, vm::setActualLabourHours) },
                )
            }
            composable(Routes.NOTES) { NotesScreen(vm) }
            composable(Routes.QUICK) {
                QuickCalcScreen(
                    quick,
                    remember(vm) { QuickActions(vm::setQuickRate, vm::setQuickField, vm::addQuickRow, vm::setQuickGroupRate, vm::ungroupQuick, vm::groupQuickRows) },
                )
            }
            composable(Routes.HOPPUS) {
                HoppusScreen(hoppus, remember(vm) { HoppusActions(vm::setHoppusField, vm::addHoppusLog, vm::removeHoppusLog, vm::setSawRate) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(templates, vm::deleteTemplate) }
            composable(Routes.OPERATIONS) { OperationsScreen(opsTab, { opsTab = it }, ovm) }
        }
    }
}
