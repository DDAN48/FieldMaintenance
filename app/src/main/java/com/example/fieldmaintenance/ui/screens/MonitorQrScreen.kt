@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.fieldmaintenance.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.fieldmaintenance.data.model.AssetType
import com.example.fieldmaintenance.data.model.PassiveType
import com.example.fieldmaintenance.ui.components.ReportBottomBar
import com.example.fieldmaintenance.ui.components.ReportTab
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.EmailManager
import com.example.fieldmaintenance.util.ExportManager
import com.example.fieldmaintenance.util.GeoPoint
import com.example.fieldmaintenance.util.hasIncompleteAssets
import com.example.fieldmaintenance.util.loadDiscardedLabels
import com.example.fieldmaintenance.util.requiredCounts
import com.example.fieldmaintenance.util.verifyMeasurementFiles
import com.example.fieldmaintenance.util.MaintenanceStorage
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@Composable
fun MonitorQrScreen(navController: androidx.navigation.NavController, reportId: String) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    val repository = DatabaseProvider.getRepository()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportManager = remember { ExportManager(context, repository) }

    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    val report by viewModel.report.collectAsState()
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var hasMissingAssets by remember { mutableStateOf(false) }

    val assets by viewModel.assets.collectAsState()
    val passives by repository.getPassivesByReportId(reportId).collectAsState(initial = emptyList())
    val passiveCounts = remember(passives) { passives.groupingBy { it.type }.eachCount() }
    val mapPoint by produceState<GeoPoint?>(initialValue = null, report, assets) {
        value = withContext(Dispatchers.IO) {
            val current = report ?: return@withContext null
            val reportFolderName = MaintenanceStorage.reportFolderName(current.eventName, reportId)
            fun findGeoForAsset(asset: com.example.fieldmaintenance.data.model.Asset, isModule: Boolean): GeoPoint? {
                val targetAsset = if (isModule) asset.copy(type = AssetType.AMPLIFIER) else asset
                val dir = MaintenanceStorage.ensureAssetDir(context, reportFolderName, targetAsset)
                val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
                if (files.isEmpty()) return null
                val discarded = loadDiscardedLabels(File(dir, ".discarded_measurements.txt"))
                val required = requiredCounts(targetAsset.type, isModule = isModule)
                val summary = verifyMeasurementFiles(
                    context = context,
                    files = files,
                    asset = targetAsset,
                    repository = repository,
                    discardedLabels = discarded,
                    expectedDocsisOverride = required.expectedDocsis,
                    expectedChannelOverride = required.expectedChannel
                )
                return summary.geoLocation
            }
            assets.forEach { asset ->
                findGeoForAsset(asset, isModule = false)?.let { return@withContext it }
                if (asset.type == AssetType.NODE) {
                    findGeoForAsset(asset, isModule = true)?.let { return@withContext it }
                }
            }
            null
        }
    }
    val mapBitmap by produceState<Bitmap?>(initialValue = null, mapPoint) {
        value = mapPoint?.let { loadStaticMap(it.latitude, it.longitude) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { ReportBottomBar(navController = navController, reportId = reportId, selected = ReportTab.MONITOR) },
        topBar = {
            TopAppBar(
                title = { Text("Resumen") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SummaryInfoCard(report = report)
            SummaryStatsCard(passiveCounts = passiveCounts, assets = assets, repository = repository)
            SummaryMapCard(mapBitmap = mapBitmap, mapPoint = mapPoint)
        }
    }

    if (showFinalizeDialog && report != null) {
        FinalizeReportDialog(
            onDismiss = {
                if (!isExporting) {
                    showFinalizeDialog = false
                }
            },
            onSendEmailPackage = {
                scope.launch {
                    if (isExporting) return@launch
                    isExporting = true
                    try {
                        val bundleFile = exportManager.exportToBundleZip(report!!)
                        EmailManager.sendEmail(context, report!!.eventName, listOf(bundleFile))
                    } finally {
                        isExporting = false
                        showFinalizeDialog = false
                    }
                }
            },
            onExportPackage = {
                scope.launch {
                    if (isExporting) return@launch
                    isExporting = true
                    try {
                        exportManager.exportBundleToDownloads(report!!)
                        snackbarHostState.showSnackbar("ZIP guardado en Descargas/FieldMaintenance")
                    } finally {
                        isExporting = false
                        showFinalizeDialog = false
                    }
                }
            },
            onGoHome = {
                navController.navigate(Screen.Home.route) { popUpTo(0) }
            },
            showMissingWarning = hasMissingAssets,
            isProcessing = isExporting
        )
    }

    LaunchedEffect(reportId, report) {
        hasMissingAssets = hasIncompleteAssets(context, reportId, report, repository)
    }

}

@Composable
private fun SummaryInfoCard(report: com.example.fieldmaintenance.data.model.MaintenanceReport?) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Información General", style = MaterialTheme.typography.titleSmall)
            SummaryRow(label = "Nodo", value = report?.nodeName)
            SummaryRow(label = "Evento", value = report?.eventName)
            SummaryRow(label = "Responsable", value = report?.responsible)
            SummaryRow(label = "Contratista", value = report?.contractor)
            SummaryRow(label = "Número del medidor", value = report?.meterNumber)
        }
    }
}

