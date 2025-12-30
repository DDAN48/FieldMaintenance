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
    var showFinalizeDialog by remember { mutableStateOf(false) }
    
    var eventName by remember { mutableStateOf(report?.eventName ?: "") }
    var nodeName by remember { mutableStateOf(report?.nodeName ?: "") }
    var responsible by remember { mutableStateOf(report?.responsible ?: "") }
    var contractor by remember { mutableStateOf(report?.contractor ?: "") }
    var meterNumber by remember { mutableStateOf(report?.meterNumber ?: "") }
    var attemptedSave by remember { mutableStateOf(false) }
    
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

    LaunchedEffect(report) {
        report?.let {
            eventName = it.eventName
            nodeName = it.nodeName
            responsible = it.responsible
            contractor = it.contractor
            meterNumber = it.meterNumber
        }
    }
    
    fun validate(): Boolean {
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
                        if (validate()) {
                            viewModel.saveGeneralInfo(eventName, nodeName, responsible, contractor, meterNumber)
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
                value = eventName,
                onValueChange = { eventName = it },
                label = { Text("Nombre del Evento") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = attemptedSave && eventName.isBlank(),
                supportingText = {
                    if (attemptedSave && eventName.isBlank()) Text("Obligatorio")
                }
            )
            
            OutlinedTextField(
                value = nodeName,
                onValueChange = { nodeName = it },
                label = { Text("Nombre del Nodo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = attemptedSave && nodeName.isBlank(),
                supportingText = {
                    if (attemptedSave && nodeName.isBlank()) Text("Obligatorio")
                }
            )
            
            OutlinedTextField(
                value = responsible,
                onValueChange = { responsible = it },
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
                onValueChange = { contractor = it },
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
                onValueChange = { meterNumber = it },
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
                    if (validate()) {
                        viewModel.saveGeneralInfo(eventName, nodeName, responsible, contractor, meterNumber)
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

    if (showFinalizeDialog && report != null) {
        FinalizeReportDialog(
            onDismiss = { showFinalizeDialog = false },
            onSendEmail = {
                scope.launch {
                    val pdfFile = exportManager.exportToPDF(report!!)
                    EmailManager.sendEmail(context, report!!.eventName, listOf(pdfFile))
                }
            },
            onExportPDF = {
                scope.launch {
                    exportManager.exportPdfToDownloads(report!!)
                    snackbarHostState.showSnackbar("PDF guardado en Descargas/FieldMaintenance")
                }
            },
            onExportJSON = {
                scope.launch {
                    exportManager.exportZipToDownloads(report!!)
                    snackbarHostState.showSnackbar("ZIP guardado en Descargas/FieldMaintenance")
                }
            },
            onGoHome = {
                navController.navigate(Screen.Home.route) { popUpTo(0) }
            }
        )
    }
}

