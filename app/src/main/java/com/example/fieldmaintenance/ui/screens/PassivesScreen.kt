@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fieldmaintenance.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldmaintenance.data.model.PassiveItem
import com.example.fieldmaintenance.data.model.PassiveType
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.components.ReportBottomBar
import com.example.fieldmaintenance.ui.components.ReportTab
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.EmailManager
import com.example.fieldmaintenance.util.ExportManager
import com.example.fieldmaintenance.util.SettingsStore
import com.example.fieldmaintenance.util.AppSettings
import com.example.fieldmaintenance.util.hasIncompleteAssets
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassivesScreen(navController: NavController, reportId: String) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    val repository = DatabaseProvider.getRepository()

    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    
    val passives by repository.getPassivesByReportId(reportId).collectAsState(initial = emptyList())
    val report by viewModel.report.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportManager = remember { ExportManager(context, repository) }
    var hasMissingAssets by remember { mutableStateOf(false) }

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PassiveItem?>(null) }
    var pendingDelete by remember { mutableStateOf<PassiveItem?>(null) }
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    fun openNew() {
        editing = null
        showEditor = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { ReportBottomBar(navController = navController, reportId = reportId, selected = ReportTab.PASIVOS) },
        floatingActionButton = {
            FloatingActionButton(
                // keep above bottom navigation bar
                modifier = Modifier.padding(bottom = 86.dp),
                onClick = { openNew() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Pasivo")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Pasivos") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Home.route) { popUpTo(0) } }) {
                        Icon(Icons.Default.Home, contentDescription = "Inicio")
                    }
                    IconButton(onClick = { showFinalizeDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Finalizar Reporte")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(passives, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    editing = item
                                    showEditor = true
                                },
                                onLongClick = { pendingDelete = item }
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.address, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Tipo: ${item.type.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            if (item.observation.isNotBlank()) {
                                Text(
                                    item.observation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showFinalizeDialog && report != null) {
        FinalizeReportDialog(
            onDismiss = {
                if (!isExporting) {
                    showFinalizeDialog = false
                }
            },
            onSendEmailHtml = {
                scope.launch {
                    if (isExporting) return@launch
                    isExporting = true
                    try {
                        val htmlFile = exportManager.exportToHtmlOnly(report!!)
                        EmailManager.sendEmail(context, report!!.eventName, listOf(htmlFile))
                    } finally {
                        isExporting = false
                        showFinalizeDialog = false
                    }
                }
            },
            onExportHtml = {
                scope.launch {
                    if (isExporting) return@launch
                    isExporting = true
                    try {
                        exportManager.exportHtmlOnlyToDownloads(report!!)
                        snackbarHostState.showSnackbar("HTML guardado en Descargas/FieldMaintenance")
                    } finally {
                        isExporting = false
                        showFinalizeDialog = false
                    }
                }
            },
            onExportForAppJson = {
                scope.launch {
                    if (isExporting) return@launch
                    isExporting = true
                    try {
                        exportManager.exportAppZipToDownloads(report!!)
                        snackbarHostState.showSnackbar("Exportación APP (JSON + carpetas) guardada en Descargas/FieldMaintenance")
                    } finally {
                        isExporting = false
                        showFinalizeDialog = false
                    }
                }
            },
            showMissingWarning = hasMissingAssets,
            isProcessing = isExporting
        )
    }

    LaunchedEffect(reportId, report) {
        hasMissingAssets = hasIncompleteAssets(context, reportId, report, repository)
    }

    if (showEditor) {
        PassiveEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { address, type, obs ->
                val editingSnapshot = editing
                showEditor = false
                scope.launch {
                    val now = System.currentTimeMillis()
                    val item = (editingSnapshot?.copy(
                        address = address,
                        type = type,
                        observation = obs,
                        updatedAt = now
                    ) ?: PassiveItem(
                        reportId = reportId,
                        address = address,
                        type = type,
                        observation = obs,
                        createdAt = now,
                        updatedAt = now
                    ))
                    if (editingSnapshot == null) repository.insertPassive(item) else repository.updatePassive(item)
                }
                scope.launch { snackbarHostState.showSnackbar("Guardado") }
            }
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar pasivo") },
            text = { Text("¿Deseas eliminar este registro?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deletePassiveById(item.id)
                        snackbarHostState.showSnackbar("Eliminado")
                        pendingDelete = null
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PassiveEditorDialog(
    initial: PassiveItem?,
    onDismiss: () -> Unit,
    onSave: (String, PassiveType, String) -> Unit
) {
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: PassiveType.TAPS_SPLITEADO_A_REFORMA) }
    var observation by remember { mutableStateOf(initial?.observation.orEmpty()) }
    var attempted by remember { mutableStateOf(false) }

    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Agregar Pasivo" else "Editar Pasivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = attempted && address.isBlank()
                )

                // Tipo dropdown (simple)
                Column {
                    OutlinedTextField(
                        value = type.label,
                        onValueChange = {},
                        label = { Text("Tipo") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            TextButton(onClick = { typeExpanded = true }) { Text("▼") }
                        }
                    )
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        PassiveType.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = { type = t; typeExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = observation,
                    onValueChange = { observation = it },
                    label = { Text("Observación") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                if (address.isNotBlank()) onSave(address.trim(), type, observation.trim())
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