@Composable
private fun SummaryStatsCard(
    passiveCounts: Map<PassiveType, Int>,
    assets: List<com.example.fieldmaintenance.data.model.Asset>,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository
) {
    var adjustedCount by remember { mutableStateOf(0) }
    LaunchedEffect(assets) {
        adjustedCount = countAdjustedAssets(assets, repository)
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resumen de Pasivos", style = MaterialTheme.typography.titleSmall)
            SummaryRow(label = "Taps Spliteado a reforma", value = passiveCounts[PassiveType.TAPS_SPLITEADO_A_REFORMA]?.toString())
            SummaryRow(label = "Acometidas cortadas", value = passiveCounts[PassiveType.ACOMETIDAS_CORTADAS]?.toString())
            SummaryRow(label = "Antirrobo Colocado", value = passiveCounts[PassiveType.COLOCA_ANTIRROBO_A_TAP]?.toString())
            SummaryRow(label = "Pasivos normalizado o reparado", value = passiveCounts[PassiveType.PASIVO_NORMALIZADO_O_REPARADO]?.toString())
            SummaryRow(label = "Activos ajustados", value = adjustedCount.toString())
        }
    }
}

@Composable
private fun SummaryMapCard(mapBitmap: Bitmap?, mapPoint: GeoPoint?) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ubicación", style = MaterialTheme.typography.titleSmall)
            if (mapBitmap != null) {
                Image(
                    bitmap = mapBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else {
                Text("Sin coordenadas disponibles.", style = MaterialTheme.typography.bodySmall)
            }
            if (mapPoint != null) {
                Text(
                    "${String.format("%.5f", mapPoint.latitude)}, ${String.format("%.5f", mapPoint.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value?.ifBlank { "—" } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

private suspend fun countAdjustedAssets(
    assets: List<com.example.fieldmaintenance.data.model.Asset>,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository
): Int {
    fun moduleOk(photos: List<com.example.fieldmaintenance.data.model.Photo>): Boolean =
        photos.count { it.photoType == com.example.fieldmaintenance.data.model.PhotoType.MODULE } == 2
    fun opticsOk(photos: List<com.example.fieldmaintenance.data.model.Photo>): Boolean {
        val c = photos.count { it.photoType == com.example.fieldmaintenance.data.model.PhotoType.OPTICS }
        return c in 1..2
    }
    var count = 0
    for (asset in assets) {
        val photos = repository.listPhotosByAssetId(asset.id)
        val baseOk = asset.frequencyMHz > 0 && moduleOk(photos)
        if (!baseOk) continue
        val ok = when (asset.type) {
            AssetType.NODE -> opticsOk(photos)
            AssetType.AMPLIFIER -> {
                val fieldsOk = asset.amplifierMode != null && asset.port != null && asset.portIndex != null
                if (!fieldsOk) false
                else {
                    val adj = repository.getAmplifierAdjustmentOne(asset.id)
                    adj != null &&
                        adj.inputCh50Dbmv != null &&
                        adj.inputCh116Dbmv != null &&
                        (adj.inputHighFreqMHz == null || adj.inputHighFreqMHz == 750 || adj.inputHighFreqMHz == 870) &&
                        adj.planLowDbmv != null &&
                        adj.planHighDbmv != null &&
                        adj.outCh50Dbmv != null &&
                        adj.outCh70Dbmv != null &&
                        adj.outCh110Dbmv != null &&
                        adj.outCh116Dbmv != null &&
                        adj.outCh136Dbmv != null
                }
            }
        }
        if (ok) count++
    }
    return count
}

private suspend fun loadStaticMap(latitude: Double, longitude: Double): Bitmap? {
    return withContext(Dispatchers.IO) {
        val url = "https://staticmap.openstreetmap.de/staticmap.php?center=$latitude,$longitude&zoom=15&size=300x200&markers=$latitude,$longitude,red-pushpin"
        runCatching {
            URL(url).openStream().use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
}
