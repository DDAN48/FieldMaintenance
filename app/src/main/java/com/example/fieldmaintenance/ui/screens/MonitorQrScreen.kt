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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.rememberAsyncImagePainter
import com.example.fieldmaintenance.data.model.AssetType
import com.example.fieldmaintenance.data.model.PassiveType
import com.example.fieldmaintenance.data.model.Photo
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
    val summaryData by produceState<SummaryData>(initialValue = SummaryData(), report, assets) {
        value = withContext(Dispatchers.IO) {
            val current = report ?: return@withContext SummaryData()
            val reportFolderName = MaintenanceStorage.reportFolderName(current.eventName, reportId)
            val observations = mutableListOf<ObservationEntry>()
            var mapPoint: GeoPoint? = null
            suspend fun addObservationsForAsset(
                asset: com.example.fieldmaintenance.data.model.Asset,
                isModule: Boolean
            ) {
                val targetAsset = if (isModule) asset.copy(type = AssetType.AMPLIFIER) else asset
                val dir = MaintenanceStorage.ensureAssetDir(context, reportFolderName, targetAsset)
                val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
                if (files.isEmpty()) return
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
                if (mapPoint == null && summary.geoLocation != null) {
                    mapPoint = summary.geoLocation
                }
                summary.geoIssueDetails.forEach { detail ->
                    observations.add(
                        ObservationEntry(
                            assetId = asset.id,
                            assetLabel = assetLabelFor(current.nodeName, asset),
                            file = detail.file,
                            detail = detail.detail,
                            isError = true
                        )
                    )
                }
                summary.validationIssueDetails.forEach { detail ->
                    observations.add(
                        ObservationEntry(
                            assetId = asset.id,
                            assetLabel = assetLabelFor(current.nodeName, asset),
                            file = detail.file,
                            detail = detail.detail,
                            isError = detail.isRuleViolation || detail.detail.contains("fuera de rango", ignoreCase = true)
                        )
                    )
                }
            }
            assets.forEach { asset ->
                addObservationsForAsset(asset, isModule = false)
                if (asset.type == AssetType.NODE) {
                    addObservationsForAsset(asset, isModule = true)
                }
            }
            SummaryData(
                mapPoint = mapPoint,
                observations = observations
            )
        }
    }
    val mapBitmap by produceState<Bitmap?>(initialValue = null, summaryData.mapPoint) {
        value = summaryData.mapPoint?.let { loadStaticMap(it.latitude, it.longitude) }
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
                ObservationsSummaryCard(summaryData = summaryData)
                SummaryMapCard(mapBitmap = mapBitmap, mapPoint = summaryData.mapPoint)
                AssetPhotoViewer(
                    assets = assets,
                    report = report,
                    repository = repository
                )
                AssetObservationsList(summaryData = summaryData)
                PassivesDetailCard(passives = passives)
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
private fun ObservationsSummaryCard(summaryData: SummaryData) {
    var expanded by remember { mutableStateOf(false) }
    val total = summaryData.observations.size
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Observaciones en Mediciones:", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(total.toString(), fontWeight = FontWeight.Bold)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                ObservationList(observations = summaryData.observations)
            }
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
private fun AssetPhotoViewer(
    assets: List<com.example.fieldmaintenance.data.model.Asset>,
    report: com.example.fieldmaintenance.data.model.MaintenanceReport?,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedId by remember(assets) { mutableStateOf(assets.firstOrNull()?.id ?: "") }
    val selectedAsset = assets.firstOrNull { it.id == selectedId }
    val photos by repository.getPhotosByAssetId(selectedId).collectAsState(initial = emptyList())
    var index by remember(selectedId, photos) { mutableStateOf(0) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fotos por activo", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded && assets.isNotEmpty()) {
                var menuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = !menuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAsset?.let { assetLabelFor(report?.nodeName ?: "", it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Activo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        assets.forEach { asset ->
                            DropdownMenuItem(
                                text = { Text(assetLabelFor(report?.nodeName ?: "", asset)) },
                                onClick = {
                                    selectedId = asset.id
                                    index = 0
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (photos.isEmpty()) {
                    Text("Sin fotos cargadas.", style = MaterialTheme.typography.bodySmall)
                } else {
                    val photo = photos.getOrNull(index.coerceIn(0, photos.lastIndex))
                    if (photo != null) {
                        PhotoViewer(
                            photo = photo,
                            index = index,
                            total = photos.size,
                            onPrev = { index = (index - 1 + photos.size) % photos.size },
                            onNext = { index = (index + 1) % photos.size },
                            scale = scale,
                            onScaleChange = { scale = it },
                            offsetX = offsetX,
                            offsetY = offsetY,
                            onOffsetChange = { x, y -> offsetX = x; offsetY = y }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoViewer(
    photo: Photo,
    index: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offsetX: Float,
    offsetY: Float,
    onOffsetChange: (Float, Float) -> Unit
) {
    val painter = rememberAsyncImagePainter(File(photo.filePath))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clipToBounds()
            .shadow(1.dp)
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior")
        }
        Text(
            text = "${photo.photoType.name} ${index + 1}/$total",
            style = MaterialTheme.typography.bodySmall
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Siguiente")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onScaleChange((scale + 0.25f).coerceAtMost(3f)) }) {
            Icon(Icons.Default.Add, contentDescription = "Zoom in")
        }
        IconButton(onClick = { onScaleChange((scale - 0.25f).coerceAtLeast(1f)) }) {
            Icon(Icons.Default.Remove, contentDescription = "Zoom out")
        }
        Text(
            text = "Reset",
            modifier = Modifier
                .clickable {
                    onScaleChange(1f)
                    onOffsetChange(0f, 0f)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AssetObservationsList(summaryData: SummaryData) {
    val grouped = summaryData.observations.groupBy { it.assetLabel }
    if (grouped.isEmpty()) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Observaciones en Mediciones", style = MaterialTheme.typography.titleSmall)
                Text("Sin observaciones.", style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    grouped.forEach { (assetLabel, entries) ->
        var expanded by remember { mutableStateOf(false) }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Observaciones en Mediciones - $assetLabel", style = MaterialTheme.typography.titleSmall)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                if (expanded) {
                    ObservationList(observations = entries)
                }
            }
        }
    }
}

@Composable
private fun PassivesDetailCard(passives: List<com.example.fieldmaintenance.data.model.PassiveItem>) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detalle de intervención de pasivos", style = MaterialTheme.typography.titleSmall)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            if (expanded) {
                if (passives.isEmpty()) {
                    Text("Sin pasivos cargados.", style = MaterialTheme.typography.bodySmall)
                } else {
                    passives.forEach { passive ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(passive.type.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(passive.address.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                            if (passive.observation.isNotBlank()) {
                                Text(passive.observation, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObservationList(observations: List<ObservationEntry>) {
    val grouped = observations.groupBy { it.file.ifBlank { "General" } }
    grouped.forEach { (file, details) ->
        Text(file, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        details.forEach { entry ->
            Text(
                text = "• ${entry.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

private fun assetLabelFor(nodeName: String, asset: com.example.fieldmaintenance.data.model.Asset): String {
    return when (asset.type) {
        AssetType.NODE -> "Activo ${nodeName.ifBlank { "Nodo" }} ${asset.frequencyMHz} MHz"
        AssetType.AMPLIFIER -> {
            val code = if (asset.port != null && asset.portIndex != null) {
                "${asset.port.name}${String.format("%02d", asset.portIndex)}"
            } else {
                "SIN-COD"
            }
            "Activo ${nodeName.ifBlank { "Nodo" }} $code ${asset.frequencyMHz} MHz"
        }
        if (ok) count++
    }
}

private data class ObservationEntry(
    val assetId: String,
    val assetLabel: String,
    val file: String,
    val detail: String,
    val isError: Boolean
)

private data class SummaryData(
    val mapPoint: GeoPoint? = null,
    val observations: List<ObservationEntry> = emptyList()
)

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
