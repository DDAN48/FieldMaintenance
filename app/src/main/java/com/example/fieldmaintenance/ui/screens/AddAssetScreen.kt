@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.example.fieldmaintenance.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.widget.Toast
import android.location.Geocoder
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.fieldmaintenance.data.model.*
import com.example.fieldmaintenance.data.model.label
import com.example.fieldmaintenance.ui.components.AmplifierAdjustmentCard
import com.example.fieldmaintenance.ui.components.NodeAdjustmentCard
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementAssetIdKey
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementReportIdKey
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementAssetTypeKey
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.ImageStore
import com.example.fieldmaintenance.util.PhotoManager
import com.example.fieldmaintenance.util.SettingsStore
import com.example.fieldmaintenance.util.AppSettings
import com.example.fieldmaintenance.util.MaintenanceStorage
import com.example.fieldmaintenance.util.PlanCache
import com.example.fieldmaintenance.util.PlanRepository
import com.example.fieldmaintenance.util.hasIncompleteAssets
import com.example.fieldmaintenance.util.MeasurementEntry
import com.example.fieldmaintenance.util.MeasurementVerificationSummary
import com.example.fieldmaintenance.util.loadDiscardedLabels
import com.example.fieldmaintenance.util.meetsRequired
import com.example.fieldmaintenance.util.requiredCounts
import com.example.fieldmaintenance.util.saveDiscardedLabels
import com.example.fieldmaintenance.util.verifyMeasurementFiles
import java.util.Locale
import java.io.FileOutputStream
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
// Note: some Material3 versions only support menuAnchor() and menuAnchor(type, enabled).
import java.io.File
import java.net.URLConnection
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssetScreen(
    navController: NavController,
    reportId: String,
    assetId: String? = null,
    assetTypeParam: String? = null
) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    
    val repository = DatabaseProvider.getRepository()
    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val workingAssetId = rememberSaveable(assetId) { assetId ?: UUID.randomUUID().toString() }
    val isEdit = assetId != null
    val report by viewModel.report.collectAsState()
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var hasMissingAssets by remember { mutableStateOf(false) }

    // Mensaje emergente al ingresar al módulo de activos (SnackBar, para que no se corte el texto)
    val syncMsgShown = rememberSaveable(workingAssetId) { mutableStateOf(false) }
    LaunchedEffect(workingAssetId) {
        if (!syncMsgShown.value) {
            snackbarHostState.showSnackbar("Recuerde sincronizar las mediciones con el nombre del activo.")
            syncMsgShown.value = true
        }
    }
    
    val parsedAssetType = remember(assetTypeParam) {
        assetTypeParam?.let { type -> runCatching { AssetType.valueOf(type) }.getOrNull() }
    }
    var assetType by remember { mutableStateOf(parsedAssetType ?: AssetType.NODE) }
    val frequencySaver = Saver<Frequency?, String>(
        save = { it?.name ?: "" },
        restore = { if (it.isBlank()) null else Frequency.valueOf(it) }
    )
    val amplifierModeSaver = Saver<AmplifierMode?, String>(
        save = { it?.name ?: "" },
        restore = { if (it.isBlank()) null else AmplifierMode.valueOf(it) }
    )
    val portSaver = Saver<Port?, String>(
        save = { it?.name ?: "" },
        restore = { if (it.isBlank()) null else Port.valueOf(it) }
    )
    var frequency by rememberSaveable(stateSaver = frequencySaver) { mutableStateOf<Frequency?>(null) }
    var technology by rememberSaveable { mutableStateOf<String?>(null) }
    var amplifierMode by rememberSaveable(stateSaver = amplifierModeSaver) { mutableStateOf<AmplifierMode?>(null) }
    var port by rememberSaveable(stateSaver = portSaver) { mutableStateOf<Port?>(null) }
    var portIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var hasNode by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }
    var modulePhotoCount by remember { mutableStateOf(0) }
    var opticsPhotoCount by remember { mutableStateOf(0) }
    var monitoringPhotoCount by remember { mutableStateOf(0) }
    var spectrumPhotoCount by remember { mutableStateOf(0) }
    var autoSaved by rememberSaveable(workingAssetId) { mutableStateOf(false) }
    var showIdentityDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotosDialog by rememberSaveable { mutableStateOf(false) }
    var showMeasurementsDialog by rememberSaveable { mutableStateOf(false) }
    var showNodeAdjustmentDialog by rememberSaveable { mutableStateOf(false) }
    var showAmplifierAdjustmentDialog by rememberSaveable { mutableStateOf(false) }
    var measurementsComplete by remember { mutableStateOf(false) }

    // Amplifier adjustment (persisted per asset)
    val amplifierAdjustment by repository.getAmplifierAdjustment(workingAssetId)
        .collectAsState(initial = null)
    var currentAmplifierAdjustment by remember(workingAssetId) { mutableStateOf<com.example.fieldmaintenance.data.model.AmplifierAdjustment?>(null) }

    // Node adjustment (persisted per asset)
    val nodeAdjustment by repository.getNodeAdjustment(workingAssetId).collectAsState(initial = null)

    val techNormalized = technology?.trim()?.lowercase(Locale.getDefault()) ?: ""
    val ampAdj = currentAmplifierAdjustment ?: amplifierAdjustment
    val autoBaseOk = frequency != null
    val autoNodeOk = assetType != AssetType.NODE || technology != null
    val autoAmplifierOk = assetType != AssetType.AMPLIFIER || (amplifierMode != null && port != null && portIndex != null)
    val autoAmplifierTablesOk = if (assetType != AssetType.AMPLIFIER) true else {
        val okAdj = (frequency != null && amplifierMode != null) &&
            ampAdj != null &&
            ampAdj.inputCh50Dbmv != null &&
            ampAdj.inputCh116Dbmv != null &&
            (ampAdj.inputHighFreqMHz == 750 || ampAdj.inputHighFreqMHz == 870) &&
            ampAdj.planLowDbmv != null &&
            ampAdj.planHighDbmv != null &&
            ampAdj.outCh50Dbmv != null &&
            ampAdj.outCh70Dbmv != null &&
            ampAdj.outCh110Dbmv != null &&
            ampAdj.outCh116Dbmv != null &&
            ampAdj.outCh136Dbmv != null
        okAdj
    }
    val ampEntradaOk = if (assetType != AssetType.AMPLIFIER) true else {
        val adj = ampAdj
        if (adj == null) {
            false
        } else {
            val ch50Med = adj.inputCh50Dbmv
            val ch50Plan = adj.inputPlanCh50Dbmv
            val highMed = adj.inputCh116Dbmv
            val highPlan = adj.inputPlanHighDbmv
            val ch50Ok = ch50Med != null && ch50Plan != null && ch50Med >= 15.0 && kotlin.math.abs(ch50Med - ch50Plan) < 4.0
            val highOk = highMed != null && highPlan != null && highMed >= 15.0 && kotlin.math.abs(highMed - highPlan) < 4.0
            ch50Ok && highOk
        }
    }

    val autoNodeAdjOk = if (assetType != AssetType.NODE) true else {
        val adj = nodeAdjustment
            ?: com.example.fieldmaintenance.data.model.NodeAdjustment(assetId = workingAssetId, reportId = reportId)
        when {
            techNormalized == "rphy" -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
            }
            techNormalized == "vccap" -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed &&
                    adj.spectrumConfirmed && adj.docsisConfirmed && frequency != null
            }
            techNormalized == "legacy" -> {
                val txOk = adj.tx1310Confirmed || adj.tx1550Confirmed
                val poOk = adj.poConfirmed
                val rxOk = !adj.rxPadSelection.isNullOrBlank()
                val measOk = adj.measurementConfirmed
                val specOk = adj.spectrumConfirmed
                txOk && poOk && rxOk && measOk && specOk && frequency != null
            }
            else -> {
                adj.nonLegacyConfirmed
            }
        }
    }

    val nodeAllowed = !(assetType == AssetType.NODE && hasNode && !isEdit)
    val autoSaveReady = autoBaseOk && autoNodeOk && autoAmplifierOk && autoAmplifierTablesOk && autoNodeAdjOk && nodeAllowed
    val identityComplete = autoBaseOk && autoNodeOk && autoAmplifierOk
    val modulePhotoRequired = if (assetType == AssetType.NODE && techNormalized == "rphy") 0 else 2
    val opticsPhotoRequired = if (assetType == AssetType.NODE && (techNormalized == "rphy" || techNormalized == "vccap")) {
        0
    } else if (assetType == AssetType.NODE) {
        1
    } else {
        0
    }
    val modulePhotosOk = modulePhotoRequired == 0 || modulePhotoCount >= modulePhotoRequired
    val opticsPhotosOk = opticsPhotoRequired == 0 || opticsPhotoCount >= opticsPhotoRequired
    val photosComplete = modulePhotosOk && opticsPhotosOk
    val photoSupportText = buildList {
        if (modulePhotoRequired > 0) {
            add("Módulo: $modulePhotoCount/$modulePhotoRequired")
        }
        if (opticsPhotoRequired > 0) {
            add("Ópticas: $opticsPhotoCount/$opticsPhotoRequired")
        }
    }.joinToString(" • ").ifBlank { "" }
    
    LaunchedEffect(Unit) {
        hasNode = viewModel.hasNode()
        if (!isEdit && hasNode) {
            assetType = AssetType.AMPLIFIER
        }
    }

    val planRepo = remember { PlanRepository(context.applicationContext) }
    val planCache by planRepo.cacheFlow().collectAsState(initial = PlanCache())

    fun normalizeNode(v: String): String =
        v.trim().replace(Regex("\\s+"), " ").uppercase(Locale.getDefault())

    val reportNodeName = report?.nodeName.orEmpty()
    val planRowForNode = remember(planCache, reportNodeName) {
        val key = normalizeNode(reportNodeName)
        if (key.isBlank()) null
        else planCache.rows.firstOrNull { normalizeNode(it.nodeCmts) == key }
    }

    LaunchedEffect(assetId) {
        if (assetId != null) {
            val asset = repository.getAssetById(assetId)
            if (asset != null) {
                assetType = asset.type
                frequency = when (asset.frequencyMHz) {
                    42 -> Frequency.MHz_42
                    85 -> Frequency.MHz_85
                    else -> null
                }
                technology = asset.technology
                amplifierMode = asset.amplifierMode
                port = asset.port
                portIndex = asset.portIndex
            }
        }
    }

    LaunchedEffect(reportId, report) {
        hasMissingAssets = hasIncompleteAssets(context, reportId, report, repository)
    }

    // Auto-fill technology from plan if available and not already set
    LaunchedEffect(planRowForNode, technology) {
        if (assetType == AssetType.NODE && technology == null && planRowForNode != null) {
            val planTech = planRowForNode.technology.trim()
            if (planTech.isNotBlank()) {
                val normalized = planTech.lowercase(Locale.getDefault())
                when {
                    normalized == "legacy" -> technology = "Legacy"
                    normalized == "rphy" -> technology = "RPHY"
                    normalized == "vccap" -> technology = "VCCAP"
                }
            }
        }
    }
    
    suspend fun validateAndShowErrors(): Boolean {
        attemptedSave = true

        val baseOk = frequency != null
        val nodeOk = assetType != AssetType.NODE || technology != null
        val amplifierOk = assetType != AssetType.AMPLIFIER || (amplifierMode != null && port != null && portIndex != null)

        val ampAdj = currentAmplifierAdjustment ?: amplifierAdjustment
        val amplifierTablesOk = if (assetType != AssetType.AMPLIFIER) true else {
            val okAdj = (frequency != null && amplifierMode != null) &&
                ampAdj != null &&
                ampAdj.inputCh50Dbmv != null &&
                ampAdj.inputCh116Dbmv != null &&
                (ampAdj.inputHighFreqMHz == 750 || ampAdj.inputHighFreqMHz == 870) &&
                ampAdj.planLowDbmv != null &&
                ampAdj.planHighDbmv != null &&
                ampAdj.outCh50Dbmv != null &&
                ampAdj.outCh70Dbmv != null &&
                ampAdj.outCh110Dbmv != null &&
                ampAdj.outCh116Dbmv != null &&
                ampAdj.outCh136Dbmv != null
            okAdj
        }

        // Photos required
        // We compute counts in PhotoSection via callbacks
        val techNormalized = technology?.trim()?.lowercase(Locale.getDefault()) ?: ""
        val moduleOk = if (assetType == AssetType.NODE && techNormalized == "rphy") true else modulePhotoCount == 2
        val opticsOk = if (assetType == AssetType.NODE && (techNormalized == "rphy" || techNormalized == "vccap")) true 
                      else assetType != AssetType.NODE || (opticsPhotoCount in 1..2)

        val nodeAllowed = !(assetType == AssetType.NODE && hasNode && !isEdit)

        val nodeAdjOk = if (assetType != AssetType.NODE) true else {
            val adj = nodeAdjustment
                ?: com.example.fieldmaintenance.data.model.NodeAdjustment(assetId = workingAssetId, reportId = reportId)
            val tech = technology?.trim()?.lowercase(Locale.getDefault()) 
                ?: planRowForNode?.technology?.trim()?.lowercase(Locale.getDefault()) ?: "legacy"
            when {
                tech == "rphy" -> {
                    adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
                }
                tech == "vccap" -> {
                    adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed && 
                    adj.spectrumConfirmed && adj.docsisConfirmed && frequency != null
                }
                tech == "legacy" -> {
                    val txOk = adj.tx1310Confirmed || adj.tx1550Confirmed
                    val poOk = adj.poConfirmed
                    val rxOk = !adj.rxPadSelection.isNullOrBlank()
                    val measOk = adj.measurementConfirmed
                    val specOk = adj.spectrumConfirmed
                    txOk && poOk && rxOk && measOk && specOk && frequency != null
                }
                else -> {
                    adj.nonLegacyConfirmed
                }
            }
        }

        val ok = baseOk && nodeOk && amplifierOk && amplifierTablesOk && moduleOk && opticsOk && nodeAllowed && nodeAdjOk
        if (!ok) {
            if (assetType == AssetType.AMPLIFIER && !amplifierTablesOk) {
                snackbarHostState.showSnackbar("Completa todas las tablas del ajuste del amplificador")
            } else if (assetType == AssetType.NODE && planRowForNode != null && !nodeAdjOk) {
                snackbarHostState.showSnackbar("Completa el Ajuste de Nodo")
            } else {
                snackbarHostState.showSnackbar("Faltan campos o fotos obligatorias")
            }
        }
        return ok
    }

    LaunchedEffect(
        autoSaveReady,
        assetType,
        frequency,
        technology,
        amplifierMode,
        port,
        portIndex,
        nodeAdjustment,
        currentAmplifierAdjustment,
        amplifierAdjustment
    ) {
        if (!autoSaveReady) return@LaunchedEffect
        val asset = Asset(
            id = workingAssetId,
            reportId = reportId,
            type = assetType,
            frequencyMHz = frequency?.mhz ?: 0,
            amplifierMode = amplifierMode,
            port = port,
            portIndex = portIndex,
            technology = if (assetType == AssetType.NODE) technology else null
        )
        if (isEdit || autoSaved) {
            viewModel.updateAsset(asset)
        } else {
            viewModel.addAsset(asset)
        }
        autoSaved = true
        withContext(Dispatchers.IO) {
            val reportFolder = MaintenanceStorage.reportFolderName(report?.eventName, reportId)
            MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isEdit) "Editar Activo" else "Agregar Activo")
                        val bwText = frequency?.mhz?.let { "$it MHz" } ?: "—"
                        when (assetType) {
                            AssetType.AMPLIFIER -> {
                                val tipoText = amplifierMode?.label ?: "—"
                                val nodeLabel = reportNodeName.ifBlank { "Nodo" }
                                val codeLabel = if (port != null && portIndex != null) {
                                    "${port?.name}${String.format("%02d", portIndex)}"
                                } else {
                                    "SIN-COD"
                                }
                                Text(
                                    "$nodeLabel $codeLabel - $bwText - $tipoText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            AssetType.NODE -> {
                                val techText = technology?.trim().orEmpty().ifBlank { "—" }
                                val nodeLabel = reportNodeName.ifBlank { "Nodo" }
                                Text(
                                    "$nodeLabel - $bwText - $techText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                },
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
                    IconButton(onClick = {
                        scope.launch {
                            if (validateAndShowErrors()) {
                                if (assetType == AssetType.AMPLIFIER) {
                                    val existing = repository.listAssetsByReportId(reportId)
                                    val dup = existing.any {
                                        it.type == AssetType.AMPLIFIER &&
                                            it.id != workingAssetId &&
                                            it.port == port &&
                                            it.portIndex == portIndex
                                    }
                                    if (dup) {
                                        snackbarHostState.showSnackbar("Activo ya existe: ${port?.name}${String.format("%02d", portIndex)}")
                                        return@launch
                                    }
                                }
                                val asset = Asset(
                                    id = workingAssetId,
                                    reportId = reportId,
                                    type = assetType,
                                    frequencyMHz = frequency!!.mhz,
                                    amplifierMode = amplifierMode,
                                    port = port,
                                    portIndex = portIndex
                                )
                                if (isEdit) viewModel.updateAsset(asset) else viewModel.addAsset(asset)
                                withContext(Dispatchers.IO) {
                                    val reportFolder = MaintenanceStorage.reportFolderName(
                                        report?.eventName,
                                        reportId
                                    )
                                    MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
                                }
                                snackbarHostState.showSnackbar("Guardado")
                            } else {
                                snackbarHostState.showSnackbar("Completa los datos del activo")
                            }
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
            AdjustmentSummaryCard(
                title = "Identidad del activo",
                status = if (identityComplete) "Completo" else "Pendiente",
                actionLabel = if (identityComplete) "Editar" else "Completar",
                isComplete = identityComplete,
                onAction = { showIdentityDialog = true }
            )

            if (showIdentityDialog) {
                FullScreenAdjustmentDialog(
                    title = "Identidad del activo",
                    onDismiss = { showIdentityDialog = false },
                    onComplete = { showIdentityDialog = false }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var expandedFreq by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedFreq,
                            onExpandedChange = { expandedFreq = !expandedFreq },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = frequency?.let { "${it.mhz} MHz" } ?: "Seleccionar",
                                onValueChange = {},
                                readOnly = true,
                                enabled = true,
                                label = { Text("Frec módulo") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFreq)
                                },
                                isError = attemptedSave && frequency == null,
                                supportingText = {
                                    if (attemptedSave && frequency == null) Text("Obligatorio")
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedFreq,
                                onDismissRequest = { expandedFreq = false }
                            ) {
                                listOf(Frequency.MHz_42, Frequency.MHz_85).forEach { freq ->
                                    DropdownMenuItem(
                                        text = { Text("${freq.mhz} MHz") },
                                        onClick = {
                                            frequency = freq
                                            expandedFreq = false
                                        }
                                    )
                                }
                            }
                        }

                        if (assetType == AssetType.NODE) {
                            var expandedTech by remember { mutableStateOf(false) }
                            val techOptions = listOf("Legacy", "RPHY", "VCCAP")
                            ExposedDropdownMenuBox(
                                expanded = expandedTech,
                                onExpandedChange = { expandedTech = !expandedTech },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = technology ?: "Seleccionar",
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = true,
                                    label = { Text("Tecnología") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTech)
                                    },
                                    isError = attemptedSave && technology == null,
                                    supportingText = {
                                        if (attemptedSave && technology == null) Text("Obligatorio")
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedTech,
                                    onDismissRequest = { expandedTech = false }
                                ) {
                                    techOptions.forEach { tech ->
                                        DropdownMenuItem(
                                            text = { Text(tech) },
                                            onClick = {
                                                technology = tech
                                                expandedTech = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (assetType == AssetType.AMPLIFIER) {
                            var expandedMode by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expandedMode,
                                onExpandedChange = { expandedMode = !expandedMode },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = when (amplifierMode) {
                                        AmplifierMode.HGD -> AmplifierMode.HGD.label
                                        AmplifierMode.HGDT -> AmplifierMode.HGDT.label
                                        AmplifierMode.LE -> AmplifierMode.LE.label
                                        null -> "Seleccionar"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = true,
                                    label = { Text("Tipo") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode)
                                    },
                                    isError = attemptedSave && amplifierMode == null,
                                    supportingText = {
                                        if (attemptedSave && amplifierMode == null) Text("Obligatorio")
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedMode,
                                    onDismissRequest = { expandedMode = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("HGD") },
                                        onClick = {
                                            amplifierMode = AmplifierMode.HGD
                                            expandedMode = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("HGBT") },
                                        onClick = {
                                            amplifierMode = AmplifierMode.HGDT
                                            expandedMode = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("LE") },
                                        onClick = {
                                            amplifierMode = AmplifierMode.LE
                                            expandedMode = false
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                var expandedPort by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expandedPort,
                                    onExpandedChange = { expandedPort = !expandedPort },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = port?.name ?: "Puerto",
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = true,
                                        label = { Text("Puerto") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPort)
                                        },
                                        isError = attemptedSave && port == null,
                                        supportingText = {
                                            if (attemptedSave && port == null) Text("Obligatorio")
                                        }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedPort,
                                        onDismissRequest = { expandedPort = false }
                                    ) {
                                        Port.values().forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.name) },
                                                onClick = {
                                                    port = p
                                                    expandedPort = false
                                                }
                                            )
                                        }
                                    }
                                }

                                var expandedIndex by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expandedIndex,
                                    onExpandedChange = { expandedIndex = !expandedIndex },
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    val labelValue = portIndex?.let { String.format("%02d", it) } ?: "N°"
                                    OutlinedTextField(
                                        value = labelValue,
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = true,
                                        label = { Text("N°") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIndex)
                                        },
                                        isError = attemptedSave && portIndex == null,
                                        supportingText = {
                                            if (attemptedSave && portIndex == null) Text("Obligatorio")
                                        }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedIndex,
                                        onDismissRequest = { expandedIndex = false }
                                    ) {
                                        (1..4).forEach { idx ->
                                            DropdownMenuItem(
                                                text = { Text(String.format("%02d", idx)) },
                                                onClick = {
                                                    portIndex = idx
                                                    expandedIndex = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Submódulo: Ajuste de Nodo (después de identidad)
            if (assetType == AssetType.NODE) {
                val isNodeComplete = autoNodeAdjOk
                AdjustmentSummaryCard(
                    title = "Ajuste de Nodo",
                    status = if (isNodeComplete) "Completo" else "Pendiente",
                    actionLabel = if (isNodeComplete) "Editar" else "Completar",
                    isComplete = isNodeComplete,
                    onAction = { showNodeAdjustmentDialog = true }
                )
            }
            
            // Submódulo: Ajuste de Amplificador (antes de Fotos)
            if (assetType == AssetType.AMPLIFIER) {
                val isAmpReady = frequency != null && amplifierMode != null
                val isAmpComplete = autoAmplifierTablesOk
                val ampSupport = if (!isAmpReady) {
                    "Selecciona Frecuencia y Tipo (HGBT/HGD/LE) para habilitar el ajuste."
                } else {
                    null
                }
                AdjustmentSummaryCard(
                    title = "Ajuste de Amplificador",
                    status = if (isAmpComplete) "Completo" else "Pendiente",
                    actionLabel = if (isAmpComplete) "Editar" else "Completar",
                    isComplete = isAmpComplete,
                    supportingText = ampSupport,
                    actionEnabled = isAmpReady,
                    onAction = { showAmplifierAdjustmentDialog = true }
                )
            }

            if (showNodeAdjustmentDialog && assetType == AssetType.NODE) {
                FullScreenAdjustmentDialog(
                    title = "Ajuste de Nodo",
                    onDismiss = { showNodeAdjustmentDialog = false },
                    onComplete = { showNodeAdjustmentDialog = false }
                ) {
                    NodeAdjustmentCard(
                        assetId = workingAssetId,
                        reportId = reportId,
                        nodeName = reportNodeName,
                        frequency = frequency,
                        technology = technology,
                        planRow = planRowForNode,
                        adjustment = nodeAdjustment,
                        showRequiredErrors = attemptedSave,
                        onPersist = { adj ->
                            scope.launch { repository.upsertNodeAdjustment(adj) }
                        }
                    )
                }
            }

            if (showAmplifierAdjustmentDialog && assetType == AssetType.AMPLIFIER && frequency != null && amplifierMode != null) {
                FullScreenAdjustmentDialog(
                    title = "Ajuste de Amplificador",
                    onDismiss = { showAmplifierAdjustmentDialog = false },
                    onComplete = { showAmplifierAdjustmentDialog = false }
                ) {
                    AmplifierAdjustmentCard(
                        assetId = workingAssetId,
                        bandwidth = frequency,
                        amplifierMode = amplifierMode,
                        initial = amplifierAdjustment,
                        showRequiredErrors = attemptedSave,
                        onCurrentChange = { currentAmplifierAdjustment = it },
                        onPersist = { adj -> repository.upsertAmplifierAdjustment(adj.copy(assetId = workingAssetId)) }
                    )
                }
            }
            
            AdjustmentSummaryCard(
                title = "Fotos",
                status = if (photosComplete) "Completo" else "Pendiente",
                actionLabel = if (photosComplete) "Editar" else "Completar",
                isComplete = photosComplete,
                supportingText = photoSupportText.takeIf { it.isNotBlank() },
                onAction = { showPhotosDialog = true }
            )

            if (showPhotosDialog) {
                FullScreenAdjustmentDialog(
                    title = "Fotos",
                    onDismiss = { showPhotosDialog = false },
                    onComplete = { showPhotosDialog = false }
                ) {
                    val assetDisplayName = remember(reportNodeName, assetType, port, portIndex) {
                        val baseNodeName = reportNodeName.ifBlank { "Nodo" }
                        if (assetType == AssetType.NODE) {
                            baseNodeName
                        } else {
                            val code = if (port != null && portIndex != null) {
                                "${port?.name}${String.format("%02d", portIndex)}"
                            } else {
                                "SIN-COD"
                            }
                            "$baseNodeName $code".trim()
                        }
                    }
                    val eventName = remember(report?.eventName) {
                        report?.eventName?.trim().orEmpty().ifBlank { "Sin evento" }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Foto del Módulo (no para RPHY)
                        if (assetType != AssetType.NODE || technology != "RPHY") {
                            PhotoSection(
                                title = "Foto del Módulo y Tapa",
                                reportId = reportId,
                                assetId = workingAssetId,
                                photoType = PhotoType.MODULE,
                                assetLabel = assetDisplayName,
                                eventName = eventName,
                                repository = repository,
                                minRequired = if (assetType == AssetType.NODE && technology != "RPHY") 2 else 0,
                                showRequiredError = attemptedSave && (assetType != AssetType.NODE || technology != "RPHY"),
                                maxAllowed = 2,
                                onCountChange = {
                                    modulePhotoCount = it
                                }
                            )
                        }

                        // Fotos adicionales según tipo
                        // Foto TX y RX con pads (no para RPHY ni VCCAP)
                        if (assetType == AssetType.NODE && technology != "RPHY" && technology != "VCCAP") {
                            PhotoSection(
                                title = "Foto TX  y RX con pads",
                                reportId = reportId,
                                assetId = workingAssetId,
                                photoType = PhotoType.OPTICS,
                                assetLabel = assetDisplayName,
                                eventName = eventName,
                                repository = repository,
                                minRequired = 1,
                                showRequiredError = attemptedSave,
                                maxAllowed = 2,
                                onCountChange = {
                                    opticsPhotoCount = it
                                }
                            )
                        }

                        if (assetType == AssetType.NODE) {
                            PhotoSection(
                                title = "Foto de monitoria de PO directa y retorno",
                                reportId = reportId,
                                assetId = workingAssetId,
                                photoType = PhotoType.MONITORING,
                                assetLabel = assetDisplayName,
                                eventName = eventName,
                                repository = repository,
                                minRequired = 0,
                                showRequiredError = false,
                                maxAllowed = 2,
                                onCountChange = {
                                    monitoringPhotoCount = it
                                }
                            )
                        }

                        // Fotos de Inyección de portadoras (no para RPHY)
                        if (assetType != AssetType.NODE || technology != "RPHY") {
                            // Para NODO con Legacy o VCCAP: 4 fotos, para otros: 3 fotos
                            val maxSpectrumPhotos = if (assetType == AssetType.NODE && (technology == "Legacy" || technology == "VCCAP")) 4 else 3
                            PhotoSection(
                                title = "Fotos de Inyección de portadoras por puerto",
                                reportId = reportId,
                                assetId = workingAssetId,
                                photoType = PhotoType.SPECTRUM,
                                assetLabel = assetDisplayName,
                                eventName = eventName,
                                repository = repository,
                                minRequired = 0,
                                showRequiredError = false,
                                maxAllowed = maxSpectrumPhotos,
                                onCountChange = {
                                    spectrumPhotoCount = it
                                }
                            )
                        }

                        if (assetType == AssetType.NODE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Solo se permite un nodo por zona.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            val techNormalized = technology?.trim()?.lowercase(Locale.getDefault()) ?: ""
            val isRphyNode = assetType == AssetType.NODE && techNormalized == "rphy"

            if (autoSaved && !isRphyNode) {
                AdjustmentSummaryCard(
                    title = "Carga de Mediciones",
                    status = if (measurementsComplete) "Completo" else "Pendiente",
                    actionLabel = if (measurementsComplete) "Editar" else "Completar",
                    isComplete = measurementsComplete,
                    onAction = { showMeasurementsDialog = true }
                )

                if (showMeasurementsDialog) {
                    FullScreenAdjustmentDialog(
                        title = "Carga de Mediciones",
                        onDismiss = { showMeasurementsDialog = false },
                        onComplete = { showMeasurementsDialog = false }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (assetType == AssetType.AMPLIFIER && !ampEntradaOk) {
                                Text(
                                    "Complete mediciones de entrada válidas para continuar. La diferencia entre el nivel de entrada y medido aceptable es menor a 4. Nivel minimo de entrada permitido es 15 dBmV si esta indicado por plano.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            AssetFileSection(
                                context = context,
                                navController = navController,
                                repository = repository,
                                reportFolder = MaintenanceStorage.reportFolderName(report?.eventName, reportId),
                                onInteraction = {},
                                onCompletionChange = { measurementsComplete = it },
                                asset = Asset(
                                    id = workingAssetId,
                                    reportId = reportId,
                                    type = assetType,
                                    frequencyMHz = frequency?.mhz ?: 0,
                                    amplifierMode = amplifierMode,
                                    port = port,
                                    portIndex = portIndex,
                                    technology = if (assetType == AssetType.NODE) technology else null
                                )
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        val asset = Asset(
                            id = workingAssetId,
                            reportId = reportId,
                            type = assetType,
                            frequencyMHz = (frequency?.mhz ?: 0),
                            technology = if (assetType == AssetType.NODE) technology else null,
                            amplifierMode = amplifierMode,
                            port = port,
                            portIndex = portIndex
                        )
                        if (validateAndShowErrors()) {
                            if (assetType == AssetType.AMPLIFIER) {
                                val existing = repository.listAssetsByReportId(reportId)
                                val dup = existing.any {
                                    it.type == AssetType.AMPLIFIER &&
                                        it.id != workingAssetId &&
                                        it.port == port &&
                                        it.portIndex == portIndex
                                }
                                if (dup) {
                                    snackbarHostState.showSnackbar("Activo ya existe: ${port?.name}${String.format("%02d", portIndex)}")
                                    return@launch
                                }
                            }
                            val fixedAsset = asset.copy(frequencyMHz = frequency!!.mhz)
                            if (isEdit) viewModel.updateAsset(asset) else viewModel.addAsset(asset)
                            navController.navigate(Screen.AssetSummary.createRoute(reportId))
                        } else {
                            snackbarHostState.showSnackbar("Completa los datos del activo")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = if (assetType == AssetType.AMPLIFIER) {
                    amplifierMode != null && port != null && portIndex != null
                } else {
                    true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (assetType == AssetType.NODE) "Agregar Nodo" else "+ Agregar Activo")
            }
        }
    }

    if (showFinalizeDialog && report != null) {
        val exportManager = remember { com.example.fieldmaintenance.util.ExportManager(context, repository) }
        val r = report ?: return
        FinalizeReportDialog(
            onDismiss = { showFinalizeDialog = false },
            onSendEmailPackage = {
                scope.launch {
                    val bundleFile = exportManager.exportToBundleZip(r)
                    com.example.fieldmaintenance.util.EmailManager.sendEmail(context, r.eventName, listOf(bundleFile))
                }
            },
            onExportPackage = {
                scope.launch {
                    exportManager.exportBundleToDownloads(r)
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

@Composable
private fun AdjustmentSummaryCard(
    title: String,
    status: String,
    actionLabel: String,
    isComplete: Boolean,
    supportingText: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            if (!supportingText.isNullOrBlank()) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onAction, enabled = actionEnabled) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenAdjustmentDialog(
    title: String,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onComplete) {
                            Text("Completar")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar"
                            )
                        }
                    }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun MeasurementTableCard(
    title: String,
    headers: List<String>,
    strokeColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    content: @Composable () -> Unit
) {
    var expanded by remember(title) { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, strokeColor, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = textPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = textSecondary
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        color = textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = strokeColor, thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun MeasurementTableRow(
    cells: List<String>,
    invalidCells: Set<Int> = emptySet(),
    textPrimary: Color,
    errorColor: Color,
    strokeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        cells.forEachIndexed { index, cell ->
            val cellColor = if (invalidCells.contains(index)) errorColor else textPrimary
            Text(
                text = cell,
                color = cellColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
    HorizontalDivider(color = strokeColor, thickness = 1.dp)
}

@Composable
fun PhotoSection(
    title: String,
    reportId: String,
    assetId: String,
    photoType: PhotoType,
    assetLabel: String,
    eventName: String,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository,
    minRequired: Int,
    showRequiredError: Boolean,
    maxAllowed: Int,
    onCountChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by repository
        .getPhotosByAssetId(assetId)
        .map { list -> list.filter { it.photoType == photoType } }
        .collectAsState(initial = emptyList())

    LaunchedEffect(photos.size) { onCountChange(photos.size) }

    val isMissingRequired = showRequiredError && minRequired > 0 && photos.size < minRequired
    val isOverMax = photos.size > maxAllowed
    val isAtMax = photos.size >= maxAllowed
    val allowsGallery = photoType != PhotoType.MODULE && photoType != PhotoType.OPTICS

    var photoToDelete by remember { mutableStateOf<com.example.fieldmaintenance.data.model.Photo?>(null) }
    var photoToPreview by remember { mutableStateOf<com.example.fieldmaintenance.data.model.Photo?>(null) }
    var latestLocation by remember { mutableStateOf<Location?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    // These must survive configuration changes (e.g., rotating to landscape opens camera and Activity may recreate)
    var pendingCameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Launcher para seleccionar foto de la galería
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val dir = ImageStore.assetPhotoDir(
                        context = context,
                        reportId = reportId,
                        assetId = assetId,
                        photoType = photoType.name.lowercase()
                    )
                    val dest = File(dir, "gallery_${System.currentTimeMillis()}.jpg")
                    ImageStore.copyUriToFile(context, it, dest)
                    repository.insertPhoto(
                        com.example.fieldmaintenance.data.model.Photo(
                            assetId = assetId,
                            photoType = photoType,
                            filePath = dest.absolutePath,
                            fileName = dest.name
                        )
                    )
                } catch (_: Exception) {
                    // ignore for now
                }
            }
        }
    }

    // Launcher para tomar foto con la cámara
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraFilePath
        if (path.isNullOrBlank()) return@rememberLauncherForActivityResult

        val file = File(path)
        // Some camera apps return success=false but still write the file; treat as success if file exists and has bytes.
        val ok = success || (file.exists() && file.length() > 0)
        if (!ok) return@rememberLauncherForActivityResult

        scope.launch {
            val labelInfo = buildPhotoLabel(
                context = context,
                file = file,
                assetLabel = assetLabel,
                eventName = eventName,
                overrideLocation = latestLocation
            )
            if (labelInfo != null) {
                annotateImageWithLabel(file, labelInfo)
            }
            repository.insertPhoto(
                com.example.fieldmaintenance.data.model.Photo(
                    assetId = assetId,
                    photoType = photoType,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    latitude = labelInfo?.latitude,
                    longitude = labelInfo?.longitude
                )
            )
            // clear pending
            pendingCameraFilePath = null
            pendingCameraUriString = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = pendingCameraUriString?.let { Uri.parse(it) }
            if (uri != null) {
                runCatching { cameraLauncher.launch(uri) }
                    .onFailure { Toast.makeText(context, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show() }
            }
        } else {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
        if (!granted) {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) return@DisposableEffect onDispose {}
        val listener = LocationListener { location ->
            latestLocation = location
        }
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, 1000L, 1f, listener, Looper.getMainLooper())
            }
        }
        onDispose {
            runCatching { manager.removeUpdates(listener) }
        }
    }

    // Función para crear archivo temporal para la cámara
    val takePicture = {
        try {
            val dir = ImageStore.assetPhotoDir(
                context = context,
                reportId = reportId,
                assetId = assetId,
                photoType = photoType.name.lowercase()
            )
            val photoFile = File(dir, "camera_${System.currentTimeMillis()}.jpg")
            val photoUri = PhotoManager.getFileUri(context, photoFile)
            pendingCameraFilePath = photoFile.absolutePath
            pendingCameraUriString = photoUri.toString()

            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val locationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!locationGranted) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (granted) {
                cameraLauncher.launch(photoUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Error preparando la cámara", Toast.LENGTH_SHORT).show()
        }
    }
    
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMissingRequired || isOverMax) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Black
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
                // Conteo estilo 0/2, 1/3, etc.
                Text(
                    text = "${photos.size}/$maxAllowed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isMissingRequired) {
                Text(
                    text = "Obligatorio: mínimo $minRequired foto(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isOverMax) {
                Text(
                    text = "Máximo permitido: $maxAllowed foto(s) (borra las extra)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allowsGallery) {
                    OutlinedButton(
                        onClick = { if (!isAtMax) galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        enabled = !isAtMax
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galería")
                    }
                }
                
                OutlinedButton(
                    onClick = { if (!isAtMax) takePicture() },
                    modifier = Modifier.weight(1f),
                    enabled = !isAtMax
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cámara")
                }
            }
            
            if (photos.isNotEmpty()) {
            Text(
                text = "${photos.size} foto(s) seleccionada(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos) { photo ->
                    Card(
                        modifier = Modifier
                            .size(72.dp)
                            .combinedClickable(
                                onClick = { photoToPreview = photo },
                                onLongClick = { photoToDelete = photo }
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = rememberAsyncImagePainter(File(photo.filePath)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            }
        }
    }

    photoToPreview?.let { photo ->
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            val maxX = ((containerSize.width * (scale - 1f)) / 2f).coerceAtLeast(0f)
            val maxY = ((containerSize.height * (scale - 1f)) / 2f).coerceAtLeast(0f)
            offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
            offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
        }
        LaunchedEffect(scale) {
            if (scale <= 1.01f) {
                offsetX = 0f
                offsetY = 0f
            }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { photoToPreview = null }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size -> containerSize = size }
                            .transformable(transformState)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = rememberAsyncImagePainter(File(photo.filePath)),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    }
                    TextButton(
                        onClick = { photoToPreview = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
    
    photoToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Borrar foto") },
            text = { Text("¿Deseas borrar esta foto?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { File(target.filePath).delete() }
                        repository.deletePhoto(target)
                        photoToDelete = null
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

private data class PhotoLabelInfo(
    val lines: List<String>,
    val mapBitmap: Bitmap?,
    val latitude: Double?,
    val longitude: Double?
)

private suspend fun buildPhotoLabel(
    context: Context,
    file: File,
    assetLabel: String,
    eventName: String,
    overrideLocation: Location?
): PhotoLabelInfo? {
    val latLong = readExifLatLongWithRetry(file)
    val bestLocation = selectBestLocation(latLong, overrideLocation, getLastKnownLocation(context))
    if (latLong == null && bestLocation != null) {
        applyLocationToExif(file, bestLocation)
    }
    val latitude = latLong?.getOrNull(0) ?: bestLocation?.latitude
    val longitude = latLong?.getOrNull(1) ?: bestLocation?.longitude
    val address = if (latitude != null && longitude != null) {
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            }.getOrNull()
        }
    } else {
        null
    }
    val coords = if (latitude != null && longitude != null) {
        String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
    } else {
        null
    }
    val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
    val dateTime = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
    val formattedDateTime = dateTime?.let { raw ->
        runCatching {
            val parsed = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).parse(raw)
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(parsed ?: Date())
        }.getOrNull()
    } ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

    val headerLine = "$assetLabel - $eventName"
    val locationLine = when {
        !address.isNullOrBlank() && coords != null -> "$address ($coords)"
        coords != null -> coords
        else -> "Ubicación no disponible"
    }
    val timeLine = formattedDateTime
    val mapBitmap = if (latitude != null && longitude != null) {
        loadStaticMap(latitude, longitude)
    } else {
        null
    }
    return PhotoLabelInfo(
        lines = listOf(headerLine, locationLine, timeLine),
        mapBitmap = mapBitmap,
        latitude = latitude,
        longitude = longitude
    )
}

private suspend fun readExifLatLongWithRetry(file: File): DoubleArray? {
    repeat(6) { attempt ->
        val latLong = runCatching { ExifInterface(file.absolutePath).latLong }.getOrNull()
        if (latLong != null) return latLong
        if (attempt < 5) {
            delay(400)
        }
    }
    return null
}

private fun selectBestLocation(
    exifLatLong: DoubleArray?,
    liveLocation: Location?,
    fallbackLocation: Location?
): Location? {
    if (exifLatLong != null) return null
    val liveOk = liveLocation?.accuracy?.let { it <= 30f } == true
    return when {
        liveOk -> liveLocation
        fallbackLocation != null -> fallbackLocation
        else -> null
    }
}

private fun applyLocationToExif(file: File, location: Location) {
    runCatching {
        val exif = ExifInterface(file.absolutePath)
        exif.setGpsInfo(location)
        exif.saveAttributes()
    }
}

private fun annotateImageWithLabel(file: File, labelInfo: PhotoLabelInfo) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
    val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutable)
    val padding = (mutable.width * 0.012f).coerceIn(6f, 20f)
    val textSize = (mutable.width * 0.055f).coerceIn(26f, 68f)
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.textSize = textSize
    }
    val maxWidth = (mutable.width - padding * 2).toInt()
    val labelText = labelInfo.lines.joinToString("\n")
    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout.Builder
            .obtain(labelText, 0, labelText.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .build()
    } else {
        @Suppress("DEPRECATION")
        StaticLayout(
            labelText,
            textPaint,
            maxWidth,
            Layout.Alignment.ALIGN_CENTER,
            1f,
            0f,
            false
        )
    }
    val backgroundPaint = Paint().apply {
        color = android.graphics.Color.argb(170, 0, 0, 0)
    }
    val mapHeight = (mutable.height * 0.1f).roundToInt()
    val mapWidth = (mapHeight * 1.3f).roundToInt()
    val totalHeight = maxOf(layout.height + (padding * 2).toInt(), mapHeight + (padding * 2).toInt())
    val top = (mutable.height - totalHeight).coerceAtLeast(0)
    canvas.drawRect(
        0f,
        top.toFloat(),
        mutable.width.toFloat(),
        mutable.height.toFloat(),
        backgroundPaint
    )
    labelInfo.mapBitmap?.let { map ->
        val destWidth = mapWidth.coerceAtMost(mutable.width)
        val destHeight = mapHeight.coerceAtMost(mutable.height)
        val left = padding
        val topMap = top + ((totalHeight - destHeight) / 2f)
        val dest = android.graphics.Rect(
            left.toInt(),
            topMap.toInt(),
            (left + destWidth).toInt(),
            (topMap + destHeight).toInt()
        )
        canvas.drawBitmap(map, null, dest, null)
    }
    canvas.save()
    val textX = (mutable.width - layout.width) / 2f
    canvas.translate(textX, top + padding)
    layout.draw(canvas)
    canvas.restore()

    FileOutputStream(file).use { out ->
        mutable.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
}

private fun getLastKnownLocation(context: Context): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) {
        return null
    }
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    return providers.asSequence()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .firstOrNull()
}

private suspend fun loadStaticMap(latitude: Double, longitude: Double): Bitmap? {
    return withContext(Dispatchers.IO) {
        val url = "https://staticmap.openstreetmap.de/staticmap.php?center=$latitude,$longitude&zoom=15&size=300x200&markers=$latitude,$longitude,red-pushpin"
        runCatching {
            URL(url).openStream().use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
}

private fun formatDbmv(value: Double?): String =
    if (value == null) "—" else String.format(Locale.getDefault(), "%.1f", value)

private fun formatMHz(value: Double?): String =
    if (value == null) "—" else String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun AssetFileSection(
    context: Context,
    navController: NavController,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository,
    reportFolder: String,
    onInteraction: () -> Unit,
    onCompletionChange: (Boolean) -> Unit = {},
    asset: Asset
) {
    val isNodeAsset = asset.type == AssetType.NODE
    val rxAssetDir = remember(reportFolder, asset) {
        MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
    }
    val moduleAsset = if (isNodeAsset) {
        asset.copy(type = AssetType.AMPLIFIER)
    } else {
        asset
    }
    val moduleAssetDir = remember(reportFolder, moduleAsset) {
        MaintenanceStorage.ensureAssetDir(context, reportFolder, moduleAsset)
    }
    var rxFiles by remember(rxAssetDir) { mutableStateOf(rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()) }
    var moduleFiles by remember(moduleAssetDir) { mutableStateOf(moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()) }
    val scope = rememberCoroutineScope()
    val rxDiscardedFile = remember(rxAssetDir) { File(rxAssetDir, ".discarded_measurements.txt") }
    var rxDiscardedLabels by remember(rxAssetDir) { mutableStateOf(loadDiscardedLabels(rxDiscardedFile)) }
    val moduleDiscardedFile = remember(moduleAssetDir) { File(moduleAssetDir, ".discarded_measurements.txt") }
    var moduleDiscardedLabels by remember(moduleAssetDir) { mutableStateOf(loadDiscardedLabels(moduleDiscardedFile)) }

    val viaviIntent = remember {
        context.packageManager.getLaunchIntentForPackage("com.viavisolutions.mobiletech")
    }
    fun startViaviImport(assetTypeForImport: AssetType) {
        onInteraction()
        if (viaviIntent != null) {
            navController.currentBackStackEntry?.savedStateHandle?.apply {
                set(PendingMeasurementReportIdKey, asset.reportId)
                set(PendingMeasurementAssetIdKey, asset.id)
                set(PendingMeasurementAssetTypeKey, assetTypeForImport.name)
            }
            runCatching { context.startActivity(viaviIntent) }
                .onFailure {
                    Toast.makeText(
                        context,
                        "No se pudo abrir Viavi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            Toast.makeText(
                context,
                "Viavi (mobiletech) no está instalada",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var isExpanded by remember { mutableStateOf(true) }
    var rxExpanded by remember { mutableStateOf(true) }
    var moduleExpanded by remember { mutableStateOf(true) }
    var verificationSummaryRx by remember { mutableStateOf<MeasurementVerificationSummary?>(null) }
    var verificationSummaryModule by remember { mutableStateOf<MeasurementVerificationSummary?>(null) }
    var duplicateNotice by remember { mutableStateOf<List<String>>(emptyList()) }
    var surplusNotice by remember { mutableStateOf<List<String>>(emptyList()) }
    var surplusSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var surplusTargetCount by remember { mutableStateOf(0) }
    var surplusIsModule by remember { mutableStateOf(false) }
    var pendingDeleteEntry by remember { mutableStateOf<MeasurementEntry?>(null) }
    var pendingDeleteIsModule by remember { mutableStateOf(false) }

    fun displayLabel(entry: MeasurementEntry): String {
        return if (entry.isDiscarded && !entry.label.contains("DESCARTADA", ignoreCase = true)) {
            "${entry.label} DESCARTADA"
        } else {
            entry.label
        }
    }

    fun toggleDiscardRx(entry: MeasurementEntry) {
        if (!entry.fromZip) return
        val updated = rxDiscardedLabels.toMutableSet()
        if (entry.isDiscarded) {
            updated.remove(entry.label)
        } else {
            updated.add(entry.label)
        }
        rxDiscardedLabels = updated
        saveDiscardedLabels(rxDiscardedFile, updated)
        val rxRequired = requiredCounts(asset.type, isModule = false)
        scope.launch {
            verificationSummaryRx = verifyMeasurementFiles(
                context,
                rxFiles,
                asset,
                repository,
                updated,
                expectedDocsisOverride = rxRequired.expectedDocsis,
                expectedChannelOverride = rxRequired.expectedChannel
            )
        }
    }

    fun toggleDiscardModule(entry: MeasurementEntry) {
        if (!entry.fromZip) return
        val updated = moduleDiscardedLabels.toMutableSet()
        if (entry.isDiscarded) {
            updated.remove(entry.label)
        } else {
            updated.add(entry.label)
        }
        moduleDiscardedLabels = updated
        saveDiscardedLabels(moduleDiscardedFile, updated)
        val moduleRequired = requiredCounts(moduleAsset.type, isModule = true)
        scope.launch {
            verificationSummaryModule = verifyMeasurementFiles(
                context,
                moduleFiles,
                moduleAsset,
                repository,
                updated,
                expectedDocsisOverride = moduleRequired.expectedDocsis,
                expectedChannelOverride = moduleRequired.expectedChannel
            )
        }
    }

    LaunchedEffect(rxFiles, rxDiscardedLabels) {
        val rxRequired = requiredCounts(asset.type, isModule = false)
        if (rxFiles.isNotEmpty()) {
            val summary = verifyMeasurementFiles(
                context,
                rxFiles,
                asset,
                repository,
                rxDiscardedLabels,
                expectedDocsisOverride = rxRequired.expectedDocsis,
                expectedChannelOverride = rxRequired.expectedChannel
            )
            verificationSummaryRx = summary
            val duplicates = summary.result.duplicateFileNames + summary.result.duplicateEntryNames
            if (duplicates.isNotEmpty() && duplicateNotice.isEmpty()) {
                duplicateNotice = duplicates
            }
            val totalExpected = rxRequired.expectedDocsis + rxRequired.expectedChannel
            val totalActual = summary.result.docsisExpert + summary.result.channelExpert
            val surplusCount = (totalActual - totalExpected).coerceAtLeast(0)
            if (surplusCount > 0 && surplusNotice.isEmpty()) {
                surplusNotice = (summary.result.docsisNames + summary.result.channelNames).distinct()
                surplusSelection = emptySet()
                surplusTargetCount = surplusCount
                surplusIsModule = false
            }
            if (summary.result.duplicateFileCount > 0) {
                rxFiles = rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            }
        } else {
            verificationSummaryRx = null
        }
    }

    LaunchedEffect(moduleFiles, moduleDiscardedLabels) {
        if (!isNodeAsset) return@LaunchedEffect
        val moduleRequired = requiredCounts(moduleAsset.type, isModule = true)
        if (moduleFiles.isNotEmpty()) {
            val summary = verifyMeasurementFiles(
                context,
                moduleFiles,
                moduleAsset,
                repository,
                moduleDiscardedLabels,
                expectedDocsisOverride = moduleRequired.expectedDocsis,
                expectedChannelOverride = moduleRequired.expectedChannel
            )
            verificationSummaryModule = summary
            val duplicates = summary.result.duplicateFileNames + summary.result.duplicateEntryNames
            if (duplicates.isNotEmpty() && duplicateNotice.isEmpty()) {
                duplicateNotice = duplicates
            }
            val totalExpected = moduleRequired.expectedDocsis + moduleRequired.expectedChannel
            val totalActual = summary.result.docsisExpert + summary.result.channelExpert
            val surplusCount = (totalActual - totalExpected).coerceAtLeast(0)
            if (surplusCount > 0 && surplusNotice.isEmpty()) {
                surplusNotice = (summary.result.docsisNames + summary.result.channelNames).distinct()
                surplusSelection = emptySet()
                surplusTargetCount = surplusCount
                surplusIsModule = true
            }
            if (summary.result.duplicateFileCount > 0) {
                moduleFiles = moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            }
        } else {
            verificationSummaryModule = null
        }
    }

    val observationSummary = remember(verificationSummaryRx, verificationSummaryModule, isNodeAsset) {
        val rxSummary = verificationSummaryRx
        val moduleSummary = if (isNodeAsset) verificationSummaryModule else null
        Triple(rxSummary, moduleSummary, (rxSummary?.observationTotal ?: 0) + (moduleSummary?.observationTotal ?: 0))
    }
    val observationPrefs = remember {
        context.getSharedPreferences("observation_flags", Context.MODE_PRIVATE)
    }
    var showObservationsDialog by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val rxRequired = requiredCounts(asset.type, isModule = false)
            val moduleRequired = requiredCounts(moduleAsset.type, isModule = true)
            val canRefresh = if (isNodeAsset) {
                meetsRequired(verificationSummaryRx, rxRequired) &&
                    meetsRequired(verificationSummaryModule, moduleRequired)
            } else {
                meetsRequired(verificationSummaryRx, rxRequired)
            }
            LaunchedEffect(canRefresh, asset.id) {
                onCompletionChange(canRefresh)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Carga de Mediciones",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showObservationsDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = "Observaciones",
                        tint = if (observationSummary.third > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                IconButton(
                    onClick = {
                        onInteraction()
                        scope.launch {
                            val updatedRxFiles = rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                            rxFiles = updatedRxFiles
                            if (updatedRxFiles.isNotEmpty()) {
                                verificationSummaryRx = verifyMeasurementFiles(
                                    context,
                                    updatedRxFiles,
                                    asset,
                                    repository,
                                    rxDiscardedLabels,
                                    expectedDocsisOverride = rxRequired.expectedDocsis,
                                    expectedChannelOverride = rxRequired.expectedChannel
                                )
                            } else {
                                verificationSummaryRx = null
                            }

                            if (isNodeAsset) {
                                val updatedModuleFiles = moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                                moduleFiles = updatedModuleFiles
                                if (updatedModuleFiles.isNotEmpty()) {
                                    verificationSummaryModule = verifyMeasurementFiles(
                                        context,
                                        updatedModuleFiles,
                                        moduleAsset,
                                        repository,
                                        moduleDiscardedLabels,
                                        expectedDocsisOverride = moduleRequired.expectedDocsis,
                                        expectedChannelOverride = moduleRequired.expectedChannel
                                    )
                                } else {
                                    verificationSummaryModule = null
                                }
                            }
                        }
                    },
                    enabled = asset.type in setOf(AssetType.NODE, AssetType.AMPLIFIER) && canRefresh
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refrescar verificación")
                }
                IconButton(onClick = {
                    onInteraction()
                    isExpanded = !isExpanded
                }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir"
                    )
                }
            }
            LaunchedEffect(canRefresh, asset.id) {
                if (canRefresh && observationSummary.third > 0) {
                    val key = "obs_auto_shown_${asset.id}"
                    val wasShown = observationPrefs.getBoolean(key, false)
                    if (!wasShown) {
                        showObservationsDialog = true
                        observationPrefs.edit().putBoolean(key, true).apply()
                    }
                }
            }
            if (showObservationsDialog) {
                AlertDialog(
                    onDismissRequest = { showObservationsDialog = false },
                    title = { Text("Fallas Detectadas") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (rxSummary, moduleSummary, _) = observationSummary
                            val geoIssues = buildList {
                                rxSummary?.geoIssueDetails?.let { addAll(it) }
                                if (isNodeAsset) {
                                    moduleSummary?.geoIssueDetails?.let { addAll(it) }
                                }
                            }
                            if (observationSummary.third == 0) {
                                Text("Sin observaciones.")
                            } else {
                                Text("Observaciones detectadas: ${observationSummary.third}", fontWeight = FontWeight.SemiBold)
                                if (geoIssues.isNotEmpty()) {
                                    Text(
                                        "Problemas de Georeferencia",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    geoIssues.forEach { detail ->
                                        Text(
                                            "${detail.file}: ${detail.detail}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showObservationsDialog = false }) {
                            Text("Aceptar")
                        }
                    }
                )
            }
            val geoLocation = if (isNodeAsset) {
                verificationSummaryModule?.geoLocation ?: verificationSummaryRx?.geoLocation
            } else {
                verificationSummaryRx?.geoLocation
            }
            Text(
                "Georreferencia: ${geoLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isExpanded) {
                @Composable
                fun VerificationSummaryView(
                    summary: MeasurementVerificationSummary,
                    assetForDisplay: Asset,
                    onToggleDiscard: (MeasurementEntry) -> Unit,
                    onRequestDelete: (MeasurementEntry) -> Unit,
                    isModule: Boolean
                ) {
                    val docsisEntries = summary.result.measurementEntries.filter { it.type == "docsisexpert" }
                    val channelEntries = summary.result.measurementEntries.filter { it.type == "channelexpert" }
                    val required = requiredCounts(assetForDisplay.type, isModule)

                    val docsisListEntries = docsisEntries.filterNot { it.isDiscarded }
                    val channelListEntries = channelEntries.filterNot { it.isDiscarded }
                    val docsisTableEntries = docsisListEntries.take(required.maxDocsisTable)
                    val channelTableEntries = channelListEntries.take(required.maxChannelTable)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val cardColor = Color(0xFF141823)
                        val strokeColor = Color(0xFF2A3142)
                        val accentColor = Color(0xFF1E88E5)
                        val errorColor = Color(0xFFD32F2F)
                        val tableTextPrimary = Color(0xFFE7EAF0)
                        val tableTextSecondary = Color(0xFFB0B7C3)

                        val docsisCountLabel = "${summary.result.docsisExpert}/${summary.expectedDocsis}"
                        val channelCountLabel = "${summary.result.channelExpert}/${summary.expectedChannel}"

                        data class MeasurementTab(
                            val label: String,
                            val entry: MeasurementEntry,
                            val hasError: Boolean
                        )

                        fun docsisHasError(entry: MeasurementEntry): Boolean {
                            return entry.docsisLevelOk.values.any { !it }
                        }

                        fun channelHasError(entry: MeasurementEntry): Boolean {
                            val pilotError = entry.pilotLevelOk.values.any { !it }
                            val digitalError = entry.digitalRows.any { row ->
                                (row.levelOk == false) ||
                                    (row.merOk == false) ||
                                    (row.berPreOk == false) ||
                                    (row.berPostOk == false) ||
                                    (row.icfrOk == false)
                            }
                            return pilotError || digitalError
                        }

                        @Composable
                        fun MeasurementTabChip(
                            label: String,
                            isSelected: Boolean,
                            hasError: Boolean,
                            onClick: () -> Unit
                        ) {
                            val bg = if (isSelected) accentColor else Color.Transparent
                            val borderColor = strokeColor
                            val textColor = if (hasError) errorColor else if (isSelected) Color.White else tableTextPrimary
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                    .clickable { onClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = label, color = textColor, fontSize = 13.sp)
                            }
                        }

                        @Composable
                        fun MeasurementTabsWithPagerCard(
                            tabs: List<MeasurementTab>,
                            footerProvider: (MeasurementEntry, String) -> String,
                            onDelete: (MeasurementEntry) -> Unit,
                            tableContent: @Composable (MeasurementEntry) -> Unit
                        ) {
                            val pagerState = rememberPagerState(pageCount = { tabs.size })
                            val scope = rememberCoroutineScope()
                            val selectedTab = tabs.getOrNull(pagerState.currentPage)
                            var isExpanded by remember(tabs) { mutableStateOf(false) }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardColor)
                                    .border(1.dp, strokeColor, RoundedCornerShape(14.dp))
                                    .padding(10.dp)
                            ) {
                                if (tabs.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        tabs.forEachIndexed { index, tab ->
                                            MeasurementTabChip(
                                                label = tab.label,
                                                isSelected = pagerState.currentPage == index,
                                                hasError = tab.hasError,
                                                onClick = {
                                                    isExpanded = true
                                                    scope.launch { pagerState.animateScrollToPage(index) }
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                if (tabs.isNotEmpty() && isExpanded) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { page ->
                                        val entry = tabs[page].entry
                                        tableContent(entry)
                                    }
                                } else if (tabs.isNotEmpty()) {
                                    Text(
                                        "Seleccione una medición para desplegar la tabla.",
                                        color = tableTextSecondary,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Text(
                                        "Sin mediciones cargadas.",
                                        color = tableTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                if (tabs.isNotEmpty() && isExpanded) {
                                    Spacer(Modifier.height(8.dp))

                                    selectedTab?.let { tab ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = footerProvider(tab.entry, tab.label),
                                                color = if (tab.hasError) errorColor else tableTextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { onDelete(tab.entry) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar medición",
                                                    tint = tableTextSecondary
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = strokeColor, thickness = 1.dp)
                                        content()
                                    }
                                }
                            }
                        }

                        if (assetForDisplay.type != AssetType.NODE) {
                            Text("DOCSIS Expert $docsisCountLabel", color = tableTextPrimary, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            val docsisTabs = docsisTableEntries.mapIndexed { index, entry ->
                                MeasurementTab(
                                    label = "M${index + 1}",
                                    entry = entry,
                                    hasError = docsisHasError(entry)
                                )
                            }
                            MeasurementTabsWithPagerCard(
                                tabs = docsisTabs,
                                footerProvider = { entry, label ->
                                    "$label = ${displayLabel(entry)}"
                                },
                                onDelete = onRequestDelete
                            ) { entry ->
                                val rows = entry.docsisLevels.keys.sorted().map { freq ->
                                    val channel = entry.docsisMeta[freq]?.channel?.toString() ?: "—"
                                    val frequency = entry.docsisMeta[freq]?.frequencyMHz ?: freq
                                    val level = formatDbmv(entry.docsisLevels[freq])
                                    val invalidCells = if (entry.docsisLevelOk[freq] == false) setOf(2) else emptySet()
                                    listOf(
                                        channel,
                                        formatMHz(frequency),
                                        level,
                                        "—"
                                    ) to invalidCells
                                }
                                MeasurementTableCard(
                                    title = "Upstream Channels",
                                    headers = listOf("UCD", "Frecuencia (MHz)", "Nivel (dBmV)", "ICFR (dB)")
                                    ,
                                    strokeColor = strokeColor,
                                    textPrimary = tableTextPrimary,
                                    textSecondary = tableTextSecondary
                                ) {
                                    rows.forEach { (cells, invalid) ->
                                        MeasurementTableRow(
                                            cells = cells,
                                            invalidCells = invalid,
                                            textPrimary = tableTextPrimary,
                                            errorColor = errorColor,
                                            strokeColor = strokeColor
                                        )
                                        IconButton(onClick = { onDelete(tab.entry) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar medición",
                                                tint = tableTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text("Channel Expert $channelCountLabel", color = tableTextPrimary, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        val channelTabs = channelTableEntries.mapIndexed { index, entry ->
                            MeasurementTab(
                                label = "M${index + 1}",
                                entry = entry,
                                hasError = channelHasError(entry)
                            )
                        }
                        MeasurementTabsWithPagerCard(
                            tabs = channelTabs,
                            footerProvider = { entry, label ->
                                "$label = ${displayLabel(entry)}"
                            },
                            onDelete = onRequestDelete
                        ) { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MeasurementTableCard(
                                    title = "Downstream Analogic Channels",
                                    headers = listOf("Canal", "Freq (MHz)", "M1", "M2")
                                    ,
                                    strokeColor = strokeColor,
                                    textPrimary = tableTextPrimary,
                                    textSecondary = tableTextSecondary
                                ) {
                                    val pilotChannels = listOf(50, 70, 110, 116, 136)
                                    pilotChannels.forEach { channel ->
                                        val frequency = entry.pilotMeta[channel]?.frequencyMHz
                                        val level = entry.pilotLevels[channel]
                                        val invalidCells = if (entry.pilotLevelOk[channel] == false) setOf(2) else emptySet()
                                        MeasurementTableRow(
                                            cells = listOf(
                                                channel.toString(),
                                                formatMHz(frequency),
                                                formatDbmv(level),
                                                "—"
                                            ),
                                            invalidCells = invalidCells,
                                            textPrimary = tableTextPrimary,
                                            errorColor = errorColor,
                                            strokeColor = strokeColor
                                        )
                                    }
                                }
                                MeasurementTableCard(
                                    title = "Downstream Digital Channels",
                                    headers = listOf("Canal", "Freq (MHz)", "Nivel (dBmV)", "MER", "BER pre", "BER post", "ICFR")
                                    ,
                                    strokeColor = strokeColor,
                                    textPrimary = tableTextPrimary,
                                    textSecondary = tableTextSecondary
                                ) {
                                    entry.digitalRows.forEach { row ->
                                        val invalidCells = buildSet {
                                            if (row.levelOk == false) add(2)
                                            if (row.merOk == false) add(3)
                                            if (row.berPreOk == false) add(4)
                                            if (row.berPostOk == false) add(5)
                                            if (row.icfrOk == false) add(6)
                                        }
                                        MeasurementTableRow(
                                            cells = listOf(
                                                row.channel.toString(),
                                                formatMHz(row.frequencyMHz),
                                                formatDbmv(row.levelDbmv),
                                                formatDbmv(row.mer),
                                                row.berPre?.toString() ?: "—",
                                                row.berPost?.toString() ?: "—",
                                                formatDbmv(row.icfr)
                                            ),
                                            invalidCells = invalidCells,
                                            textPrimary = tableTextPrimary,
                                            errorColor = errorColor,
                                            strokeColor = strokeColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (asset.type == AssetType.NODE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rxExpanded = !rxExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mediciones RX", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { startViaviImport(AssetType.NODE) }) {
                                Icon(Icons.Default.FileUpload, contentDescription = "Agregar mediciones RX")
                            }
                            Icon(
                                if (rxExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        if (rxExpanded) {
                            verificationSummaryRx?.let { summary ->
                                VerificationSummaryView(
                                    summary,
                                    asset,
                                    ::toggleDiscardRx,
                                    onRequestDelete = { entry ->
                                        pendingDeleteEntry = entry
                                        pendingDeleteIsModule = false
                                    },
                                    isModule = false
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { moduleExpanded = !moduleExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mediciones Modulo", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { startViaviImport(AssetType.AMPLIFIER) }) {
                                Icon(Icons.Default.FileUpload, contentDescription = "Agregar mediciones Modulo")
                            }
                            Icon(
                                if (moduleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        if (moduleExpanded) {
                            verificationSummaryModule?.let { summary ->
                                VerificationSummaryView(
                                    summary,
                                    moduleAsset,
                                    ::toggleDiscardModule,
                                    onRequestDelete = { entry ->
                                        pendingDeleteEntry = entry
                                        pendingDeleteIsModule = true
                                    },
                                    isModule = true
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Agregar Mediciones", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { startViaviImport(asset.type) }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Agregar mediciones")
                        }
                    }
                    verificationSummaryRx?.let { summary ->
                        VerificationSummaryView(
                            summary,
                            asset,
                            ::toggleDiscardRx,
                            onRequestDelete = { entry ->
                                pendingDeleteEntry = entry
                                pendingDeleteIsModule = false
                            },
                            isModule = false
                        )
                    }
                }
            }
        }
    }
    if (pendingDeleteEntry != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text("Eliminar medición") },
            text = { Text("¿Desea eliminar esta medición?") },
            confirmButton = {
                TextButton(onClick = {
                    val entry = pendingDeleteEntry
                    val isModule = pendingDeleteIsModule
                    pendingDeleteEntry = null
                    if (entry != null) {
                        val list = if (isModule) moduleFiles else rxFiles
                        val entryName = entry.label.substringAfterLast('/')
                        val file = list.firstOrNull { it.name == entryName }
                        if (file != null) {
                            scope.launch(Dispatchers.IO) {
                                MaintenanceStorage.moveMeasurementFileToTrash(context, file)
                                val updated = if (isModule) {
                                    moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                                } else {
                                    rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                                }
                                val required = if (isModule) {
                                    requiredCounts(moduleAsset.type, isModule = true)
                                } else {
                                    requiredCounts(asset.type, isModule = false)
                                }
                                val summary = if (updated.isNotEmpty()) {
                                    verifyMeasurementFiles(
                                        context,
                                        updated,
                                        if (isModule) moduleAsset else asset,
                                        repository,
                                        if (isModule) moduleDiscardedLabels else rxDiscardedLabels,
                                        expectedDocsisOverride = required.expectedDocsis,
                                        expectedChannelOverride = required.expectedChannel
                                    )
                                } else {
                                    null
                                }
                                withContext(Dispatchers.Main) {
                                    if (isModule) {
                                        moduleFiles = updated
                                        verificationSummaryModule = summary
                                    } else {
                                        rxFiles = updated
                                        verificationSummaryRx = summary
                                    }
                                }
                            }
                        } else if (entry.fromZip) {
                            if (isModule) {
                                toggleDiscardModule(entry)
                            } else {
                                toggleDiscardRx(entry)
                            }
                        }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) { Text("Cancelar") }
            }
        )
    }


    if (duplicateNotice.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mediciones duplicadas",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { duplicateNotice = emptyList() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    duplicateNotice.forEach { name ->
                        Text(
                            "No se agregó la medición $name por estar duplicada.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (surplusNotice.isNotEmpty()) {
        val selectedCount = surplusSelection.size
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text("Mediciones sobrantes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Se detectaron $surplusTargetCount mediciones de más. Seleccione cuáles desea eliminar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    surplusNotice.forEach { name ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = surplusSelection.contains(name),
                                onCheckedChange = { checked ->
                                    surplusSelection = if (checked) {
                                        if (surplusSelection.size < surplusTargetCount) {
                                            surplusSelection + name
                                        } else {
                                            surplusSelection
                                        }
                                    } else {
                                        surplusSelection - name
                                    }
                                }
                            )
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = surplusSelection
                        val updated = if (surplusIsModule) {
                            moduleDiscardedLabels + selected
                        } else {
                            rxDiscardedLabels + selected
                        }
                        if (surplusIsModule) {
                            moduleDiscardedLabels = updated
                            saveDiscardedLabels(moduleDiscardedFile, updated)
                        } else {
                            rxDiscardedLabels = updated
                            saveDiscardedLabels(rxDiscardedFile, updated)
                        }
                        surplusNotice = emptyList()
                        surplusSelection = emptySet()
                        surplusTargetCount = 0
                        scope.launch {
                            if (surplusIsModule) {
                                val moduleRequired = requiredCounts(moduleAsset.type, isModule = true)
                                verificationSummaryModule = verifyMeasurementFiles(
                                    context,
                                    moduleFiles,
                                    moduleAsset,
                                    repository,
                                    moduleDiscardedLabels,
                                    expectedDocsisOverride = moduleRequired.expectedDocsis,
                                    expectedChannelOverride = moduleRequired.expectedChannel
                                )
                            } else {
                                val rxRequired = requiredCounts(asset.type, isModule = false)
                                verificationSummaryRx = verifyMeasurementFiles(
                                    context,
                                    rxFiles,
                                    asset,
                                    repository,
                                    rxDiscardedLabels,
                                    expectedDocsisOverride = rxRequired.expectedDocsis,
                                    expectedChannelOverride = rxRequired.expectedChannel
                                )
                            }
                        }
                    },
                    enabled = selectedCount == surplusTargetCount
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}
