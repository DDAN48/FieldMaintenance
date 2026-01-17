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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
import com.example.fieldmaintenance.util.MaintenanceStorage
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.abs

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
    val missingFlags = remember { mutableStateMapOf<String, Boolean>() }
    val hasMissingAssets = missingFlags.values.any { it }
    
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
                    val photos by repository.getPhotosByAssetId(asset.id).collectAsState(initial = emptyList())
                    val nodeAdjustment by repository.getNodeAdjustment(asset.id).collectAsState(initial = null)
                    val amplifierAdjustment by repository.getAmplifierAdjustment(asset.id).collectAsState(initial = null)
                    val reportFolder = MaintenanceStorage.reportFolderName(report?.eventName, reportId)
                    val measurementCount = remember(asset, reportFolder) {
                        MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
                            .listFiles()
                            ?.count { it.isFile } ?: 0
                    }
                    val hasMissingData = remember(
                        asset,
                        photos,
                        nodeAdjustment,
                        amplifierAdjustment,
                        measurementCount
                    ) {
                        val techNormalized = asset.technology?.trim()?.lowercase() ?: ""
                        val moduleCount = photos.count { it.photoType == PhotoType.MODULE }
                        val opticsCount = photos.count { it.photoType == PhotoType.OPTICS }
                        val moduleOk = if (asset.type == AssetType.NODE && techNormalized == "rphy") true else moduleCount == 2
                        val opticsOk = if (asset.type == AssetType.NODE && (techNormalized == "rphy" || techNormalized == "vccap")) true
                        else asset.type != AssetType.NODE || (opticsCount in 1..2)

                        val nodeAdjOk = if (asset.type != AssetType.NODE) true else {
                            val adj = nodeAdjustment
                                ?: com.example.fieldmaintenance.data.model.NodeAdjustment(assetId = asset.id, reportId = reportId)
                            when {
                                techNormalized == "rphy" -> {
                                    adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
                                }
                                techNormalized == "vccap" -> {
                                    adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed &&
                                        adj.spectrumConfirmed && adj.docsisConfirmed
                                }
                                techNormalized == "legacy" -> {
                                    val txOk = adj.tx1310Confirmed || adj.tx1550Confirmed
                                    val poOk = adj.poConfirmed
                                    val rxOk = !adj.rxPadSelection.isNullOrBlank()
                                    val measOk = adj.measurementConfirmed
                                    val specOk = adj.spectrumConfirmed
                                    txOk && poOk && rxOk && measOk && specOk
                                }
                                else -> adj.nonLegacyConfirmed
                            }
                        }

                        val ampAdjOk = if (asset.type != AssetType.AMPLIFIER) true else {
                            val adj = amplifierAdjustment
                            val entradaValid = adj?.let {
                                val ch50Med = it.inputCh50Dbmv
                                val ch50Plan = it.inputPlanCh50Dbmv
                                val highMed = it.inputCh116Dbmv
                                val highPlan = it.inputPlanHighDbmv
                                val ch50Ok = ch50Med != null &&
                                    ch50Plan != null &&
                                    ch50Med >= 15.0 &&
                                    abs(ch50Med - ch50Plan) < 4.0
                                val highOk = highMed != null &&
                                    highPlan != null &&
                                    highMed >= 15.0 &&
                                    abs(highMed - highPlan) < 4.0
                                ch50Ok && highOk
                            } ?: false
                            adj != null &&
                                adj.inputCh50Dbmv != null &&
                                adj.inputCh116Dbmv != null &&
                                (adj.inputHighFreqMHz == 750 || adj.inputHighFreqMHz == 870) &&
                                adj.inputPlanCh50Dbmv != null &&
                                adj.inputPlanHighDbmv != null &&
                                entradaValid &&
                                adj.planLowDbmv != null &&
                                adj.planHighDbmv != null &&
                                adj.outCh50Dbmv != null &&
                                adj.outCh70Dbmv != null &&
                                adj.outCh110Dbmv != null &&
                                adj.outCh116Dbmv != null &&
                                adj.outCh136Dbmv != null
                        }

                        val measurementsOk = measurementCount > 0
                        !(moduleOk && opticsOk && nodeAdjOk && ampAdjOk && measurementsOk)
                    }

                    LaunchedEffect(hasMissingData) {
                        missingFlags[asset.id] = hasMissingData
                    }

                    AssetSummaryCard(
                        asset = asset,
                        nodeName = report?.nodeName.orEmpty(),
                        hasMissingData = hasMissingData,
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
    hasMissingData: Boolean,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (hasMissingData) {
            CardDefaults.cardColors(containerColor = Color(0xA6CD9D10))
        } else {
            CardDefaults.cardColors()
        }
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
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hasMissingData) Color.White else MaterialTheme.colorScheme.onSurface
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
                    color = if (hasMissingData) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasMissingData) {
                    Text(
                        text = "Pendiente",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEB3C38),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Icon(
                    if (hasMissingData) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (hasMissingData) Color.White else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun FinalizeReportDialog(
    onDismiss: () -> Unit,
    onSendEmailPackage: () -> Unit,
    onExportPackage: () -> Unit,
    onGoHome: () -> Unit,
    showMissingWarning: Boolean
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finalizar Reporte") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        if (!showMissingWarning) {
                            onExportPackage()
                            onDismiss()
                        }
                    },
                    enabled = !showMissingWarning
                ) {
                    Text("📦 Exportar reporte (ZIP)")
                }
                TextButton(
                    onClick = {
                        if (!showMissingWarning) {
                            onSendEmailPackage()
                            onDismiss()
                        }
                    },
                    enabled = !showMissingWarning
                ) {
                    Text("✉️ Enviar reporte (ZIP)")
                }
                TextButton(onClick = {
                    onGoHome()
                    onDismiss()
                }) {
                    Text("🏠 Volver al inicio")
                }

                Spacer(modifier = Modifier.height(6.dp))
                if (showMissingWarning) {
                    Text(
                        "Debe completar datos de activos o borrarlos para poder exportar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDE3C2A)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

}
