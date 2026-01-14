package com.example.fieldmaintenance.ui.screens

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.activity.compose.BackHandler
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import kotlinx.coroutines.launch
import com.example.fieldmaintenance.data.model.PassiveType
import com.example.fieldmaintenance.data.model.isGeneralInfoComplete
import com.example.fieldmaintenance.ui.components.ReportBottomBar
import com.example.fieldmaintenance.ui.components.ReportTab
import com.example.fieldmaintenance.util.EmailManager
import com.example.fieldmaintenance.util.ExportManager
import com.example.fieldmaintenance.util.MaintenanceStorage
import com.example.fieldmaintenance.util.PlanCache
import com.example.fieldmaintenance.util.PlanRepository
import com.example.fieldmaintenance.util.SettingsStore
import com.example.fieldmaintenance.util.AppSettings
import com.example.fieldmaintenance.util.hasIncompleteAssets
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralInfoScreen(navController: NavController, reportId: String) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    
    val repository = DatabaseProvider.getRepository()
    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    
    val report by viewModel.report.collectAsState()
    val passives by repository.getPassivesByReportId(reportId).collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportManager = remember { ExportManager(context, repository) }
    val planRepo = remember { PlanRepository(context.applicationContext) }
    val planCache by planRepo.cacheFlow().collectAsState(initial = PlanCache())
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val settings by settingsStore.settings.collectAsState(initial = AppSettings())
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var hasMissingAssets by remember { mutableStateOf(false) }
    
    var eventName by remember { mutableStateOf(report?.eventName ?: "") }
    var nodeName by remember { mutableStateOf(report?.nodeName ?: "") }
    var responsible by remember { mutableStateOf(report?.responsible ?: "") }
    var contractor by remember { mutableStateOf(report?.contractor ?: "") }
    var meterNumber by remember { mutableStateOf(report?.meterNumber ?: "") }
    var attemptedSave by remember { mutableStateOf(false) }

    // Track manual edits so autofill doesn't overwrite what the user typed.
    var eventTouched by remember { mutableStateOf(false) }
    var contractorTouched by remember { mutableStateOf(false) }
    var responsibleTouched by remember { mutableStateOf(false) }
    var meterTouched by remember { mutableStateOf(false) }
    var eventAutoFilled by remember { mutableStateOf(false) }
    var contractorAutoFilled by remember { mutableStateOf(false) }
    
    suspend fun deleteDraftIfEmptyAndIncomplete() {
        val r = repository.getReportById(reportId) ?: return
        if (r.isGeneralInfoComplete()) return

        val hasAssets = repository.listAssetsByReportId(r.id).isNotEmpty()
        val hasPassives = repository.listPassivesByReportId(r.id).isNotEmpty()
        val hasReportPhotos = repository.listReportPhotosByReportId(r.id).isNotEmpty()
        if (!hasAssets && !hasPassives && !hasReportPhotos) {
            repository.deleteReportPermanently(r)
        }
    }

    LaunchedEffect(report, settings, planCache) {
        report?.let {
            eventName = it.eventName
            nodeName = it.nodeName
            responsible = it.responsible
            contractor = it.contractor
            meterNumber = it.meterNumber
            eventTouched = false
            contractorTouched = false
            eventAutoFilled = false
            contractorAutoFilled = false
            responsibleTouched = false
            meterTouched = false

            // Apply defaults only if report fields are empty (and user hasn't edited).
            if (responsible.isBlank() && settings.defaultResponsible.isNotBlank()) {
                responsible = settings.defaultResponsible
            }
            // Contratista: por defecto desde Configuración (pero editable en pantalla).
            if (contractor.isBlank() && settings.defaultContractor.isNotBlank()) {
                contractor = settings.defaultContractor
                // This is a default, not a plan-autofill.
                contractorAutoFilled = false
                contractorTouched = false
            }
            if (meterNumber.isBlank() && settings.defaultMeterNumber.isNotBlank()) {
                meterNumber = settings.defaultMeterNumber
            }
        }
    }

    fun normalizeNode(v: String): String =
        v.trim().replace(Regex("\\s+"), " ").uppercase(Locale.getDefault())

    fun findPlanRowByNode(): com.example.fieldmaintenance.util.PlanRow? {
        val key = normalizeNode(nodeName)
        if (key.isBlank()) return null
        return planCache.rows.firstOrNull { normalizeNode(it.nodeCmts) == key }
    }

    fun applyPlanToFields(row: com.example.fieldmaintenance.util.PlanRow?, showNoMatchWarning: Boolean) {
        val hasPlan = planCache.rows.isNotEmpty()
        val key = normalizeNode(nodeName)

        // If node is cleared, also clear any values that were auto-filled from the plan.
        if (key.isBlank()) {
            if (eventAutoFilled && !eventTouched) {
                eventName = ""
                eventAutoFilled = false
                eventTouched = false
            }
            return
        }

        if (row == null) {
            // If plan exists but node not found, clear auto-filled values and warn when requested.
            if (hasPlan) {
                // IMPORTANT: No borrar un evento previamente guardado por el usuario.
                // Solo borramos si el evento fue autollenado desde Plan (autoFilled) y el usuario no lo editó.
                if (eventAutoFilled && !eventTouched) {
                    eventName = ""
                    eventAutoFilled = false
                    eventTouched = false
                }
                if (showNoMatchWarning) {
                    scope.launch { snackbarHostState.showSnackbar("Nodo no encontrado en Plan. Completa los campos manualmente.") }
                }
            }
            return
        }

        // Fill only if user hasn't typed, or value was previously auto-filled.
        if ((!eventTouched || eventAutoFilled) && row.ticketOrEvent.isNotBlank()) {
            eventName = row.ticketOrEvent
            eventAutoFilled = true
        }
    }

    // Live autofill while typing node (no warning spam).
    LaunchedEffect(nodeName, planCache) {
        val row = findPlanRowByNode()
        applyPlanToFields(row, showNoMatchWarning = false)
    }
    
    fun validate(): Boolean {
        // If we have a plan loaded and node doesn't match, show a more specific hint (event will be empty).
        val hasPlan = planCache.rows.isNotEmpty()
        val key = normalizeNode(nodeName)
        if (hasPlan && key.isNotBlank() && findPlanRowByNode() == null && eventName.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Nodo no encontrado en Plan. Completa 'Nombre del Evento'.") }
        }
        val ok = eventName.isNotBlank() && nodeName.isNotBlank() && responsible.isNotBlank() && contractor.isNotBlank() && meterNumber.isNotBlank()
        attemptedSave = true
        if (!ok) {
            scope.launch { snackbarHostState.showSnackbar("Completa todos los campos obligatorios") }
        }
        return ok
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            ReportBottomBar(
                navController = navController,
                reportId = reportId,
                selected = ReportTab.GENERAL,
                generalInfoComplete = report?.isGeneralInfoComplete() == true
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Información General") },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            deleteDraftIfEmptyAndIncomplete()
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            deleteDraftIfEmptyAndIncomplete()
                            navController.navigate(Screen.Home.route) { popUpTo(0) }
                        }
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "Inicio")
                    }
                    IconButton(onClick = { showFinalizeDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Finalizar Reporte")
                    }
                    IconButton(onClick = {
                        // Ensure latest lookup before validating/saving.
                        applyPlanToFields(findPlanRowByNode(), showNoMatchWarning = true)
                        if (validate()) {
                            viewModel.saveGeneralInfo(eventName, nodeName, responsible, contractor, meterNumber)
                            val reportFolder = MaintenanceStorage.reportFolderName(eventName, reportId)
                            MaintenanceStorage.ensureReportDir(context, reportFolder)
                            scope.launch { snackbarHostState.showSnackbar("Guardado") }
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = nodeName,
                onValueChange = { nodeName = it },
                label = { Text("Nombre del Nodo") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            // On leaving the field, do a definitive lookup (can clear + warn).
                            applyPlanToFields(findPlanRowByNode(), showNoMatchWarning = true)
                        }
                    },
                singleLine = true,
                isError = attemptedSave && nodeName.isBlank(),
                supportingText = {
                    if (attemptedSave && nodeName.isBlank()) Text("Obligatorio")
                }
            )

            OutlinedTextField(
                value = eventName,
                onValueChange = {
                    eventName = it
                    eventTouched = it.isNotBlank()
                    if (eventTouched) eventAutoFilled = false
                },
                label = { Text("Nombre del Evento") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = attemptedSave && eventName.isBlank(),
                supportingText = {
                    if (attemptedSave && eventName.isBlank()) Text("Obligatorio")
                }
            )
            
            OutlinedTextField(
                value = responsible,
                onValueChange = {
                    responsible = it
                    responsibleTouched = it.isNotBlank()
                },
                label = { Text("Responsable") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = attemptedSave && responsible.isBlank(),
                supportingText = {
                    if (attemptedSave && responsible.isBlank()) Text("Obligatorio")
                }
            )

            OutlinedTextField(
                value = contractor,
                onValueChange = {
                    contractor = it
                    contractorTouched = it.isNotBlank()
                    if (contractorTouched) contractorAutoFilled = false
                },
                label = { Text("Contratista") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = attemptedSave && contractor.isBlank(),
                supportingText = {
                    if (attemptedSave && contractor.isBlank()) Text("Obligatorio")
                }
            )
            
            OutlinedTextField(
                value = meterNumber,
                onValueChange = {
                    meterNumber = it
                    meterTouched = it.isNotBlank()
                },
                label = { Text("Número de Medidor") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { /* Dropdown */ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                singleLine = true,
                isError = attemptedSave && meterNumber.isBlank(),
                supportingText = {
                    if (attemptedSave && meterNumber.isBlank()) Text("Obligatorio")
                }
            )

            // Totales de Pasivos (solo lectura)
            if (passives.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pasivos", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        val counts = passives.groupingBy { it.type }.eachCount()
                        PassiveType.values().forEach { t ->
                            val c = counts[t] ?: 0
                            if (c > 0) {
                                Text("${t.label}: $c", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("Total: ${passives.size}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    // On continue, re-check plan. If no match, event/contractor may be cleared -> validation will show required error.
                    applyPlanToFields(findPlanRowByNode(), showNoMatchWarning = true)
                    if (validate()) {
                        viewModel.saveGeneralInfo(eventName, nodeName, responsible, contractor, meterNumber)
                        val reportFolder = MaintenanceStorage.reportFolderName(eventName, reportId)
                        MaintenanceStorage.ensureReportDir(context, reportFolder)
                        navController.navigate(Screen.AssetSummary.createRoute(reportId))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Continuar")
            }
        }
    }

    // Si el usuario sale con BACK y no completó la info general, no dejamos un reporte vacío guardado.
    BackHandler {
        scope.launch {
            deleteDraftIfEmptyAndIncomplete()
            navController.popBackStack()
        }
    }

    LaunchedEffect(reportId, report) {
        hasMissingAssets = hasIncompleteAssets(context, reportId, report, repository)
    }

    if (showFinalizeDialog && report != null) {
        FinalizeReportDialog(
            onDismiss = { showFinalizeDialog = false },
            onSendEmailPackage = {
                scope.launch {
                    val bundleFile = exportManager.exportToBundleZip(report!!)
                    EmailManager.sendEmail(context, report!!.eventName, listOf(bundleFile))
                }
            },
            onExportPackage = {
                scope.launch {
                    exportManager.exportBundleToDownloads(report!!)
                    snackbarHostState.showSnackbar("ZIP guardado en Descargas/FieldMaintenance")
                }
            },
            onGoHome = {
                navController.navigate(Screen.Home.route) { popUpTo(0) }
            },
            showMissingWarning = hasMissingAssets
        )
    }
}
