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
import com.moraswood.qande.ui.screens.HomeScreen
import com.moraswood.qande.ui.screens.LogCalculatorScreen
import com.moraswood.qande.ui.screens.NotesScreen
import com.moraswood.qande.ui.screens.OperationsScreen
import com.moraswood.qande.ops.OperationsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moraswood.qande.ui.screens.OperationsTopBar
import com.moraswood.qande.ui.screens.OpsTab
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
    const val LENGTH = "length"
    const val TIMBER = "timber"
    const val HOPPUS = "hoppus"
    const val SETTINGS = "settings"
    const val OPERATIONS = "operations"
    const val BUILDER = "builder"
    const val CLIENTS = "clients"
    const val CALENDAR = "calendar"
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
    val avm: com.moraswood.qande.assets.AssetsViewModel = viewModel()
    val svm: com.moraswood.qande.settings.SettingsViewModel = viewModel()
    val yvm: com.moraswood.qande.yieldtracker.YieldViewModel = viewModel()
    val hoppus by vm.hoppus.collectAsState()
    val templates by vm.templates.collectAsState()
    val banner by vm.templateBanner.collectAsState()
    val templateStatus by vm.templateStatus.collectAsState()
    val estimationBanner by vm.estimationBanner.collectAsState()
    val estimationStatus by vm.estimationStatus.collectAsState()
    var confirmNewProject by rememberSaveable { mutableStateOf(false) }
    // Feature L: QR dialog, scan messages, qe://job links
    var qrJob by remember { mutableStateOf<com.moraswood.qande.data.SavedProjectEntity?>(null) }
    var scanAlert by remember { mutableStateOf<String?>(null) }
    val pendingLink by vm.pendingJobLink.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pendingLink, savedProjects) {
        val id = pendingLink ?: return@LaunchedEffect
        if (savedProjects.isEmpty()) return@LaunchedEffect
        vm.setPendingJobLink(null)
        if (vm.openJobByUuid(id)) { savedPanelOpen = false; nav.openView(Routes.WORKSPACE) } else scanAlert = "No saved job matches this QR code."
    }

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
                qrJob?.let { x ->
                    val title = com.moraswood.qande.logic.QeRevisions.label(x.name, x.revision)
                    com.moraswood.qande.ui.screens.JobQrDialog(title, x.jobUuid, onPrint = { com.moraswood.qande.print.QrLabelPrint.print(context, title, x.jobUuid) }) { qrJob = null }
                }
                com.moraswood.qande.ui.components.QeAlert(scanAlert) { scanAlert = null }
                HomeScreen(
                    showContinue = vm.hasProjectWork(),
                    savedPanelOpen = savedPanelOpen,
                    savedProjects = savedProjects,
                    onViewProject = { id -> if (vm.viewSavedProject(id)) { savedPanelOpen = false; nav.openView(Routes.WORKSPACE) } },
                    onReviseProject = { id -> if (vm.reviseSavedProject(id)) { savedPanelOpen = false; nav.openView(Routes.WORKSPACE) } },
                    onDeleteProject = vm::deleteSavedProject,
                    onOpenLatest = { root -> if (vm.openLatestRevision(root)) { savedPanelOpen = false; nav.openView(Routes.WORKSPACE) } },
                    onPrintRevision = { id -> vm.savedSnapshot(id)?.let { st -> vm.withSignature(st) { sig -> QuotePrint.print(context, st, sig) } } },
                    onJobQr = { x -> qrJob = x },
                    onDuplicate = { id -> vm.duplicateSavedProject(id) { msg -> scanAlert = msg } },
                    onScanQr = {
                        com.moraswood.qande.ui.screens.JobQrScanner.scan(context, { raw ->
                            val id = com.moraswood.qande.logic.QeQr.parse(raw)
                            if (id == null) scanAlert = "This QR code is not a Moras Wood job label."
                            else if (vm.openJobByUuid(id)) { savedPanelOpen = false; nav.openView(Routes.WORKSPACE) }
                            else scanAlert = "No saved job matches this QR code."
                        }, { scanAlert = it })
                    },
                    onHoppus = { nav.openView(Routes.HOPPUS) },
                    onBuilder = { nav.openView(Routes.BUILDER) },
                    onClients = { nav.openView(Routes.CLIENTS) },
                    onCalendar = { nav.openView(Routes.CALENDAR) },
                    onNewProject = { startNewProject() },
                    onSavedProjects = { savedPanelOpen = true },
                    onCloseSaved = { savedPanelOpen = false },
                    onOperations = { opsTab = OpsTab.Projects; ovm.resetPage(); avm.resetPage(); nav.openView(Routes.OPERATIONS) },
                    onNotes = { nav.openView(Routes.NOTES) },
                    onLengthConverter = { nav.openView(Routes.LENGTH) },
                    onTimberCalculator = { nav.openView(Routes.TIMBER) },
                    onProjectsPayments = { opsTab = OpsTab.Projects; ovm.resetPage(); avm.resetPage(); nav.openView(Routes.OPERATIONS) },
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
                                    onGroupItems = vm::groupItems,
                                    onUngroupItems = vm::ungroupItems,
                                    onItemGroupField = vm::setItemGroupField,
                                    onToggleItemGroup = vm::toggleItemGroup,
                                    onRowFace = vm::setEstimationRowFace,
                                    onMoveCategory = vm::moveEstimationCategory,
                                    onCategoryDefault = vm::setCategoryDefault,
                                )
                            },
                            groups = workspace.itemGroups.orEmpty(),
                            categoryDefaults = workspace.categoryDefaults,
                        )
                    },
                    quotation = {
                        QuotationPanel(
                            state = workspace,
                            savedNotes = savedNotes,
                            noteStatus = noteStatus,
                            aiStatus = aiStatus,
                            aiBusy = aiBusy,
                            clients = vm.clients.collectAsState().value,
                            signature = vm.signature.collectAsState().value,
                            actions = remember(vm) {
                                QuotationActions(
                                    onPrint = { val st = vm.workspace.value; vm.withSignature(st) { sig -> QuotePrint.print(context, st, sig) } },
                                    onExport = {
                                        val err = vm.exportToProjects()
                                        if (err == null) { opsTab = OpsTab.Projects; ovm.resetPage(); avm.resetPage(); ovm.consumePendingImport(); nav.openView(Routes.OPERATIONS) }
                                        err
                                    },
                                    onCustomerName = vm::setCustomerName,
                                    onCustomerLocation = vm::setCustomerLocation,
                                    onPickClient = vm::pickClient,
                                    onSaveAsClient = vm::saveCustomerAsClient,
                                    onDeliveryDate = vm::setDeliveryDate,
                                    onSaveSignature = vm::saveSignature,
                                    onClearSignature = vm::clearSignature,
                                    onSelectOption = vm::selectQuoteOption,
                                    onOptionName = vm::setQuoteOptionName,
                                    onAddOption = vm::addQuoteOption,
                                    onDuplicateOption = vm::duplicateQuoteOption,
                                    onDuplicateAsSpecies = vm::duplicateAsSpecies,
                                    onOptionSpecies = vm::setQuoteOptionSpecies,
                                    onPrintComparison = vm::setPrintComparison,
                                    onPrintArea = vm::setPrintArea,
                                    onGroupByCategory = vm::setQuoteGroupByCategory,
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
            composable(Routes.TIMBER) { com.moraswood.qande.ui.screens.TimberCalculatorScreen() }
            composable(Routes.LENGTH) { com.moraswood.qande.ui.screens.LengthConverterScreen() }
            composable(Routes.HOPPUS) {
                LogCalculatorScreen(hoppus, remember(vm) { HoppusActions(vm::setHoppusField, vm::addHoppusLog, vm::removeHoppusLog, vm::setSawRate) }, yvm)
            }
            composable(Routes.SETTINGS) {
                val dwDefaults by vm.dwDefaults.collectAsState()
                SettingsScreen(templates, vm::deleteTemplate, svm, dwDefaults, vm::setDwDefaults)
            }
            composable(Routes.BUILDER) {
                com.moraswood.qande.ui.screens.DoorWindowScreen(vm, onOpenCalculator = { nav.openView(Routes.WORKSPACE) }, onPrintCutList = {
                    com.moraswood.qande.print.CutListPrint.print(context, com.moraswood.qande.logic.QeCutList.build(vm.workspace.value, vm.dwDefaults.value, java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))))
                })
            }
            composable(Routes.CALENDAR) {
                val entries by vm.calendarEntries.collectAsState()
                androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshToday() }
                com.moraswood.qande.ui.screens.CalendarScreen(entries, java.time.LocalDate.now(),
                    onOpen = { root -> if (vm.openLatestRevision(root)) nav.openView(Routes.WORKSPACE) },
                    onCompleted = vm::setCompleted)
            }
            composable(Routes.CLIENTS) {
                val summaries by vm.clientSummaries.collectAsState()
                com.moraswood.qande.ui.screens.ClientsScreen(summaries, vm::saveClient, vm::deleteClient,
                    onOpenJob = { root -> if (vm.openLatestRevision(root)) nav.openView(Routes.WORKSPACE) })
            }
            composable(Routes.OPERATIONS) { OperationsScreen(opsTab, { opsTab = it }, ovm, avm) }
        }
    }
}
