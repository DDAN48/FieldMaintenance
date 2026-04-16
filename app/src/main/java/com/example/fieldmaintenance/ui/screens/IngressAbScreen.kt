@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fieldmaintenance.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldmaintenance.data.model.IngressOrigin
import com.example.fieldmaintenance.ui.components.ReportBottomBar
import com.example.fieldmaintenance.ui.components.ReportTab
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.EmailManager
import com.example.fieldmaintenance.util.ExportManager
import com.example.fieldmaintenance.util.hasIncompleteAssets
import kotlinx.coroutines.launch

@Composable
fun IngressAbScreen(navController: NavController, reportId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DatabaseProvider.init(context)
    val repository = DatabaseProvider.getRepository()

    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    val report by viewModel.report.collectAsState()
    val items by repository.getIngressOriginsByReportId(reportId).collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportManager = remember { ExportManager(context, repository) }

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<IngressOrigin?>(null) }
    var pendingDelete by remember { mutableStateOf<IngressOrigin?>(null) }
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var hasMissingAssets by remember { mutableStateOf(false) }

    fun openNew() {
        editing = null
        showEditor = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { ReportBottomBar(navController = navController, reportId = reportId, selected = ReportTab.INGRESS) },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = 86.dp),
                onClick = { openNew() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar origen ingress")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Ingress Ab") },
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
                items(items, key = { it.id }) { item ->
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
                            if (item.clientId.isNotBlank()) Text("ID cliente: ${item.clientId}", style = MaterialTheme.typography.bodySmall)
                            if (item.buildingId.isNotBlank()) Text("ID edificio: ${item.buildingId}", style = MaterialTheme.typography.bodySmall)
                            if (item.ticketGenerated.isNotBlank()) Text("Ticket: ${item.ticketGenerated}", style = MaterialTheme.typography.bodySmall)
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
            onDismiss = { if (!isExporting) showFinalizeDialog = false },
            onSendEmailHtmlWithImages = {
                scope.launch {
                    if (isExporting) return@launch
                    isExporting = true
                    try {
                        val zipFiles = exportManager.exportToHtmlWithImagesZips(report!!)
                        EmailManager.sendEmail(context, report!!.eventName, zipFiles)
                    } finally {
                        isExporting = false
                        showFinalizeDialog = false
                    }
                }
            },
            onSendEmailForAppJson = {
                scope.launch {
                    if (isExporting) return@launch
                    isExporting = true
                    try {
                        val zipFile = exportManager.exportToZIPForApp(report!!)
                        EmailManager.sendEmail(context, report!!.eventName, listOf(zipFile))
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
        IngressOriginEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { address, clientId, buildingId, ticket, obs ->
                val snapshot = editing
                showEditor = false
                scope.launch {
                    val now = System.currentTimeMillis()
                    val item = snapshot?.copy(
                        address = address,
                        clientId = clientId,
                        buildingId = buildingId,
                        ticketGenerated = ticket,
                        observation = obs,
                        updatedAt = now
                    ) ?: IngressOrigin(
                        reportId = reportId,
                        address = address,
                        clientId = clientId,
                        buildingId = buildingId,
                        ticketGenerated = ticket,
                        observation = obs,
                        createdAt = now,
                        updatedAt = now
                    )
                    if (snapshot == null) repository.insertIngressOrigin(item) else repository.updateIngressOrigin(item)
                }
                scope.launch { snackbarHostState.showSnackbar("Guardado") }
            }
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar ingreso") },
            text = { Text("¿Deseas eliminar este registro?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { repository.deleteIngressOriginById(item.id) }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun IngressOriginEditorDialog(
    initial: IngressOrigin?,
    onDismiss: () -> Unit,
    onSave: (address: String, clientId: String, buildingId: String, ticket: String, observation: String) -> Unit
) {
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var clientId by remember { mutableStateOf(initial?.clientId.orEmpty()) }
    var buildingId by remember { mutableStateOf(initial?.buildingId.orEmpty()) }
    var ticket by remember { mutableStateOf(initial?.ticketGenerated.orEmpty()) }
    var observation by remember { mutableStateOf(initial?.observation.orEmpty()) }

    val clientLocked = buildingId.trim().isNotBlank()
    val buildingLocked = clientId.trim().isNotBlank()
    val hasAnyId = clientId.trim().isNotBlank() || buildingId.trim().isNotBlank()
    val canSave = address.trim().isNotBlank() && hasAnyId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar origen ingress") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = {
                        clientId = it
                        if (it.trim().isNotBlank()) buildingId = ""
                    },
                    label = { Text("ID cliente") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !clientLocked
                )
                OutlinedTextField(
                    value = buildingId,
                    onValueChange = {
                        buildingId = it
                        if (it.trim().isNotBlank()) clientId = ""
                    },
                    label = { Text("ID edificio") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !buildingLocked
                )
                OutlinedTextField(
                    value = ticket,
                    onValueChange = { ticket = it },
                    label = { Text("Ticket generado") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = observation,
                    onValueChange = { observation = it },
                    label = { Text("Observación") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Nota: si completas ID cliente, ID edificio se bloquea (y viceversa).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (!canSave) {
                    Text(
                        "Completa Dirección y al menos uno: ID cliente o ID edificio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val cleanAddress = address.trim()
                    val cleanClient = clientId.trim()
                    val cleanBuilding = buildingId.trim()
                    val cleanTicket = ticket.trim()
                    val cleanObs = observation.trim()
                    onSave(cleanAddress, cleanClient, cleanBuilding, cleanTicket, cleanObs)
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

