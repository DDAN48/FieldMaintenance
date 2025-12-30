@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fieldmaintenance.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldmaintenance.data.model.*
import com.example.fieldmaintenance.data.model.label
import com.example.fieldmaintenance.ui.components.ReportBottomBar
import com.example.fieldmaintenance.ui.components.ReportTab
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.EmailManager
import com.example.fieldmaintenance.util.ExportManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSummaryScreen(navController: NavController, reportId: String) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    
    val repository = DatabaseProvider.getRepository()
    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    
    val assets by viewModel.assets.collectAsState()
    val report by viewModel.report.collectAsState()
    var showFinalizeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportManager = remember { ExportManager(context, repository) }
    var assetToDelete by remember { mutableStateOf<Asset?>(null) }
    val hasNode = assets.any { it.type == AssetType.NODE }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { ReportBottomBar(navController = navController, reportId = reportId, selected = ReportTab.ACTIVOS) },
        topBar = {
            TopAppBar(
                title = { Text("Resumen de Activos") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                    }) {
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(assets) { asset ->
                    AssetSummaryCard(
                        asset = asset,
                        nodeName = report?.nodeName.orEmpty(),
                        onClick = {
                            navController.navigate(Screen.AddAsset.createRoute(reportId, asset.id))
                        },
                        onLongClick = { assetToDelete = asset }
                    )
                }
            }
            
            if (!hasNode) {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.AddAsset.createRoute(reportId)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agregar Nodo")
                }
            }

            OutlinedButton(
                onClick = { navController.navigate(Screen.AddAsset.createRoute(reportId)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Añadir Amplificador")
            }
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
    
    assetToDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { assetToDelete = null },
            title = { Text("Borrar activo") },
            text = { Text("¿Estás seguro de borrar este activo?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAsset(asset)
                    assetToDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { assetToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun AssetSummaryCard(
    asset: Asset,
    nodeName: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (asset.type) {
                        AssetType.NODE -> nodeName.ifBlank { "Nodo" }
                        AssetType.AMPLIFIER -> {
                            val code = if (asset.port != null && asset.portIndex != null) {
                                "${asset.port.name}${String.format("%02d", asset.portIndex)}"
                            } else {
                                "SIN-COD"
                            }
                            val prefix = nodeName.ifBlank { "Nodo" }
                            "$prefix $code"
                        }
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = buildString {
                        append("Frecuencia: ${asset.frequencyMHz} MHz")
                        if (asset.type == AssetType.AMPLIFIER) {
                            val type = asset.amplifierMode?.label ?: "N/A"
                            append(" - Tipo: $type")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun FinalizeReportDialog(
    onDismiss: () -> Unit,
    onSendEmail: () -> Unit,
    onExportPDF: () -> Unit,
    onExportJSON: () -> Unit,
    onGoHome: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finalizar Reporte") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = {
                    onSendEmail()
                    onDismiss()
                }) {
                    Text("📧 Enviar por correo")
                }
                TextButton(onClick = {
                    onExportPDF()
                    onDismiss()
                }) {
                    Text("📄 Exportar PDF")
                }
                TextButton(onClick = {
                    onExportJSON()
                    onDismiss()
                }) {
                    Text("📦 Exportar editable (JSON)")
                }
                TextButton(onClick = {
                    onGoHome()
                    onDismiss()
                }) {
                    Text("🏠 Volver al inicio")
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Recuerde sincronizar mediciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

